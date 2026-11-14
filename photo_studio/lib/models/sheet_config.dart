import 'dart:math' as math;

/// Print paper sizes, in millimetres (portrait).
class PaperSize {
  final String id;
  final String name;
  final double widthMm;
  final double heightMm;
  const PaperSize(this.id, this.name, this.widthMm, this.heightMm);

  static const presets = <PaperSize>[
    PaperSize('4x6', '4×6 inch (ফটো পেপার)', 101.6, 152.4),
    PaperSize('a4', 'A4', 210, 297),
    PaperSize('5x7', '5×7 inch', 127, 177.8),
    PaperSize('a5', 'A5', 148, 210),
    PaperSize('letter', 'Letter', 215.9, 279.4),
  ];

  static PaperSize byId(String id) =>
      presets.firstWhere((p) => p.id == id, orElse: () => presets.first);
}

enum SheetOrientation { auto, portrait, landscape }

class SheetConfig {
  String paperId;
  SheetOrientation orientation;
  double marginMm;
  double gapMm;
  bool cutLines;

  /// 0 = fill each page with copies of one photo; >0 = exact copies per photo.
  int copiesPerPhoto;

  SheetConfig({
    this.paperId = '4x6',
    this.orientation = SheetOrientation.auto,
    this.marginMm = 2,
    this.gapMm = 1.5,
    this.cutLines = true,
    this.copiesPerPhoto = 0,
  });

  PaperSize get paper => PaperSize.byId(paperId);
}

/// One photo slot on a page, in millimetres from the top-left corner.
class Slot {
  final double x, y, w, h;
  final int photoIndex;
  const Slot(this.x, this.y, this.w, this.h, this.photoIndex);
}

class PageLayout {
  final double widthMm;
  final double heightMm;
  final List<Slot> slots;
  const PageLayout(this.widthMm, this.heightMm, this.slots);
}

class GridFit {
  final double pageW, pageH;
  final int cols, rows;
  const GridFit(this.pageW, this.pageH, this.cols, this.rows);
  int get perPage => cols * rows;
}

class LayoutEngine {
  static GridFit fit(SheetConfig c, double photoW, double photoH) {
    final p = c.paper;
    GridFit calc(double pw, double ph) {
      int n(double avail, double size) =>
          math.max(0, ((avail + c.gapMm) / (size + c.gapMm)).floor());
      return GridFit(pw, ph, n(pw - 2 * c.marginMm, photoW),
          n(ph - 2 * c.marginMm, photoH));
    }

    final portrait = calc(p.widthMm, p.heightMm);
    final landscape = calc(p.heightMm, p.widthMm);
    switch (c.orientation) {
      case SheetOrientation.portrait:
        return portrait;
      case SheetOrientation.landscape:
        return landscape;
      case SheetOrientation.auto:
        return landscape.perPage > portrait.perPage ? landscape : portrait;
    }
  }

  /// Builds pages for [photoCount] photos of [photoW]×[photoH] mm.
  static List<PageLayout> build(
      SheetConfig c, int photoCount, double photoW, double photoH) {
    final g = fit(c, photoW, photoH);
    if (g.perPage == 0 || photoCount == 0) return const [];

    // Sequence of photo indices to place.
    final seq = <int>[];
    if (c.copiesPerPhoto <= 0) {
      for (var i = 0; i < photoCount; i++) {
        for (var k = 0; k < g.perPage; k++) {
          seq.add(i);
        }
      }
    } else {
      for (var i = 0; i < photoCount; i++) {
        for (var k = 0; k < c.copiesPerPhoto; k++) {
          seq.add(i);
        }
      }
    }

    final gridW = g.cols * photoW + (g.cols - 1) * c.gapMm;
    final gridH = g.rows * photoH + (g.rows - 1) * c.gapMm;
    final ox = (g.pageW - gridW) / 2;
    final oy = (g.pageH - gridH) / 2;

    final pages = <PageLayout>[];
    for (var start = 0; start < seq.length; start += g.perPage) {
      final slots = <Slot>[];
      for (var k = 0; k < g.perPage && start + k < seq.length; k++) {
        final r = k ~/ g.cols, col = k % g.cols;
        slots.add(Slot(ox + col * (photoW + c.gapMm),
            oy + r * (photoH + c.gapMm), photoW, photoH, seq[start + k]));
      }
      pages.add(PageLayout(g.pageW, g.pageH, slots));
    }
    return pages;
  }
}
