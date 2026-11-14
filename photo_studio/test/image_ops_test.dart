import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:photo_studio/services/image_ops.dart';

void main() {
  late Directory dir;
  setUp(() => dir = Directory.systemTemp.createTempSync('ps_test'));
  tearDown(() => dir.deleteSync(recursive: true));

  // A fake portrait: green background with a skin-coloured "head" ellipse.
  img.Image makePortrait(int w, int h) {
    final im = img.Image(width: w, height: h);
    img.fill(im, color: img.ColorRgb8(0, 200, 0));
    img.fillCircle(im,
        x: w ~/ 2, y: h ~/ 2, radius: w ~/ 4, color: img.ColorRgb8(220, 170, 140));
    return im;
  }

  test('normalize resizes large images and writes seg copy', () {
    final src = '${dir.path}/src.png';
    File(src).writeAsBytesSync(img.encodePng(makePortrait(2400, 3200)));
    final r = normalizeImage(src, '${dir.path}/n.jpg', '${dir.path}/s.jpg');
    expect(r.height, kMaxWorkSide);
    expect(r.width, 1200);
    final seg = img.decodeJpg(File('${dir.path}/s.jpg').readAsBytesSync())!;
    expect(math.max(seg.width, seg.height), kSegSide);
  });

  test('mask + render replaces background and keeps the subject', () {
    const w = 400, h = 600;
    final norm = '${dir.path}/n.jpg';
    File(norm).writeAsBytesSync(img.encodeJpg(makePortrait(w, h), quality: 100));

    // Low-res confidence mask (as ML Kit would return for a smaller input).
    const mw = 200, mh = 300;
    final conf = Float32List(mw * mh);
    for (var y = 0; y < mh; y++) {
      for (var x = 0; x < mw; x++) {
        final d = math.sqrt(math.pow(x - mw / 2, 2) + math.pow(y - mh / 2, 2));
        conf[y * mw + x] = d < mw / 4 ? 0.95 : 0.03;
      }
    }
    // A stray speck that should be removed.
    for (var y = 5; y < 8; y++) {
      for (var x = 5; x < 8; x++) {
        conf[y * mw + x] = 0.99;
      }
    }

    final res = buildMask(MaskJob(
      normPath: norm,
      maskPath: '${dir.path}/m.mask',
      width: w,
      height: h,
      confidences: conf,
      maskW: mw,
      maskH: mh,
      softness: 0.5,
      faceCx: w / 2,
      faceTop: h / 2 - 60,
      faceW: 120,
    ));
    expect(res.hasMask, isTrue);
    // Crown ≈ top of the circle (h/2 - w/4 = 200).
    expect(res.crownY, inInclusiveRange(190, 215));
    final mask = File('${dir.path}/m.mask').readAsBytesSync();
    expect(mask.length, w * h);
    expect(mask[12 * w + 12], 0, reason: 'speck removed');

    final out = renderPhoto(RenderJob(
      normPath: norm,
      maskPath: '${dir.path}/m.mask',
      width: w,
      height: h,
      cropCx: w / 2,
      cropCy: h / 2,
      cropH: h.toDouble(),
      outW: 200,
      outH: 300,
      bgArgb: 0xFF0000FF, // blue
      brightness: 0,
      contrast: 1,
    ));
    expect(out.width, 200);
    final corner = out.getPixel(3, 3);
    expect([corner.r, corner.g, corner.b], [0, 0, 255]);
    final centre = out.getPixel(100, 150);
    expect(centre.r, closeTo(220, 8));
    expect(centre.g, closeTo(170, 8));
    expect(centre.b, closeTo(140, 8));
  });

  test('render without mask keeps original and pads outside with bg', () {
    const w = 300, h = 300;
    final norm = '${dir.path}/n.jpg';
    File(norm).writeAsBytesSync(img.encodeJpg(makePortrait(w, h), quality: 100));
    final out = renderPhoto(RenderJob(
      normPath: norm,
      maskPath: null,
      width: w,
      height: h,
      cropCx: 0, // crop centred on the left edge → left half is outside
      cropCy: h / 2,
      cropH: h.toDouble(),
      outW: 100,
      outH: 100,
      bgArgb: 0xFFFFFFFF,
      brightness: 0,
      contrast: 1,
    ));
    final left = out.getPixel(5, 50);
    expect([left.r, left.g, left.b], [255, 255, 255]);
    final right = out.getPixel(95, 5);
    expect(right.g, greaterThan(180)); // original green background kept
    expect(right.r, lessThan(30));
  });

  test('mmToPx', () {
    expect(mmToPx(25.4, 300), 300);
    expect(mmToPx(35, 300), 413);
    expect(mmToPx(45, 300), 531);
  });
}
