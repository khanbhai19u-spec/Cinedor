import 'package:material_ui/material_ui.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';

/// Live, GPU-drawn preview of a finished photo (crop + background colour +
/// brightness/contrast). Matches what the exporter renders at full resolution.
class PassportPreview extends StatelessWidget {
  final PhotoItem photo;
  final PhotoSize size;
  final Color bg;
  final bool showBorder;

  const PassportPreview({
    super.key,
    required this.photo,
    required this.size,
    required this.bg,
    this.showBorder = true,
  });

  static List<double> colorMatrix(double brightness, double contrast) {
    final o = 128 * (1 - contrast) + brightness * 255;
    return [
      contrast, 0, 0, 0, o, //
      0, contrast, 0, 0, o, //
      0, 0, contrast, 0, o, //
      0, 0, 0, 1, 0,
    ];
  }

  @override
  Widget build(BuildContext context) {
    final p = photo;
    final bytes = p.removeBackground ? p.previewPng : p.previewOrig;
    return AspectRatio(
      aspectRatio: size.aspect,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: p.bgOverride ?? bg,
          border: showBorder
              ? Border.all(color: Colors.black26, width: 0.5)
              : null,
        ),
        child: ClipRect(
          child: bytes == null || p.cropH <= 0
              ? const SizedBox.expand()
              : LayoutBuilder(builder: (context, c) {
                  final s = c.maxHeight / p.cropH;
                  return Stack(clipBehavior: Clip.hardEdge, children: [
                    Positioned(
                      left: c.maxWidth / 2 - p.cropCx * s,
                      top: c.maxHeight / 2 - p.cropCy * s,
                      width: p.width * s,
                      height: p.height * s,
                      child: ColorFiltered(
                        colorFilter: ColorFilter.matrix(
                            colorMatrix(p.brightness, p.contrast)),
                        child: Image.memory(bytes,
                            fit: BoxFit.fill,
                            gaplessPlayback: true,
                            filterQuality: FilterQuality.medium),
                      ),
                    ),
                  ]);
                }),
        ),
      ),
    );
  }
}
