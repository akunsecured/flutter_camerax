import 'dart:typed_data';
import 'dart:ui';

import 'camera_size.dart';

class CameraTimeStatistics {
  final int processTime;
  final int sentTime;
  final int transferTime;

  CameraTimeStatistics({
    required this.processTime,
    required this.sentTime,
    required this.transferTime,
  });

  factory CameraTimeStatistics.fromMap(Map map) {
    final int sentTime = map['sent_time'] ?? -1;

    return CameraTimeStatistics(
      processTime: map['process_time'] ?? -1,
      sentTime: sentTime,
      transferTime: DateTime.now().millisecondsSinceEpoch - sentTime,
    );
  }
}

class CameraImage {
  final Uint8List imageBytes;
  final int timestamp;
  final Size size;
  final CameraTimeStatistics? timeStatistics;

  CameraImage({
    required this.imageBytes,
    required this.timestamp,
    required this.size,
    this.timeStatistics,
  });

  factory CameraImage.fromMap(Map map) => CameraImage(
        imageBytes: map['bytes'] ?? Uint8List(0),
        timestamp: map['timestamp'] ?? -1,
        size: sizeFromMap(
          map['size'] ?? {},
        ),
        timeStatistics: map['time_statistics'] != null
            ? CameraTimeStatistics.fromMap(map['time_statistics'])
            : null,
      );
}
