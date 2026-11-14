import 'package:flutter_test/flutter_test.dart';
import 'package:photo_studio/models/sheet_config.dart';

void main() {
  test('35x45 on 4x6 auto picks landscape with 8 photos', () {
    final c = SheetConfig(paperId: '4x6');
    final fit = LayoutEngine.fit(c, 35, 45);
    expect(fit.perPage, 8);
    expect(fit.pageW, greaterThan(fit.pageH)); // landscape
  });

  test('40x50 on A4 fits 28', () {
    final c = SheetConfig(paperId: 'a4');
    final fit = LayoutEngine.fit(c, 40, 50);
    expect(fit.perPage, 28); // landscape 7×4
  });

  test('fill-page mode makes one page per photo', () {
    final c = SheetConfig(paperId: '4x6', copiesPerPhoto: 0);
    final pages = LayoutEngine.build(c, 3, 35, 45);
    expect(pages.length, 3);
    expect(pages.every((p) => p.slots.length == 8), isTrue);
    expect(pages[1].slots.every((s) => s.photoIndex == 1), isTrue);
  });

  test('fixed copies pack photos across pages', () {
    final c = SheetConfig(paperId: '4x6', copiesPerPhoto: 4);
    final pages = LayoutEngine.build(c, 5, 35, 45); // 20 photos, 8 per page
    expect(pages.length, 3);
    expect(pages.last.slots.length, 4);
  });

  test('slots stay inside the page and do not overlap', () {
    for (final paper in PaperSize.presets) {
      final c = SheetConfig(paperId: paper.id, copiesPerPhoto: 0);
      final pages = LayoutEngine.build(c, 1, 35, 45);
      final p = pages.first;
      for (final s in p.slots) {
        expect(s.x, greaterThanOrEqualTo(c.marginMm - 1e-6));
        expect(s.y, greaterThanOrEqualTo(c.marginMm - 1e-6));
        expect(s.x + s.w, lessThanOrEqualTo(p.widthMm - c.marginMm + 1e-6));
        expect(s.y + s.h, lessThanOrEqualTo(p.heightMm - c.marginMm + 1e-6));
      }
      for (var i = 0; i < p.slots.length; i++) {
        for (var j = i + 1; j < p.slots.length; j++) {
          final a = p.slots[i], b = p.slots[j];
          final overlap = a.x < b.x + b.w - 1e-6 &&
              b.x < a.x + a.w - 1e-6 &&
              a.y < b.y + b.h - 1e-6 &&
              b.y < a.y + a.h - 1e-6;
          expect(overlap, isFalse);
        }
      }
    }
  });

  test('too-large photo yields no pages', () {
    final c = SheetConfig(paperId: '4x6');
    expect(LayoutEngine.build(c, 1, 200, 200), isEmpty);
  });
}
