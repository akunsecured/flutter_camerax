import 'package:camerax/camerax.dart';
import 'package:flutter/material.dart';

class AnalyzeView extends StatefulWidget {
  @override
  _AnalyzeViewState createState() => _AnalyzeViewState();
}

class _AnalyzeViewState extends State<AnalyzeView> {
  late CameraController cameraController;

  @override
  void initState() {
    super.initState();
    cameraController = CameraController();
    start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          CameraView(cameraController),
          Positioned(
            left: 24.0,
            top: 32.0,
            child: IconButton(
              icon: Icon(Icons.cancel, color: Colors.white),
              onPressed: () => Navigator.of(context).pop(),
            ),
          ),
          Container(
            alignment: Alignment.bottomCenter,
            margin: EdgeInsets.only(bottom: 80.0),
            child: IconButton(
              icon: ValueListenableBuilder(
                valueListenable: cameraController.torchState,
                builder: (context, state, child) {
                  final color =
                      state == TorchState.off ? Colors.grey : Colors.white;
                  return Icon(Icons.bolt, color: color);
                },
              ),
              iconSize: 32.0,
              onPressed: () => cameraController.torch(),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    print("Dispose called");
    cameraController.dispose();
    super.dispose();
  }

  void start() async {
    await cameraController.startAsync();
  }

  void display(Barcode barcode) {
    Navigator.of(context).popAndPushNamed('display', arguments: barcode);
  }
}