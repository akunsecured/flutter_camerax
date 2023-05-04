package dev.yanshouwang.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Size
import android.view.Surface
import android.view.Surface.ROTATION_90
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.Log
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import java.util.*
import kotlinx.coroutines.*

class CameraXHandler(private val activity: Activity, private val textureRegistry: TextureRegistry) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
    PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val REQUEST_CODE = 20230413
        private const val INVALID_TIME: Long = -1
    }

    private var isInitialized: Boolean = false
    private var isStreaming: Boolean = false
    private var lastImageTaken: Long = -1

    private var sink: EventChannel.EventSink? = null
    private var listener: PluginRegistry.RequestPermissionsResultListener? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var imageAnalyzer: ImageAnalysis? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "permissionState" -> permissionState(result)
            "requestPermission" -> requestPermissions(result)
            "initCamera" -> initCamera(call, result)
            "torch" -> torchNative(call, result)
            "stopCamera" -> stopCamera(result)
            "startImageStream" -> startImageStream(call, result)
            "stopImageStream" -> stopImageStream(result)
            "setOutputImageSize" -> setOutputImageSize(call, result)
            "isStreaming" -> isStreaming(result)
            else -> result.notImplemented()
        }
    }

    private fun isStreaming(result: MethodChannel.Result) =
        result.success(isStreaming)

    private fun startImageStream(args: Map<*, *>?) {
        val delay: Int? = args?.get("delay") as Int?
        val debugging: Boolean = (args?.get("debugging") as Boolean?) ?: false

        Log.d("CameraX/ImageStream", "Starting image stream with delay: $delay")

        val executor = ContextCompat.getMainExecutor(activity)

        imageAnalyzer?.setAnalyzer(
            executor
        ) { image ->
            val now = Calendar.getInstance().time.time

            if (delay != null) {
                if (lastImageTaken != INVALID_TIME && ((now - lastImageTaken) < delay)) {
                    image.close()
                    return@setAnalyzer
                }
            }

            if (image.format == ImageFormat.YUV_420_888) {
                var imageBytes: ByteArray

                GlobalScope.launch(Dispatchers.IO) {
                    imageBytes = image.jpeg

                    withContext(Dispatchers.Main) {
                        val data = mutableMapOf(
                            "bytes" to imageBytes,
                            "timestamp" to now,
                            "size" to mapOf(
                                "width" to image.width,
                                "height" to image.height,
                            ),
                        )

                        if (debugging) {
                            data["time_statistics"] = mapOf(
                                "sent_time" to Calendar.getInstance().time.time,
                                "process_time" to Calendar.getInstance().time.time - now,
                            )
                        }

                        sink?.success(
                            mapOf(
                                "name" to "image",
                                "data" to data
                            )
                        )

                        lastImageTaken = now

                        image.close()
                    }
                }
            }
        }

        isStreaming = true

        sink?.success(
            mapOf(
                "name" to "streamingState",
                "data" to isStreaming,
            )
        )
    }

    private fun startImageStream(call: MethodCall, result: MethodChannel.Result) {
        if (!isInitialized) {
            result.error(
                "CameraX/InitializationError",
                "The camera has not yet been initialized",
                null
            )
            return
        }

        if (isStreaming) {
            result.error("CameraX/ImageStreamError", "Image stream is already started", null)
            return
        }

        startImageStream(call.arguments as Map<*, *>?)

        result.success(null)
    }

    private fun stopImageStream() {
        Log.d("CameraX/ImageStream", "Stopping image stream")

        imageAnalyzer?.clearAnalyzer()

        isStreaming = false

        sink?.success(
            mapOf(
                "name" to "streamingState",
                "data" to isStreaming,
            )
        )
    }

    private fun stopImageStream(result: MethodChannel.Result) {
        if (!isStreaming) {
            result.error("CameraX/ImageStream", "Image stream is already stopped", null)
            return
        }

        stopImageStream()

        result.success(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean =
        listener?.onRequestPermissionsResult(requestCode, permissions, grantResults) ?: false

    private fun permissionState(result: MethodChannel.Result) =
        result.success(
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )

    private fun requestPermissions(result: MethodChannel.Result) {
        listener = PluginRegistry.RequestPermissionsResultListener { requestCode, _, grantResults ->
            if (requestCode != REQUEST_CODE) {
                false
            } else {
                val authorized = grantResults[0] == PackageManager.PERMISSION_GRANTED
                result.success(authorized)
                listener = null
                true
            }
        }
        val permissions = arrayOf(Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE)
    }

    private fun initCamera(call: MethodCall, result: MethodChannel.Result) {
        val future = ProcessCameraProvider.getInstance(activity)
        val executor = ContextCompat.getMainExecutor(activity)

        val args = call.arguments as Map<*, *>

        future.addListener({
            cameraProvider = future.get()
            cameraProvider?.unbindAll()

            textureEntry = textureRegistry.createSurfaceTexture()
            val textureId = textureEntry!!.id()

            // Preview
            val surfaceProvider = Preview.SurfaceProvider { request ->
                val resolution = request.resolution
                val texture = textureEntry!!.surfaceTexture()
                texture.setDefaultBufferSize(resolution.width, resolution.height)
                val surface = Surface(texture)
                request.provideSurface(surface, executor) { }
            }
            val preview = Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }

            // Analyzer
            val sizeArgs = args["size"] as Map<*, *>?

            val size = if (sizeArgs == null) {
                Size(1280, 720)
            } else {
                Size((sizeArgs["width"] as Double).toInt(), (sizeArgs["height"] as Double).toInt())
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(ROTATION_90)
                .setTargetResolution(size)
                .build()

            // Bind to lifecycle.
            val owner = activity as LifecycleOwner
            val selector =
                if (args["selector"] == 0) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
            camera = cameraProvider!!.bindToLifecycle(owner, selector, preview, imageAnalyzer)
            camera!!.cameraInfo.torchState.observe(owner) { state ->
                // TorchState.OFF = 0; TorchState.ON = 1
                val event = mapOf("name" to "torchState", "data" to state)
                sink?.success(event)
            }

            // TODO: seems there's not a better way to get the final resolution
            @SuppressLint("RestrictedApi")
            val resolution = preview.attachedSurfaceResolution!!
            val portrait = camera!!.cameraInfo.sensorRotationDegrees % 180 == 0
            val width = resolution.width.toDouble()
            val height = resolution.height.toDouble()
            val sizeMap = if (portrait) mapOf("width" to width, "height" to height) else mapOf(
                "width" to height,
                "height" to width
            )
            val res =
                mapOf(
                    "textureId" to textureId,
                    "size" to sizeMap,
                    "torchable" to camera!!.torchable
                )

            isInitialized = true

            result.success(res)
        }, executor)
    }

    private fun torchNative(call: MethodCall, result: MethodChannel.Result) {
        val state = call.arguments == 1
        camera!!.cameraControl.enableTorch(state)
        result.success(null)
    }

    private fun setOutputImageSize(call: MethodCall, result: MethodChannel.Result) {
        val sizeArgs = call.arguments as Map<*, *>?

        if (isStreaming) {
            result.error(
                "CameraX/SetOutputImageSize",
                "Output image size cannot be modified while the image stream is running",
                null
            )
            return
        }

        if (sizeArgs == null) {
            result.error("CameraX/SetOutputImageSize", "Size is null", null)
            return
        }

        val size = Size(
            (sizeArgs["width"] as Double).toInt(),
            (sizeArgs["height"] as Double).toInt(),
        )

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(ROTATION_90)
            .setTargetResolution(size)
            .build()

        result.success(null)
    }

    private fun stopCamera(result: MethodChannel.Result) {
        if (isStreaming) {
            stopImageStream()
        }

        val owner = activity as LifecycleOwner
        camera!!.cameraInfo.torchState.removeObservers(owner)
        cameraProvider!!.unbindAll()
        textureEntry!!.release()

        camera = null
        textureEntry = null
        cameraProvider = null

        isInitialized = false

        result.success(null)
    }
}