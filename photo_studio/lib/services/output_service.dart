import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';
import 'dart:ui' show Color;

import 'package:file_picker/file_picker.dart';
import 'package:gal/gal.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:share_plus/share_plus.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';
import '../models/sheet_config.dart';
import 'image_ops.dart';

enum ImageFormat { jpg, png }

class RenderedPhoto {
  final img.Image image;
  final Uint8List jpg; // high-quality JPG, used for PDF embedding
  RenderedPhoto(this.image, this.jpg);
}

class SheetJob {
  final PageLayout page;
  final List<img.Image> photos;
  final int dpi;
  final bool cutLines;
  final ImageFormat format;
  final int jpgQuality;
  SheetJob(this.page, this.photos, this.dpi, this.cutLines, this.format,
      this.jpgQuality);
}

class PdfJob {
  final List<PageLayout> pages;
  final List<Uint8List> jpgs;
  final bool cutLines;
  PdfJob(this.pages, this.jpgs, this.cutLines);
}

Uint8List _renderSheet(SheetJob j) {
  final p = j.page;
  final sheet = img.Image(
      width: mmToPx(p.widthMm, j.dpi), height: mmToPx(p.heightMm, j.dpi));
  img.fill(sheet, color: img.ColorRgb8(255, 255, 255));
  final line = img.ColorRgb8(170, 170, 170);
  for (final s in p.slots) {
    final x = mmToPx(s.x, j.dpi), y = mmToPx(s.y, j.dpi);
    final w = mmToPx(s.w, j.dpi), h = mmToPx(s.h, j.dpi);
    var ph = j.photos[s.photoIndex];
    if (ph.width != w || ph.height != h) {
      ph = img.copyResize(ph,
          width: w, height: h, interpolation: img.Interpolation.cubic);
    }
    img.compositeImage(sheet, ph, dstX: x, dstY: y);
    if (j.cutLines) {
      img.drawRect(sheet,
          x1: x - 1, y1: y - 1, x2: x + w, y2: y + h, color: line);
    }
  }
  return j.format == ImageFormat.png
      ? img.encodePng(sheet, level: 6)
      : img.encodeJpg(sheet, quality: j.jpgQuality);
}

Future<Uint8List> _buildPdf(PdfJob j) async {
  final doc = pw.Document(title: 'Photo Studio', creator: 'Photo Studio');
  final images = j.jpgs.map((b) => pw.MemoryImage(b)).toList();
  const mm = PdfPageFormat.mm;
  for (final page in j.pages) {
    doc.addPage(pw.Page(
      pageFormat:
          PdfPageFormat(page.widthMm * mm, page.heightMm * mm, marginAll: 0),
      build: (_) => pw.SizedBox(
        width: page.widthMm * mm,
        height: page.heightMm * mm,
        child: pw.Stack(children: [
          for (final s in page.slots)
            pw.Positioned(
              left: s.x * mm,
              top: s.y * mm,
              child: pw.SizedBox(
                width: s.w * mm,
                height: s.h * mm,
                child: pw.Image(images[s.photoIndex], fit: pw.BoxFit.fill),
              ),
            ),
          if (j.cutLines)
            for (final s in page.slots)
              pw.Positioned(
                left: s.x * mm,
                top: s.y * mm,
                child: pw.Container(
                  width: s.w * mm,
                  height: s.h * mm,
                  decoration: pw.BoxDecoration(
                      border: pw.Border.all(color: PdfColors.grey500, width: 0.3)),
                ),
              ),
        ]),
      ),
    ));
  }
  return doc.save();
}

class OutputService {
  static int _stamp = 0;

  static String _name(String prefix) {
    final t = DateTime.now();
    String two(int v) => v.toString().padLeft(2, '0');
    _stamp++;
    return '${prefix}_${t.year}${two(t.month)}${two(t.day)}_${two(t.hour)}${two(t.minute)}${two(t.second)}_$_stamp';
  }

  static RenderJob jobFor(PhotoItem p, PhotoSize size, int dpi, Color bg) {
    final outW = mmToPx(size.widthMm, dpi);
    final outH = mmToPx(size.heightMm, dpi);
    // Keep the crop aspect equal to the output aspect.
    return RenderJob(
      normPath: p.normPath!,
      maskPath: p.removeBackground ? p.maskPath : null,
      width: p.width,
      height: p.height,
      cropCx: p.cropCx,
      cropCy: p.cropCy,
      cropH: p.cropH,
      outW: outW,
      outH: outH,
      bgArgb: (p.bgOverride ?? bg).toARGB32(),
      brightness: p.brightness,
      contrast: p.contrast,
    );
  }

  /// Renders every ready photo (in parallel isolates, 3 at a time).
  static Future<List<RenderedPhoto>> renderAll(
    List<PhotoItem> photos,
    PhotoSize size,
    int dpi,
    Color bg, {
    void Function(int done, int total)? onProgress,
  }) async {
    final out = List<RenderedPhoto?>.filled(photos.length, null);
    var next = 0, done = 0;
    Future<void> worker() async {
      while (next < photos.length) {
        final i = next++;
        final job = jobFor(photos[i], size, dpi, bg);
        out[i] = await Isolate.run(() {
          final im = renderPhoto(job);
          return RenderedPhoto(im, img.encodeJpg(im, quality: 95));
        });
        done++;
        onProgress?.call(done, photos.length);
      }
    }

    await Future.wait(List.generate(3, (_) => worker()));
    return out.cast<RenderedPhoto>();
  }

  static Future<Uint8List> buildPdf(
      List<PageLayout> pages, List<RenderedPhoto> photos, bool cutLines) {
    final job = PdfJob(pages, photos.map((e) => e.jpg).toList(), cutLines);
    return Isolate.run(() => _buildPdf(job));
  }

  static Future<List<Uint8List>> buildSheetImages(
    List<PageLayout> pages,
    List<RenderedPhoto> photos,
    int dpi,
    bool cutLines,
    ImageFormat format, {
    int jpgQuality = 95,
  }) async {
    final imgs = photos.map((e) => e.image).toList();
    final result = <Uint8List>[];
    for (final p in pages) {
      final job = SheetJob(p, imgs, dpi, cutLines, format, jpgQuality);
      result.add(await Isolate.run(() => _renderSheet(job)));
    }
    return result;
  }

  static Future<Uint8List> encode(
      img.Image im, ImageFormat f, int jpgQuality) {
    return Isolate.run(() => f == ImageFormat.png
        ? img.encodePng(im, level: 6)
        : img.encodeJpg(im, quality: jpgQuality));
  }

  /// Saves images into the phone gallery (album "Photo Studio").
  static Future<int> saveToGallery(List<Uint8List> files, String prefix) async {
    if (!await Gal.hasPermission(toAlbum: true)) {
      final ok = await Gal.requestPermission(toAlbum: true);
      if (!ok) throw Exception('গ্যালারিতে সেভ করার অনুমতি দেওয়া হয়নি');
    }
    var n = 0;
    for (final b in files) {
      await Gal.putImageBytes(b, album: 'Photo Studio', name: _name(prefix));
      n++;
    }
    return n;
  }

  /// Lets the user pick where to save (Downloads, Drive…). Returns false if
  /// the user cancelled.
  static Future<bool> saveFileAs(
      Uint8List bytes, String baseName, String ext, String mime) async {
    final uri = await FilePicker.saveFile(
        fileName: '${_name(baseName)}.$ext', bytes: bytes, mimeType: mime);
    return uri != null;
  }

  static Future<File> writeTemp(Uint8List bytes, String baseName, String ext) async {
    final dir = await getTemporaryDirectory();
    final f = File('${dir.path}/${_name(baseName)}.$ext');
    await f.writeAsBytes(bytes);
    return f;
  }

  static Future<void> share(List<File> files, {String? text}) async {
    await SharePlus.instance.share(ShareParams(
        files: files.map((f) => XFile(f.path)).toList(), text: text));
  }
}
