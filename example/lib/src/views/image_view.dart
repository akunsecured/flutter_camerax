import 'package:camerax/camerax.dart';
import 'package:flutter/material.dart';

class Images extends StatelessWidget {
  final List<CameraImage> images;

  const Images(this.images, {Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size.width / 3;

    return Scaffold(
      appBar: AppBar(
        centerTitle: true,
        title: Text('Images'),
      ),
      body: SingleChildScrollView(
        child: Wrap(
          children: images
              .map(
                (image) => Container(
                  width: size,
                  child: AspectRatio(
                    aspectRatio: 1,
                    child: Image.memory(
                      image.imageBytes,
                      width: size,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
