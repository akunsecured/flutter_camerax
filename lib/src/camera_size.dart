import 'dart:ui';

enum CameraSize {
  HD,
  HD_PLUS,
  FHD,
  QHD,
  UHD,
}

extension CameraSizeExt on CameraSize {
  Size getSize() {
    switch (this) {
      case CameraSize.UHD:
        return Size(3840, 2160);
      case CameraSize.QHD:
        return Size(2560, 1440);
      case CameraSize.FHD:
        return Size(1920, 1080);
      case CameraSize.HD_PLUS:
        return Size(1600, 900);
      default:
        return Size(1280, 720);
    }
  }
}

Size sizeFromMap(Map<dynamic, dynamic> data) {
  final width = data['width'] ?? -1;
  final height = data['height'] ?? -1;
  return Size(width.toDouble(), height.toDouble());
}

extension SizeExt on Size {
  Map<String, dynamic> toMap() => {
        'width': width,
        'height': height,
      };
}
