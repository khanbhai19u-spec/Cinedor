// Pure-Dart image work. Everything here is safe to run inside Isolate.run().
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;

const int kMaxWorkSide = 1600;
const int kPreviewSide = 720;

/// Image size fed to the segmentation model. Its internal resolution is
/// ~256 px, so a bigger input only makes the platform-channel payload larger.
const int kSegSide = 640;

class NormResult {
  final int width, height;
  NormResult(this.width, this.height);
}

/// Decodes [src], applies EXIF rotation, downsizes to [kMaxWorkSide] and
/// writes a JPEG to [dst], plus a small copy for segmentation to [segDst].
NormResult normalizeImage(String src, String dst, String segDst) {
  final bytes = File(src).readAsBytesSync();
  var im = img.decodeImage(bytes);
  if (im == null) throw Exception('ছবিটি পড়া যায়নি (unsupported format)');
  im = img.bakeOrientation(im);
  if (im.numChannels == 4 || im.hasPalette) {
    // Flatten transparency / palette onto white.
    final flat = img.Image(width: im.width, height: im.height);
    img.fill(flat, color: img.ColorRgb8(255, 255, 255));
    img.compositeImage(flat, im);
    im = flat;
  }
  final longest = math.max(im.width, im.height);
  if (longest > kMaxWorkSide) {
    im = im.width >= im.height
        ? img.copyResize(im,
            width: kMaxWorkSide, interpolation: img.Interpolation.average)
        : img.copyResize(im,
            height: kMaxWorkSide, interpolation: img.Interpolation.average);
  }
  File(dst).writeAsBytesSync(img.encodeJpg(im, quality: 95));
  final segScale = kSegSide / math.max(im.width, im.height);
  final seg = segScale < 1
      ? img.copyResize(im,
          width: math.max(1, (im.width * segScale).round()),
          height: math.max(1, (im.height * segScale).round()),
          interpolation: img.Interpolation.average)
      : im;
  File(segDst).writeAsBytesSync(img.encodeJpg(seg, quality: 92));
  return NormResult(im.width, im.height);
}

class MaskJob {
  final String normPath;
  final String maskPath;
  final int width, height;
  final Float32List? confidences; // null = no segmentation (keep everything)
  final int maskW, maskH;
  final double softness; // 0..1, edge feathering strength
  final double faceCx, faceTop, faceW; // face box, -1 if none
  MaskJob({
    required this.normPath,
    required this.maskPath,
    required this.width,
    required this.height,
    required this.confidences,
    required this.maskW,
    required this.maskH,
    required this.softness,
    required this.faceCx,
    required this.faceTop,
    required this.faceW,
  });
}

class MaskResult {
  final Uint8List previewCutout; // RGBA PNG
  final Uint8List previewOrig; // JPG
  final double previewScale;
  final double crownY; // -1 if unknown
  final bool hasMask;
  MaskResult(this.previewCutout, this.previewOrig, this.previewScale,
      this.crownY, this.hasMask);
}

/// Turns raw segmentation confidences into a clean 8-bit alpha mask, stores it
/// and builds preview images. Also locates the top of the head (crown).
MaskResult buildMask(MaskJob j) {
  final w = j.width, h = j.height;
  Uint8List? alpha;

  if (j.confidences != null) {
    alpha = Uint8List(w * h);
    final c = j.confidences!;
    final sameSize = j.maskW == w && j.maskH == h;
    // Threshold window: narrower = harder edge.
    final lo = 0.5 - 0.25 * (0.4 + j.softness);
    final hi = 0.5 + 0.25 * (0.4 + j.softness);
    final span = hi - lo;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        double v;
        if (sameSize) {
          v = c[y * w + x];
        } else {
          // Bilinear upsample of a smaller mask.
          final fx = (x + 0.5) * j.maskW / w - 0.5;
          final fy = (y + 0.5) * j.maskH / h - 0.5;
          final x0 = fx.floor().clamp(0, j.maskW - 1);
          final y0 = fy.floor().clamp(0, j.maskH - 1);
          final x1 = math.min(x0 + 1, j.maskW - 1);
          final y1 = math.min(y0 + 1, j.maskH - 1);
          final ax = (fx - x0).clamp(0.0, 1.0);
          final ay = (fy - y0).clamp(0.0, 1.0);
          final a = c[y0 * j.maskW + x0] * (1 - ax) + c[y0 * j.maskW + x1] * ax;
          final b = c[y1 * j.maskW + x0] * (1 - ax) + c[y1 * j.maskW + x1] * ax;
          v = a * (1 - ay) + b * ay;
        }
        var t = ((v - lo) / span).clamp(0.0, 1.0);
        t = t * t * (3 - 2 * t); // smoothstep
        alpha[y * w + x] = (t * 255).round();
      }
    }
    _removeSmallSpecks(alpha, w, h);
    final r = (1 + j.softness * 2).round();
    alpha = _boxBlur(alpha, w, h, r);
    File(j.maskPath).writeAsBytesSync(alpha);
  }

  // Crown detection: first row above the face where the person mask is solid.
  double crown = -1;
  if (alpha != null && j.faceCx >= 0) {
    final half = math.max(2, (j.faceW * 0.22).round());
    final x0 = (j.faceCx - half).round().clamp(0, w - 1);
    final x1 = (j.faceCx + half).round().clamp(0, w - 1);
    final limit = j.faceTop.round().clamp(0, h - 1);
    for (var y = 0; y <= limit; y++) {
      var sum = 0;
      for (var x = x0; x <= x1; x++) {
        sum += alpha[y * w + x];
      }
      if (sum / (x1 - x0 + 1) > 140) {
        crown = y.toDouble();
        break;
      }
    }
  }

  // Previews.
  final src = img.decodeJpg(File(j.normPath).readAsBytesSync())!;
  final scale = math.min(1.0, kPreviewSide / math.max(w, h));
  final pw = math.max(1, (w * scale).round());
  final ph = math.max(1, (h * scale).round());
  final small = img.copyResize(src,
      width: pw, height: ph, interpolation: img.Interpolation.average);
  final orig = img.encodeJpg(small, quality: 88);

  final cut = img.Image(width: pw, height: ph, numChannels: 4);
  for (var y = 0; y < ph; y++) {
    final sy = math.min(h - 1, (y / scale).floor());
    for (var x = 0; x < pw; x++) {
      final sx = math.min(w - 1, (x / scale).floor());
      final p = small.getPixel(x, y);
      final a = alpha == null ? 255 : alpha[sy * w + sx];
      cut.setPixelRgba(x, y, p.r, p.g, p.b, a);
    }
  }
  final cutPng = img.encodePng(cut, level: 1);
  return MaskResult(cutPng, orig, pw / w, crown, alpha != null);
}

/// Separable box blur, radius [r].
Uint8List _boxBlur(Uint8List a, int w, int h, int r) {
  if (r <= 0) return a;
  final tmp = Uint8List(w * h);
  final out = Uint8List(w * h);
  final win = 2 * r + 1;
  for (var y = 0; y < h; y++) {
    var sum = 0;
    final row = y * w;
    for (var k = -r; k <= r; k++) {
      sum += a[row + k.clamp(0, w - 1)];
    }
    for (var x = 0; x < w; x++) {
      tmp[row + x] = sum ~/ win;
      sum += a[row + math.min(w - 1, x + r + 1)] - a[row + math.max(0, x - r)];
    }
  }
  for (var x = 0; x < w; x++) {
    var sum = 0;
    for (var k = -r; k <= r; k++) {
      sum += tmp[k.clamp(0, h - 1) * w + x];
    }
    for (var y = 0; y < h; y++) {
      out[y * w + x] = sum ~/ win;
      sum += tmp[math.min(h - 1, y + r + 1) * w + x] -
          tmp[math.max(0, y - r) * w + x];
    }
  }
  return out;
}

/// Keeps only the largest connected foreground region (removes stray blobs in
/// the background that segmentation sometimes leaves behind).
void _removeSmallSpecks(Uint8List a, int w, int h) {
  final label = Int32List(w * h);
  final sizes = <int>[0];
  var best = 0, bestSize = 0, next = 0;
  final stack = <int>[];
  for (var i = 0; i < w * h; i++) {
    if (a[i] < 128 || label[i] != 0) continue;
    next++;
    var size = 0;
    stack.add(i);
    label[i] = next;
    while (stack.isNotEmpty) {
      final p = stack.removeLast();
      size++;
      final x = p % w, y = p ~/ w;
      void visit(int q) {
        if (label[q] == 0 && a[q] >= 128) {
          label[q] = next;
          stack.add(q);
        }
      }

      if (x > 0) visit(p - 1);
      if (x < w - 1) visit(p + 1);
      if (y > 0) visit(p - w);
      if (y < h - 1) visit(p + w);
    }
    sizes.add(size);
    if (size > bestSize) {
      bestSize = size;
      best = next;
    }
  }
  if (best == 0) return;
  // Remove only blobs that are small compared with the main subject.
  final minKeep = bestSize * 0.15;
  for (var i = 0; i < w * h; i++) {
    final l = label[i];
    if (l != 0 && l != best && sizes[l] < minKeep) {
      a[i] = 0;
    }
  }
}

class RenderJob {
  final String normPath;
  final String? maskPath; // null = keep original background
  final int width, height;
  final double cropCx, cropCy, cropH;
  final int outW, outH;
  final int bgArgb;
  final double brightness, contrast;
  RenderJob({
    required this.normPath,
    required this.maskPath,
    required this.width,
    required this.height,
    required this.cropCx,
    required this.cropCy,
    required this.cropH,
    required this.outW,
    required this.outH,
    required this.bgArgb,
    required this.brightness,
    required this.contrast,
  });
}

/// Renders one finished photo: crop + background colour + adjustments.
img.Image renderPhoto(RenderJob j) {
  final src = img.decodeJpg(File(j.normPath).readAsBytesSync())!;
  final rgb = src.getBytes(order: img.ChannelOrder.rgb);
  final w = src.width, h = src.height;
  Uint8List? mask;
  if (j.maskPath != null && File(j.maskPath!).existsSync()) {
    mask = File(j.maskPath!).readAsBytesSync();
    if (mask.length != w * h) mask = null;
  }

  final bgR = (j.bgArgb >> 16) & 0xff;
  final bgG = (j.bgArgb >> 8) & 0xff;
  final bgB = j.bgArgb & 0xff;

  final scale = j.cropH / j.outH; // source px per output px
  final cropW = j.outW * scale;
  final left = j.cropCx - cropW / 2;
  final top = j.cropCy - j.cropH / 2;
  final ss = scale > 1.2 ? math.min(3, scale.ceil()) : 1;
  final step = scale / ss;

  // Lookup table for brightness/contrast.
  final lut = Uint8List(256);
  for (var i = 0; i < 256; i++) {
    final v = (i - 128) * j.contrast + 128 + j.brightness * 255;
    lut[i] = v.round().clamp(0, 255);
  }

  final out = img.Image(width: j.outW, height: j.outH);
  for (var oy = 0; oy < j.outH; oy++) {
    for (var ox = 0; ox < j.outW; ox++) {
      double r = 0, g = 0, b = 0, aSum = 0;
      for (var sy = 0; sy < ss; sy++) {
        final fy = top + oy * scale + (sy + 0.5) * step - 0.5;
        for (var sx = 0; sx < ss; sx++) {
          final fx = left + ox * scale + (sx + 0.5) * step - 0.5;
          if (fx < -0.5 || fy < -0.5 || fx > w - 0.5 || fy > h - 0.5) {
            continue; // outside the source → background
          }
          final x0 = fx.floor().clamp(0, w - 1);
          final y0 = fy.floor().clamp(0, h - 1);
          final x1 = math.min(x0 + 1, w - 1);
          final y1 = math.min(y0 + 1, h - 1);
          final ax = (fx - x0).clamp(0.0, 1.0);
          final ay = (fy - y0).clamp(0.0, 1.0);
          final w00 = (1 - ax) * (1 - ay), w10 = ax * (1 - ay);
          final w01 = (1 - ax) * ay, w11 = ax * ay;
          final i00 = y0 * w + x0, i10 = y0 * w + x1;
          final i01 = y1 * w + x0, i11 = y1 * w + x1;
          final a = mask == null
              ? 1.0
              : (mask[i00] * w00 +
                      mask[i10] * w10 +
                      mask[i01] * w01 +
                      mask[i11] * w11) /
                  255.0;
          if (a <= 0) continue;
          double ch(int o) =>
              rgb[i00 * 3 + o] * w00 +
              rgb[i10 * 3 + o] * w10 +
              rgb[i01 * 3 + o] * w01 +
              rgb[i11 * 3 + o] * w11;
          r += lut[ch(0).round().clamp(0, 255)] * a;
          g += lut[ch(1).round().clamp(0, 255)] * a;
          b += lut[ch(2).round().clamp(0, 255)] * a;
          aSum += a;
        }
      }
      final n = (ss * ss).toDouble();
      final bgW = n - aSum;
      out.setPixelRgb(
        ox,
        oy,
        ((r + bgR * bgW) / n).round().clamp(0, 255),
        ((g + bgG * bgW) / n).round().clamp(0, 255),
        ((b + bgB * bgW) / n).round().clamp(0, 255),
      );
    }
  }
  return out;
}

int mmToPx(double mm, int dpi) => (mm / 25.4 * dpi).round();

// ---------------------------------------------------------------------------
// Isolate entry helpers. These are top-level on purpose: a closure passed to
// Isolate.run() carries its whole enclosing scope, so creating it inside an
// instance method or next to UI callbacks drags unsendable objects along
// ("Invalid argument(s): Illegal argument in isolate message"). Here the
// closures can only see their own parameters.

Future<NormResult> normalizeInIsolate(String src, String dst, String segDst) =>
    Isolate.run(() => normalizeImage(src, dst, segDst));

Future<MaskResult> buildMaskInIsolate(MaskJob job) =>
    Isolate.run(() => buildMask(job));
