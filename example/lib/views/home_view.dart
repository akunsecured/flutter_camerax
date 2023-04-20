import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:camerax/camerax.dart';
import 'package:camerax_example/utils.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

class HomeView extends StatefulWidget {
  @override
  State<HomeView> createState() => _HomeViewState();
}

class _HomeViewState extends State<HomeView> {
  late CameraController _cameraController;
  Directory? _saveDir;

  StreamSubscription<CameraImage>? _imageSub;

  final List<CameraImage> images = [];

  final List<int> averageTimeToTakePicture = [];
  final List<int> averageTimeToProcessImage = [];
  final List<int> averageTimeToArrive = [];

  final bool isDebugging = true;
  final bool isEmulator = false;

  @override
  void initState() {
    super.initState();

    _cameraController = CameraController(size: CameraSize.HD);
    _init();
  }

  Future<Directory> _getSaveDirectory() async {
    Directory dir;
    var downloadDir = Directory('/storage/emulated/0/Download');

    if (!(await downloadDir.exists())) {
      dir = await getExternalStorageDirectory() as Directory;
    } else {
      dir = Directory('/storage/emulated/0/Download/CameraX');
      if (!(await dir.exists())) {
        await dir.create();
      }
    }

    return dir;
  }

  Future<void> _init() async {
    await Permission.storage.request();

    _saveDir = await _getSaveDirectory();
    if (isDebugging) {
      print('Saving images to ${_saveDir?.path}');
    }

    await _cameraController.initCamera();
  }

  @override
  void dispose() {
    _cameraController.dispose();

    super.dispose();
  }

  Future<void> _saveImage(Uint8List bytes, int timestamp) async {
    final imagePath = '${_saveDir!.path}/$timestamp.jpeg';
    final imageFile = File(imagePath);
    await imageFile.writeAsBytes(bytes);
  }

  Future<void> _startImageStream() async {
    images.clear();

    averageTimeToTakePicture.clear();
    averageTimeToProcessImage.clear();
    averageTimeToArrive.clear();

    _imageSub = _cameraController.images.listen((event) async {
      print(
          'New image at ${(DateTime.fromMillisecondsSinceEpoch(event.timestamp))}');

      unawaited(_saveImage(event.imageBytes, event.timestamp));

      if (isDebugging && event.timeStatistics != null) {
        print('\nTime to transfer: ${event.timeStatistics!.transferTime} ms');
        averageTimeToArrive.add(event.timeStatistics!.transferTime);
        averageTimeToProcessImage.add(event.timeStatistics!.processTime);

        if (images.isNotEmpty) {
          averageTimeToTakePicture.add(event.timestamp - images.last.timestamp);
        }
      }

      images.add(event);
    });

    await _cameraController.startImageStream(250, isDebugging);
  }

  Future<void> _stopImageStream() async {
    await _cameraController.stopImageStream();

    await _imageSub?.cancel();
    _imageSub = null;

    if (isDebugging) {
      print(
          'Average time to take a picture: ${averageTimeToTakePicture.average} ms');
      print(
          'Average time to process an image: ${averageTimeToProcessImage.average} ms');
      print(
          'Average time to wait for the arrival of a picture: ${averageTimeToArrive.average} ms');
    }
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
