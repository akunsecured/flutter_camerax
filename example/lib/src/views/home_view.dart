import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:camerax/camerax.dart';
import 'package:camerax_example/src/views/image_view.dart';
import 'package:camerax_example/utils.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

class HomeView extends StatefulWidget {
  @override
  State<HomeView> createState() => _HomeViewState();
}

class _HomeViewState extends State<HomeView> {
  late CameraController _cameraController;
  Directory? _externalDir;

  StreamSubscription<CameraImage>? _imageSub;

  final List<CameraImage> images = [];

  final List<int> averageTimeToTakePicture = [];
  final List<int> averageTimeToProcessImage = [];
  final List<int> averageTimeToArrive = [];

  @override
  void initState() {
    super.initState();

    _cameraController = CameraController();
    _init();
  }

  Future<void> _init() async {
    _externalDir = (await getExternalStorageDirectory())!;

    await _cameraController.startAsync();
  }

  @override
  void dispose() {
    _cameraController.dispose();

    super.dispose();
  }

  Future<void> _saveImage(Uint8List bytes, int timestamp) async {
    final imagePath = '${_externalDir!.path}/$timestamp.jpeg';
    final imageFile = File(imagePath);
    await imageFile.writeAsBytes(bytes);
  }

  Future<void> _startImageStream() async {
    images.clear();

    averageTimeToTakePicture.clear();
    averageTimeToProcessImage.clear();
    averageTimeToArrive.clear();

    _imageSub = _cameraController.images.listen((event) async {
      event.arrivalTime = DateTime.now().millisecondsSinceEpoch - event.sent;
      print(
          'New image at ${(DateTime.fromMillisecondsSinceEpoch(event.timestamp))}\nTime to transfer: ${event.arrivalTime} ms');

      unawaited(_saveImage(event.imageBytes, event.timestamp));

      averageTimeToArrive.add(event.arrivalTime!);
      averageTimeToProcessImage.add(event.processTime);

      if (images.isNotEmpty) {
        averageTimeToTakePicture.add(event.timestamp - images.last.timestamp);
      }

      images.add(event);
    });

    await _cameraController.startImageStream(1000);
  }

  void goToResults() => Navigator.of(context)
      .push(MaterialPageRoute(builder: (_) => Images(images)));

  Future<void> _stopImageStream() async {
    await _cameraController.stopImageStream();

    await _imageSub?.cancel();
    _imageSub = null;

    print(
        'Average time to take a picture: ${averageTimeToTakePicture.average} ms');
    print(
        'Average time to process an image: ${averageTimeToProcessImage.average} ms');
    print(
        'Average time to wait for the arrival of a picture: ${averageTimeToArrive.average} ms');

    goToResults();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('CameraX'),
      ),
      body: CameraView(_cameraController),
      floatingActionButton: ValueListenableBuilder<bool>(
        valueListenable: _cameraController.streamingState,
        builder: (_, bool isStreaming, __) => FloatingActionButton(
          backgroundColor: isStreaming ? Colors.red : Colors.green,
          // onPressed: () => Navigator.of(context).pushNamed('analyze'),
          onPressed: () async {
            if (isStreaming) {
              await _stopImageStream();
            } else {
              await _startImageStream();
            }
          },
          child: Icon(
            Icons.camera,
            color: Colors.white,
          ),
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
    );
  }
}
