import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:photo_studio/models/sheet_config.dart';
import 'package:photo_studio/services/image_ops.dart';
import 'package:photo_studio/services/output_service.dart';

void main() {
  test('builds a PDF page per sheet and full-size JPG/PNG sheets', () async {
    final photos = List.generate(2, (i) {
      final im = img.Image(width: mmToPx(35, 300), height: mmToPx(45, 300));
      img.fill(im, color: img.ColorRgb8(i * 200, 50, 80));
      return RenderedPhoto(im, img.encodeJpg(im));
    });
    final c = SheetConfig(paperId: '4x6', copiesPerPhoto: 0);
    final pages = LayoutEngine.build(c, 2, 35, 45);
    expect(pages.length, 2);

    final pdf = await OutputService.buildPdf(pages, photos, true);
    final text = String.fromCharCodes(pdf);
    expect(text.startsWith('%PDF'), isTrue);
    expect(RegExp(r'/Type\s*/Page[^s]').allMatches(text).length, 2);

    final jpgs = await OutputService.buildSheetImages(
        pages, photos, 300, true, ImageFormat.jpg);
    final sheet = img.decodeJpg(jpgs.first)!;
    // 4×6 inch landscape at 300 dpi.
    expect(sheet.width, 1800);
    expect(sheet.height, 1200);

    final pngs = await OutputService.buildSheetImages(
        pages, photos, 300, false, ImageFormat.png);
    final p = img.decodePng(pngs[1])!;
    final s = pages[1].slots.first;
    final px = p.getPixel(mmToPx(s.x, 300) + 20, mmToPx(s.y, 300) + 20);
    expect([px.r, px.g, px.b], [200, 50, 80]);
  });
}
