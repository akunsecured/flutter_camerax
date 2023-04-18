import 'dart:typed_data';

class ImageSize {
  final int width;
  final int height;

  ImageSize({
    required this.width,
    required this.height,
  });

  factory ImageSize.fromMap(Map map) => ImageSize(
        width: map['width'] ?? -1,
        height: map['height'] ?? -1,
      );
}

class CameraImage {
  final Uint8List imageBytes;
  final int timestamp;
  final int sent;
  final int processTime;
  final ImageSize size;

  int? arrivalTime;

  CameraImage({
    required this.imageBytes,
    required this.timestamp,
    required this.sent,
    required this.size,
    required this.processTime,
    this.arrivalTime,
  });

  factory CameraImage.fromNative(Map map) => CameraImage(
        imageBytes: map['bytes'] ?? Uint8List(0),
        timestamp: map['timestamp'] ?? -1,
        sent: map['sent'] ?? -1,
        processTime: map['process_time'] ?? -1,
        size: ImageSize.fromMap(map['size'] ?? {}),
      );
}
