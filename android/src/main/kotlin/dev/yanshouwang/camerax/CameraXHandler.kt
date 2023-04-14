package dev.yanshouwang.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.Surface.ROTATION_90
import androidx.annotation.IntDef
import androidx.annotation.NonNull
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import io.flutter.Log
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import java.util.*

class CameraXHandler(private val activity: Activity, private val textureRegistry: TextureRegistry) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
    PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val REQUEST_CODE = 20230413
        private const val INVALID_TIME: Long = -1
    }

    private var sink: EventChannel.EventSink? = null
    private var listener: PluginRegistry.RequestPermissionsResultListener? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var imageAnalyzer: ImageAnalysis? = null

    @AnalyzeMode
    private var analyzeMode: Int = AnalyzeMode.NONE

    private var initialized: Boolean = false

    private var lastImageTaken: Long = -1

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "state" -> stateNative(result)
            "request" -> requestNative(result)
            "start" -> startNative(call, result)
            "torch" -> torchNative(call, result)
            "analyze" -> analyzeNative(call, result)
            "stop" -> stopNative(result)
            "startImageStream" -> startImageStream(call, result)
            "stopImageStream" -> stopImageStream(result)
            "isStreaming" -> isStreaming(result)
            else -> result.notImplemented()
        }
    }

    private fun isStreaming(result: MethodChannel.Result) =
        result.success(analyzeMode == AnalyzeMode.IMAGE_BYTES)

    private fun startImageStream(call: MethodCall, result: MethodChannel.Result) {
        if (analyzeMode == AnalyzeMode.IMAGE_BYTES) {
            result.error("ERROR", "Image stream is already started", null)
            return
        }

        sink?.success(
            mapOf(
                "name" to "streaming",
                "data" to true,
            )
        )

        val delay = call.arguments as Int?
        Log.d("ImageStream", "Image streaming started with delay: $delay")

        val executor = ContextCompat.getMainExecutor(activity)

        analyzeMode = AnalyzeMode.IMAGE_BYTES

        imageAnalyzer?.setAnalyzer(
            executor
        ) { image ->
            val now = Calendar.getInstance().time.time

            if (delay != null) {
                // Log.d("ImageStream", "Now: $now, last: $lastImageTaken")
                if (lastImageTaken != INVALID_TIME && ((now - lastImageTaken) < delay)) {
                    // Log.d("ImageStream", "Returning..")
                    image.close()
                    return@setAnalyzer
                }
            }

            if (image.format == ImageFormat.YUV_420_888) {
                val imageBytes = image.jpeg

                sink?.success(
                    mapOf(
                        "name" to "imageBytes",
                        "data" to mapOf(
                            "bytes" to imageBytes,
                            "timestamp" to now,
                            "size" to mapOf(
                                "width" to image.width,
                                "height" to image.height,
                            ),
                            "sent" to Calendar.getInstance().time.time,
                        ),
                    )
                )

                lastImageTaken = now
            }

            image.close()
        }

        result.success(null)
    }

    private fun stopImageStream(result: MethodChannel.Result) {
        if (analyzeMode == AnalyzeMode.NONE) {
            result.error("ERROR", "Image stream is already stopped", null)
            return
        }

        analyzeMode = AnalyzeMode.NONE

        imageAnalyzer?.clearAnalyzer()

        sink?.success(
            mapOf(
                "name" to "streaming",
                "data" to false,
            )
        )

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
    ): Boolean {
        return listener?.onRequestPermissionsResult(requestCode, permissions, grantResults) ?: false
    }

    private fun stateNative(result: MethodChannel.Result) {
        // Can't get exact denied or not_determined state without request. Just return not_determined when state isn't authorized
        val state =
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) 1
            else 0
        result.success(state)
    }

    private fun requestNative(result: MethodChannel.Result) {
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

    private fun startNative(call: MethodCall, result: MethodChannel.Result) {
        val future = ProcessCameraProvider.getInstance(activity)
        val executor = ContextCompat.getMainExecutor(activity)

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
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(ROTATION_90)
                .setTargetResolution(Size(1280, 720))
                .build()

            // Bind to lifecycle.
            val owner = activity as LifecycleOwner
            val selector =
                if (call.arguments == 0) CameraSelector.DEFAULT_FRONT_CAMERA
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
            val size = if (portrait) mapOf("width" to width, "height" to height) else mapOf(
                "width" to height,
                "height" to width
            )
            val answer =
                mapOf("textureId" to textureId, "size" to size, "torchable" to camera!!.torchable)

            initialized = true

            result.success(answer)
        }, executor)
    }

    private fun torchNative(call: MethodCall, result: MethodChannel.Result) {
        val state = call.arguments == 1
        camera!!.cameraControl.enableTorch(state)
        result.success(null)
    }

    private fun analyzeNative(call: MethodCall, result: MethodChannel.Result) {
        analyzeMode = call.arguments as Int
        result.success(null)
    }

    private fun stopNative(result: MethodChannel.Result) {
        val owner = activity as LifecycleOwner
        camera!!.cameraInfo.torchState.removeObservers(owner)
        cameraProvider!!.unbindAll()
        textureEntry!!.release()

        analyzeMode = AnalyzeMode.NONE
        camera = null
        textureEntry = null
        cameraProvider = null

        result.success(null)
    }
}

@IntDef(AnalyzeMode.NONE, AnalyzeMode.BARCODE, AnalyzeMode.IMAGE_BYTES)
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class AnalyzeMode {
    companion object {
        const val NONE = 0
        const val BARCODE = 1
        const val IMAGE_BYTES = 2
    }
}