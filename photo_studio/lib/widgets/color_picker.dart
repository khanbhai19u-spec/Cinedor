import 'package:material_ui/material_ui.dart';

import '../state/studio_state.dart';

/// Bottom sheet with preset colours, an HSV picker and a HEX field.
Future<Color?> pickColor(BuildContext context, Color initial,
    {bool allowReset = false}) {
  return showModalBottomSheet<Color>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => _ColorSheet(initial: initial, allowReset: allowReset),
  );
}

/// Returned by [pickColor] when the user chooses "use batch colour".
const kResetColor = Color(0x00000001);

class _ColorSheet extends StatefulWidget {
  final Color initial;
  final bool allowReset;
  const _ColorSheet({required this.initial, required this.allowReset});

  @override
  State<_ColorSheet> createState() => _ColorSheetState();
}

class _ColorSheetState extends State<_ColorSheet> {
  late HSVColor hsv;
  final hex = TextEditingController();

  @override
  void initState() {
    super.initState();
    hsv = HSVColor.fromColor(widget.initial);
    hex.text = _hex(widget.initial);
  }

  @override
  void dispose() {
    hex.dispose();
    super.dispose();
  }

  String _hex(Color c) =>
      (c.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0').toUpperCase();

  void _set(HSVColor v) {
    setState(() {
      hsv = v;
      hex.text = _hex(v.toColor());
    });
  }

  Widget _track(List<Color> colors, double value, ValueChanged<double> onChanged) {
    return SizedBox(
      height: 36,
      child: LayoutBuilder(builder: (context, c) {
        void at(Offset o) => onChanged((o.dx / c.maxWidth).clamp(0.0, 1.0));
        return GestureDetector(
          onPanDown: (d) => at(d.localPosition),
          onPanUpdate: (d) => at(d.localPosition),
          child: Stack(alignment: Alignment.centerLeft, children: [
            Container(
              height: 22,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(11),
                gradient: LinearGradient(colors: colors),
                border: Border.all(color: Colors.black12),
              ),
            ),
            Positioned(
              left: (value * c.maxWidth - 14).clamp(0.0, c.maxWidth - 28),
              child: Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: hsv.toColor(),
                  border: Border.all(color: Colors.white, width: 3),
                  boxShadow: const [BoxShadow(blurRadius: 3, color: Colors.black38)],
                ),
              ),
            ),
          ]),
        );
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final color = hsv.toColor();
    return Padding(
      padding: EdgeInsets.fromLTRB(
          20, 0, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('ব্যাকগ্রাউন্ড কালার', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Wrap(spacing: 10, runSpacing: 10, children: [
            for (final c in kBgPresets)
              GestureDetector(
                onTap: () => _set(HSVColor.fromColor(c)),
                child: Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: c,
                    shape: BoxShape.circle,
                    border: Border.all(
                        color: c.toARGB32() == color.toARGB32()
                            ? Theme.of(context).colorScheme.primary
                            : Colors.black26,
                        width: c.toARGB32() == color.toARGB32() ? 3 : 1),
                  ),
                ),
              ),
          ]),
          const SizedBox(height: 18),
          const Text('Hue'),
          _track(
            [for (var h = 0; h <= 360; h += 60) HSVColor.fromAHSV(1, h.toDouble(), 1, 1).toColor()],
            hsv.hue / 360,
            (v) => _set(hsv.withHue(v * 360)),
          ),
          const Text('Saturation'),
          _track(
            [hsv.withSaturation(0).toColor(), hsv.withSaturation(1).toColor()],
            hsv.saturation,
            (v) => _set(hsv.withSaturation(v)),
          ),
          const Text('Brightness'),
          _track(
            [Colors.black, hsv.withValue(1).toColor()],
            hsv.value,
            (v) => _set(hsv.withValue(v)),
          ),
          const SizedBox(height: 8),
          Row(children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.black26),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: TextField(
                controller: hex,
                decoration: const InputDecoration(
                    prefixText: '#', labelText: 'HEX', border: OutlineInputBorder()),
                maxLength: 6,
                onChanged: (t) {
                  if (t.length == 6) {
                    final v = int.tryParse(t, radix: 16);
                    if (v != null) {
                      setState(() => hsv = HSVColor.fromColor(Color(0xFF000000 | v)));
                    }
                  }
                },
              ),
            ),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            if (widget.allowReset)
              TextButton(
                onPressed: () => Navigator.pop(context, kResetColor),
                child: const Text('ব্যাচ কালার ব্যবহার'),
              ),
            const Spacer(),
            TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('বাতিল')),
            const SizedBox(width: 8),
            FilledButton(
                onPressed: () => Navigator.pop(context, color),
                child: const Text('ঠিক আছে')),
          ]),
        ],
      ),
    );
  }
}
