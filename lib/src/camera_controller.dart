import 'dart:async';

import 'package:camerax/src/camera_permission_state.dart';
import 'package:camerax/src/camera_size.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'camera_args.dart';
import 'camera_facing.dart';
import 'camera_image.dart';
import 'torch_state.dart';

/// A camera controller.
abstract class CameraController {
  /// Arguments for [CameraView].
  ValueNotifier<CameraArgs?> get args;

  /// Torch state of the camera.
  ValueNotifier<TorchState> get torchState;

  /// Streaming state of the camera.
  ValueNotifier<bool> get streamingState;

  /// Image stream of the camera.
  Stream<CameraImage> get images;

  bool get isStreaming;

  /// Create a [CameraController].
  ///
  /// [facing] target facing used to select camera.
  ///
  /// [formats] the barcode formats for image analyzer.
  factory CameraController({
    CameraFacing facing = CameraFacing.back,
    CameraSize size = CameraSize.HD,
  }) =>
      _CameraController(
        facing: facing,
        cameraSize: size,
      );

  /// Start the camera asynchronously.
  Future<void> initCamera();

  /// Start the image stream.
  Future<void> startImageStream([int? delay, bool? debugging]);

  /// Stop the image stream.
  Future<void> stopImageStream();

  /// Sets the output image size.
  Future<void> setOutputImageSize(Size size);

  /// Switch the torch's state.
  void torch();

  /// Release the resources of the camera.
  Future<void> dispose();
}

class _CameraController implements CameraController {
  static const MethodChannel _method =
      MethodChannel('yanshouwang.dev/camerax/method');
  static const EventChannel _event =
      EventChannel('yanshouwang.dev/camerax/event');

  static int? _id;
  static StreamSubscription? _subscription;

  final CameraFacing facing;
  final CameraSize cameraSize;
  bool _torchable;
  late StreamController<CameraImage> _imageController;

  @override
  final ValueNotifier<CameraArgs?> args;

  @override
  final ValueNotifier<TorchState> torchState;

  @override
  final ValueNotifier<bool> streamingState;

  @override
  bool get isStreaming => streamingState.value;

  @override
  Stream<CameraImage> get images => _imageController.stream;

  _CameraController({
    required this.facing,
    required this.cameraSize,
  })  : args = ValueNotifier(null),
        torchState = ValueNotifier(TorchState.off),
        streamingState = ValueNotifier(false),
        _torchable = false {
    // In case new instance before dispose.
    if (_id != null) {
      _stopCamera();
    }
    _id = hashCode;

    // Create image stream controller.
    _imageController = StreamController.broadcast();

    // Listen event handler.
    _subscription =
        _event.receiveBroadcastStream().listen((data) => _handleEvent(data));
  }

  /// Handling native events
  void _handleEvent(Map<dynamic, dynamic> event) {
    final name = event['name'];
    final data = event['data'];
    switch (name) {
      case 'torchState':
        final state = TorchState.values[data];
        torchState.value = state;
        break;
      case 'image':
        final cameraImage = CameraImage.fromMap(data);
        _imageController.add(cameraImage);
        break;
      case 'streamingState':
        streamingState.value = data;
        break;
      default:
        throw UnimplementedError();
    }
  }

  Future<void> _stopCamera() async => await _method.invokeMethod('stopCamera');

  void _ensure(String name) {
    final message =
        'CameraController.$name called after CameraController.dispose\n'
        'CameraController methods should not be used after calling dispose.';
    assert(hashCode == _id, message);
  }

  @override
  Future<void> initCamera() async {
    _ensure('startAsync');

    // Check authorization state.
    var granted = await _method.invokeMethod('permissionState');
    var state = CameraPermissionState.values[granted ? 1 : 0];

    if (state == CameraPermissionState.undetermined) {
      final result = await _method.invokeMethod('requestPermission');
      state = result
          ? CameraPermissionState.authorized
          : CameraPermissionState.denied;
    }

    if (state != CameraPermissionState.authorized) {
      throw PlatformException(code: 'NO ACCESS');
    }

    // Initialize camera.
    final result = await _method.invokeMapMethod<String, dynamic>(
      'initCamera',
      {
        'selector': facing.index,
        'size': cameraSize.getSize().toMap(),
      },
    );
    final textureId = result?['textureId'];
    final size = sizeFromMap(result?['size']);
    args.value = CameraArgs(textureId, size);
    _torchable = result?['torchable'];
  }

  @override
  Future<void> startImageStream([int? delay, bool? debugging = false]) async {
    _ensure('startImageStream');
    await _method.invokeMethod(
      'startImageStream',
      {
        'delay': delay,
        'debugging': debugging,
      },
    );
  }

  @override
  Future<void> stopImageStream() async {
    _ensure('stopImageStream');
    await _method.invokeMethod('stopImageStream');
  }

  @override
  Future<void> setOutputImageSize(Size size) async {
    _ensure('setOutputImageSize');
    await _method.invokeMethod('setOutputImageSize', size.toMap());
  }

  @override
  void torch() {
    _ensure('torch');
    if (!_torchable) {
      return;
    }
    var state =
        torchState.value == TorchState.off ? TorchState.on : TorchState.off;
    _method.invokeMethod('torch', state.index);
  }

  @override
  Future<void> dispose() async {
    if (hashCode == _id) {
      await _stopCamera();
      await _subscription?.cancel();
      _subscription = null;
      _id = null;
    }
    await _imageController.close();
  }
}
