import 'dart:async';
import 'dart:ui' show Color;
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:photo_studio/models/photo_item.dart';
import 'package:photo_studio/models/photo_size.dart';
import 'package:photo_studio/services/image_ops.dart';
import 'package:photo_studio/services/output_service.dart';

// Regression: background isolates must not capture unsendable objects
// (UI callbacks, Completers, ML Kit handles) from the calling scope.
void main() {
  test('renderAll works when onProgress captures unsendable state', () async {
    final dir = Directory.systemTemp.createTempSync('ps_iso');
    final norm = '${dir.path}/n.jpg';
    File(norm).writeAsBytesSync(img.encodeJpg(img.Image(width: 200, height: 260)));
    final p = PhotoItem(id: 'a', sourcePath: norm)
      ..normPath = norm
      ..width = 200
      ..height = 260
      ..cropCx = 100
      ..cropCy = 130
      ..cropH = 260
      ..removeBackground = false;
    final unsendable = Completer<void>(); // like a BuildContext / State
    var calls = 0;
    final r = await OutputService.renderAll([p], PhotoSize.presets.first, 300,
        const Color(0xFFFFFFFF), onProgress: (d, t) {
      calls++;
      if (unsendable.isCompleted) return;
    });
    expect(r.single.image.width, mmToPx(35, 300));
    expect(calls, 1);
    dir.deleteSync(recursive: true);
  });

  test('normalize/mask helpers run in an isolate from an object with state', () async {
    final dir = Directory.systemTemp.createTempSync('ps_iso2');
    final src = '${dir.path}/s.png';
    File(src).writeAsBytesSync(img.encodePng(img.Image(width: 300, height: 400)));
    final holder = _StatefulHolder();
    final r = await holder.run(src, dir.path);
    expect(r.width, 300);
    dir.deleteSync(recursive: true);
  });
}

class _StatefulHolder {
  final lock = Completer<void>(); // unsendable field, like AiProcessor._mlLock
  Future<NormResult> run(String src, String d) async {
    final r = await normalizeInIsolate(src, '$d/n.jpg', '$d/g.jpg');
    // A closure using `this`, as in AiProcessor.process.
    Future<void> f() async => lock.isCompleted;
    await f();
    final m = await buildMaskInIsolate(MaskJob(
        normPath: '$d/n.jpg', maskPath: '$d/m', width: r.width, height: r.height,
        confidences: null, maskW: 0, maskH: 0, softness: 0.5,
        faceCx: -1, faceTop: -1, faceW: -1));
    expect(m.hasMask, isFalse);
    return r;
  }
}
