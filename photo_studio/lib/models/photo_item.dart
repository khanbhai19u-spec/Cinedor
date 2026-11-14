import 'dart:typed_data';
import 'dart:ui' show Color;

enum PhotoStatus { pending, processing, done, failed }

/// A photo in the current batch.
///
/// Coordinates (crop centre and crop height) are in pixels of the
/// *normalised* image (EXIF-rotated, max 1600 px) stored at [normPath].
class PhotoItem {
  final String id;
  final String sourcePath;

  String? normPath;
  int width = 0;
  int height = 0;

  /// Segmentation alpha mask (0 = background, 255 = person), width×height.
  String? maskPath;

  /// Small RGBA PNG cut-out used for on-screen previews.
  Uint8List? previewPng;
  Uint8List? previewOrig; // small JPG of the original (background kept)
  double previewScale = 1; // previewPng width / [width]

  PhotoStatus status = PhotoStatus.pending;
  String? error;

  // Face / head geometry (normalised-image pixels, -1 if unknown).
  bool faceFound = false;
  double faceTop = -1, faceBottom = -1, faceCx = -1, faceH = -1;
  double crownY = -1;

  // Crop (in normalised-image pixels).
  double cropCx = 0;
  double cropCy = 0;
  double cropH = 0;

  // Per-photo options.
  bool removeBackground = true;
  Color? bgOverride;
  double brightness = 0; // -0.5 .. 0.5
  double contrast = 1; // 0.5 .. 1.5

  PhotoItem({required this.id, required this.sourcePath});

  bool get isReady => status == PhotoStatus.done;
}
