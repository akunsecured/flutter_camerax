import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'barcode.dart';
import 'camera_args.dart';
import 'camera_facing.dart';
import 'camera_image.dart';
import 'torch_state.dart';
import 'util.dart';

/// A camera controller.
abstract class CameraController {
  /// Arguments for [CameraView].
  ValueNotifier<CameraArgs?> get args;

  /// Torch state of the camera.
  ValueNotifier<TorchState> get torchState;

  ValueNotifier<bool> get streamingState;

  bool get isStreaming;

  /// A stream of barcodes.
  Stream<Barcode> get barcodes;

  Stream<CameraImage> get images;

  /// Create a [CameraController].
  ///
  /// [facing] target facing used to select camera.
  ///
  /// [formats] the barcode formats for image analyzer.
  factory CameraController([CameraFacing facing = CameraFacing.back]) =>
      _CameraController(facing);

  /// Start the camera asynchronously.
  Future<void> startAsync();

  Future<void> startImageStream([int? delay]);

  Future<void> stopImageStream();

  /// Switch the torch's state.
  void torch();

  /// Release the resources of the camera.
  void dispose();
}

class _CameraController implements CameraController {
  static const MethodChannel method =
      MethodChannel('yanshouwang.dev/camerax/method');
  static const EventChannel event =
      EventChannel('yanshouwang.dev/camerax/event');

  static const undetermined = 0;
  static const authorized = 1;
  static const denied = 2;

  static int? id;
  static StreamSubscription? subscription;

  final CameraFacing facing;
  @override
  final ValueNotifier<CameraArgs?> args;
  @override
  final ValueNotifier<TorchState> torchState;
  @override
  final ValueNotifier<bool> streamingState;

  @override
  bool get isStreaming => streamingState.value;

  bool torchable;
  late StreamController<Barcode> barcodesController;
  late StreamController<CameraImage> imageController;

  @override
  Stream<Barcode> get barcodes => barcodesController.stream;

  @override
  Stream<CameraImage> get images => imageController.stream;

  _CameraController(this.facing)
      : args = ValueNotifier(null),
        torchState = ValueNotifier(TorchState.off),
        streamingState = ValueNotifier(false),
        torchable = false {
    // In case new instance before dispose.
    if (id != null) {
      stop();
    }
    id = hashCode;
    // Create barcode stream controller.
    barcodesController = StreamController.broadcast();

    imageController = StreamController.broadcast();

    // Listen event handler.
    subscription =
        event.receiveBroadcastStream().listen((data) => handleEvent(data));
  }

  void handleEvent(Map<dynamic, dynamic> event) {
    final name = event['name'];
    final data = event['data'];
    switch (name) {
      case 'torchState':
        final state = TorchState.values[data];
        torchState.value = state;
        break;
      case 'barcode':
        final barcode = Barcode.fromNative(data);
        barcodesController.add(barcode);
        break;
      case 'imageBytes':
        final cameraImage = CameraImage.fromNative(data);
        imageController.add(cameraImage);
        break;
      case 'streaming':
        streamingState.value = data;
        break;
      default:
        throw UnimplementedError();
    }
  }

  void tryAnalyze(int mode) {
    if (hashCode != id) {
      return;
    }
    method.invokeMethod('analyze', mode);
  }

  @override
  Future<void> startAsync() async {
    ensure('startAsync');
    // Check authorization state.
    var state = await method.invokeMethod('state');
    if (state == undetermined) {
      final result = await method.invokeMethod('request');
      state = result ? authorized : denied;
    }
    if (state != authorized) {
      throw PlatformException(code: 'NO ACCESS');
    }
    // Start camera.
    final answer =
        await method.invokeMapMethod<String, dynamic>('start', facing.index);
    final textureId = answer?['textureId'];
    final size = toSize(answer?['size']);
    args.value = CameraArgs(textureId, size);
    torchable = answer?['torchable'];
  }

  @override
  void torch() {
    ensure('torch');
    if (!torchable) {
      return;
    }
    var state =
        torchState.value == TorchState.off ? TorchState.on : TorchState.off;
    method.invokeMethod('torch', state.index);
  }

  @override
  void dispose() {
    if (hashCode == id) {
      stop();
      subscription?.cancel();
      subscription = null;
      id = null;
    }
    barcodesController.close();
  }

  void stop() => method.invokeMethod('stop');

  void ensure(String name) {
    final message =
        'CameraController.$name called after CameraController.dispose\n'
        'CameraController methods should not be used after calling dispose.';
    assert(hashCode == id, message);
  }

  @override
  Future<void> startImageStream([int? delay]) async {
    ensure('startImageStream');
    await method.invokeMethod('startImageStream', delay);
  }

  @override
  Future<void> stopImageStream() async {
    ensure('stopImageStream');
    await method.invokeMethod('stopImageStream');
  }
}
