import 'package:flutter/material.dart';

Size toSize(Map<dynamic, dynamic> data) {
  final width = data['width'] ?? -1;
  final height = data['height'] ?? -1;
  return Size(width.toDouble(), height.toDouble());
}
