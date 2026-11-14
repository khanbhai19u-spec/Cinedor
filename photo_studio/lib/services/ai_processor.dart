import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:google_mlkit_selfie_segmentation/google_mlkit_selfie_segmentation.dart';
import 'package:path_provider/path_provider.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';
import 'image_ops.dart';

/// On-device (offline) AI: Google ML Kit selfie segmentation for background
/// removal and face detection for automatic passport framing.
class AiProcessor {
  AiProcessor._();
  static final AiProcessor instance = AiProcessor._();

  SelfieSegmenter? _segmenter;
  FaceDetector? _faces;
  Future<void> _mlLock = Future.value();
  Directory? _workDir;

  Future<Directory> workDir() async {
    if (_workDir != null) return _workDir!;
    final base = await getApplicationSupportDirectory();
    final d = Directory('${base.path}/work');
    await d.create(recursive: true);
    return _workDir = d;
  }

  /// ML Kit calls are serialised; image work runs in background isolates.
  Future<T> _ml<T>(Future<T> Function() f) {
    final c = Completer<T>();
    _mlLock = _mlLock.then((_) async {
      try {
        c.complete(await f());
      } catch (e, s) {
        c.completeError(e, s);
      }
    });
    return c.future;
  }

  Future<void> process(PhotoItem p, PhotoSize size, double softness) async {
    final dir = await workDir();
    final norm = '${dir.path}/${p.id}.jpg';
    final seg = '${dir.path}/${p.id}_seg.jpg';
    final maskPath = '${dir.path}/${p.id}.mask';
    final src = p.sourcePath;

    // 1. Normalise (EXIF rotation, resize).
    final nr = await normalizeInIsolate(src, norm, seg);
    p.normPath = norm;
    p.width = nr.width;
    p.height = nr.height;

    // 2. Face detection (for automatic crop).
    _faces ??= FaceDetector(
        options: FaceDetectorOptions(
            performanceMode: FaceDetectorMode.accurate, minFaceSize: 0.08));
    final faces =
        await _ml(() => _faces!.processImage(InputImage.fromFilePath(norm)));
    Face? face;
    for (final f in faces) {
      if (face == null ||
          f.boundingBox.width * f.boundingBox.height >
              face.boundingBox.width * face.boundingBox.height) {
        face = f;
      }
    }

    // 3. Segmentation (background removal).
    Float32List? conf;
    var mw = 0, mh = 0;
    try {
      _segmenter ??= SelfieSegmenter(mode: SegmenterMode.single);
      final m = await _ml(
          () => _segmenter!.processImage(InputImage.fromFilePath(seg)));
      if (m != null && m.confidences.length == m.width * m.height) {
        conf = Float32List.fromList(m.confidences);
        mw = m.width;
        mh = m.height;
      }
    } catch (_) {
      conf = null; // fall back to "keep original background"
    }
    try {
      File(seg).deleteSync();
    } catch (_) {}

    // 4. Mask refinement + previews.
    final fb = face?.boundingBox;
    final job = MaskJob(
      normPath: norm,
      maskPath: maskPath,
      width: nr.width,
      height: nr.height,
      confidences: conf,
      maskW: mw,
      maskH: mh,
      softness: softness,
      faceCx: fb == null ? -1 : fb.center.dx,
      faceTop: fb == null ? -1 : fb.top,
      faceW: fb == null ? -1 : fb.width,
    );
    final r = await buildMaskInIsolate(job);
    p.maskPath = r.hasMask ? maskPath : null;
    p.previewPng = r.previewCutout;
    p.previewOrig = r.previewOrig;
    p.previewScale = r.previewScale;
    p.faceFound = fb != null;
    p.faceTop = fb?.top ?? -1;
    p.faceBottom = fb?.bottom ?? -1;
    p.faceCx = fb?.center.dx ?? -1;
    p.faceH = fb?.height ?? -1;
    p.crownY = r.crownY;
    if (!r.hasMask) p.removeBackground = false;

    autoCrop(p, size);
  }

  /// Positions the crop so the head fills the size's recommended proportion.
  static void autoCrop(PhotoItem p, PhotoSize size) {
    final w = p.width.toDouble(), h = p.height.toDouble();
    if (p.faceFound) {
      final chin = p.faceBottom + p.faceH * 0.04;
      // Crown from the segmentation mask, else estimated from the face box.
      var crown = p.crownY;
      final est = p.faceTop - p.faceH * 0.38;
      if (crown < 0 || crown > p.faceTop || crown < est - p.faceH * 0.5) {
        crown = est;
      }
      final head = chin - crown;
      p.cropH = head / size.headRatio;
      final cropTop = crown - size.topRatio * p.cropH;
      p.cropCy = cropTop + p.cropH / 2;
      p.cropCx = p.faceCx;
    } else {
      // No face: largest centred crop of the right aspect, biased upwards.
      p.cropH = math.min(h, w / size.aspect);
      p.cropCx = w / 2;
      p.cropCy = math.max(p.cropH / 2, h * 0.42);
      p.cropCy = math.min(p.cropCy, h - p.cropH / 2);
    }
  }

  Future<void> dispose() async {
    await _segmenter?.close();
    await _faces?.close();
    _segmenter = null;
    _faces = null;
  }
}
