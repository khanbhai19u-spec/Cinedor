import 'dart:math' as math;
import 'dart:typed_data';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';
import '../services/ai_processor.dart';
import '../services/output_service.dart';
import '../state/studio_state.dart';
import '../widgets/color_picker.dart';
import '../widgets/passport_preview.dart';
import '../widgets/ui_helpers.dart';

class EditorScreen extends StatefulWidget {
  final int initialIndex;
  const EditorScreen({super.key, required this.initialIndex});

  @override
  State<EditorScreen> createState() => _EditorScreenState();
}

class _EditorScreenState extends State<EditorScreen> {
  late int index;
  bool guides = true;
  double _previewH = 300;

  @override
  void initState() {
    super.initState();
    // Map the grid index (all photos) to an index in the ready list.
    final st = context.read<StudioState>();
    final ready = st.ready;
    final i = widget.initialIndex < st.photos.length
        ? ready.indexOf(st.photos[widget.initialIndex])
        : -1;
    index = math.max(0, i);
  }

  // Gesture state.
  double _startH = 0, _startCx = 0, _startCy = 0;
  Offset _startFocal = Offset.zero;

  List<PhotoItem> _ready(StudioState st) => st.ready;

  void _go(int delta, List<PhotoItem> list) {
    setState(() => index = (index + delta).clamp(0, list.length - 1));
  }

  Future<void> _saveOne(PhotoItem p, ImageFormat f) async {
    final st = context.read<StudioState>();
    final ok = await withProgress<int>(context, 'HD রেন্ডার হচ্ছে…', (_) async {
      final r = await OutputService.renderAll([p], st.size, st.dpi, st.bgColor);
      final Uint8List b = await OutputService.encode(r.first.image, f, st.jpgQuality);
      return OutputService.saveToGallery([b], 'passport');
    });
    if (ok != null && mounted) toast(context, 'গ্যালারিতে সেভ হয়েছে');
  }

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    final list = _ready(st);
    if (list.isEmpty) {
      return Scaffold(appBar: AppBar(), body: const Center(child: Text('কোনো প্রস্তুত ছবি নেই')));
    }
    if (index >= list.length) index = list.length - 1;
    final p = list[index];
    final size = st.size;
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: Text('এডিট ${index + 1}/${list.length}'),
        actions: [
          IconButton(
            tooltip: 'গাইড লাইন',
            icon: Icon(guides ? Icons.grid_on : Icons.grid_off),
            onPressed: () => setState(() => guides = !guides),
          ),
          PopupMenuButton<String>(
            onSelected: (v) {
              if (v == 'jpg') _saveOne(p, ImageFormat.jpg);
              if (v == 'png') _saveOne(p, ImageFormat.png);
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'jpg', child: Text('এই ছবি HD JPG সেভ')),
              PopupMenuItem(value: 'png', child: Text('এই ছবি PNG সেভ')),
            ],
          ),
        ],
      ),
      body: Column(children: [
        Expanded(
          child: Container(
            color: cs.surfaceContainerHighest,
            padding: const EdgeInsets.all(24),
            child: Row(children: [
              IconButton(
                  onPressed: index > 0 ? () => _go(-1, list) : null,
                  icon: const Icon(Icons.chevron_left, size: 32)),
              Expanded(
                child: Center(
                  child: GestureDetector(
                    onScaleStart: (d) {
                      _startH = p.cropH;
                      _startCx = p.cropCx;
                      _startCy = p.cropCy;
                      _startFocal = d.focalPoint;
                    },
                    onScaleUpdate: (d) {
                      // Normalised-image pixels per screen pixel.
                      final k = _startH / _previewH;
                      final newH = (_startH / d.scale)
                          .clamp(p.height * 0.08, math.max(p.width, p.height) * 3.0);
                      final delta = d.focalPoint - _startFocal;
                      setState(() {
                        p.cropH = newH;
                        p.cropCx = _startCx - delta.dx * k;
                        p.cropCy = _startCy - delta.dy * k;
                      });
                    },
                    onScaleEnd: (_) => st.touch(),
                    child: LayoutBuilder(builder: (context, c) {
                      _previewH = math.max(
                          1, math.min(c.maxHeight, c.maxWidth / size.aspect));
                      return Stack(children: [
                        PassportPreview(photo: p, size: size, bg: st.bgColor),
                        if (guides)
                          Positioned.fill(
                            child: IgnorePointer(
                              child: CustomPaint(painter: _GuidePainter(size)),
                            ),
                          ),
                      ]);
                    }),
                  ),
                ),
              ),
              IconButton(
                  onPressed: index < list.length - 1 ? () => _go(1, list) : null,
                  icon: const Icon(Icons.chevron_right, size: 32)),
            ]),
          ),
        ),
        if (!p.faceFound)
          Container(
            width: double.infinity,
            color: cs.errorContainer,
            padding: const EdgeInsets.all(8),
            child: Text('মুখ শনাক্ত হয়নি — আঙুল দিয়ে টেনে / জুম করে ঠিক করুন',
                style: TextStyle(color: cs.onErrorContainer), textAlign: TextAlign.center),
          ),
        Flexible(
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.symmetric(vertical: 4),
            children: [
              SwitchListTile(
                title: const Text('AI ব্যাকগ্রাউন্ড রিমুভ'),
                subtitle: Text(p.maskPath == null
                    ? 'এই ছবিতে মাস্ক তৈরি হয়নি'
                    : 'বন্ধ করলে আসল ব্যাকগ্রাউন্ড থাকবে'),
                value: p.removeBackground && p.maskPath != null,
                onChanged: p.maskPath == null
                    ? null
                    : (v) {
                        p.removeBackground = v;
                        st.touch();
                      },
              ),
              ListTile(
                leading: CircleAvatar(backgroundColor: p.bgOverride ?? st.bgColor),
                title: const Text('এই ছবির ব্যাকগ্রাউন্ড কালার'),
                subtitle: Text(p.bgOverride == null ? 'ব্যাচের কালার' : 'আলাদা কালার'),
                trailing: const Icon(Icons.palette_outlined),
                onTap: () async {
                  final c = await pickColor(context, p.bgOverride ?? st.bgColor,
                      allowReset: true);
                  if (c == null) return;
                  p.bgOverride = c == kResetColor ? null : c;
                  st.touch();
                },
              ),
              _slider('জুম', Icons.zoom_in,
                  value: (p.height / p.cropH).clamp(0.3, 8.0),
                  min: 0.3,
                  max: 8.0,
                  onChanged: (v) => setState(() => p.cropH = p.height / v)),
              _slider('উজ্জ্বলতা', Icons.brightness_6,
                  value: p.brightness,
                  min: -0.4,
                  max: 0.4,
                  onChanged: (v) => setState(() => p.brightness = v)),
              _slider('কনট্রাস্ট', Icons.contrast,
                  value: p.contrast,
                  min: 0.6,
                  max: 1.5,
                  onChanged: (v) => setState(() => p.contrast = v)),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: Wrap(spacing: 8, runSpacing: 8, children: [
                  OutlinedButton.icon(
                    icon: const Icon(Icons.center_focus_strong),
                    label: const Text('অটো ফ্রেম'),
                    onPressed: () {
                      AiProcessor.autoCrop(p, size);
                      st.touch();
                    },
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.restart_alt),
                    label: const Text('রিসেট রং'),
                    onPressed: () {
                      p.brightness = 0;
                      p.contrast = 1;
                      st.touch();
                    },
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.auto_fix_high),
                    label: const Text('AI আবার চালান'),
                    onPressed: () => st.reprocess(p),
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.copy_all),
                    label: const Text('রং সবার জন্য'),
                    onPressed: () {
                      for (final o in list) {
                        o.brightness = p.brightness;
                        o.contrast = p.contrast;
                      }
                      st.touch();
                      toast(context, 'উজ্জ্বলতা/কনট্রাস্ট সব ছবিতে প্রয়োগ হয়েছে');
                    },
                  ),
                ]),
              ),
            ],
          ),
        ),
      ]),
    );
  }

  Widget _slider(String label, IconData icon,
      {required double value,
      required double min,
      required double max,
      required ValueChanged<double> onChanged}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(children: [
        Icon(icon, size: 20),
        const SizedBox(width: 8),
        SizedBox(width: 72, child: Text(label)),
        Expanded(
          child: Slider(
            value: value.clamp(min, max),
            min: min,
            max: max,
            onChanged: onChanged,
            onChangeEnd: (_) => context.read<StudioState>().touch(),
          ),
        ),
      ]),
    );
  }
}

/// Draws the head-position guides for the chosen photo size.
class _GuidePainter extends CustomPainter {
  final PhotoSize size;
  _GuidePainter(this.size);

  @override
  void paint(Canvas canvas, Size s) {
    final paint = Paint()
      ..color = const Color(0xCCFF3B30)
      ..strokeWidth = 1;
    void dashed(Offset a, Offset b) {
      const dash = 6.0;
      final len = (b - a).distance;
      final dir = (b - a) / len;
      for (var d = 0.0; d < len; d += dash * 2) {
        canvas.drawLine(a + dir * d, a + dir * math.min(d + dash, len), paint);
      }
    }

    final top = s.height * size.topRatio;
    final chin = s.height * (size.topRatio + size.headRatio);
    dashed(Offset(0, top), Offset(s.width, top));
    dashed(Offset(0, chin), Offset(s.width, chin));
    dashed(Offset(s.width / 2, 0), Offset(s.width / 2, s.height));
    final oval = Rect.fromLTRB(s.width * 0.5 - (chin - top) * 0.36, top,
        s.width * 0.5 + (chin - top) * 0.36, chin);
    canvas.drawOval(
        oval,
        Paint()
          ..color = const Color(0x88FFFFFF)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.2);
  }

  @override
  bool shouldRepaint(_GuidePainter old) => old.size.id != size.id;
}
