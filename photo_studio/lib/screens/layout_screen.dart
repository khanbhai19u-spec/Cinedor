import 'dart:typed_data';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../models/photo_item.dart';
import '../models/sheet_config.dart';
import '../services/output_service.dart';
import '../state/studio_state.dart';
import '../widgets/passport_preview.dart';
import '../widgets/ui_helpers.dart';
import 'print_screen.dart';

/// Everything needed to output the current layout.
class PreparedOutput {
  final List<PageLayout> pages;
  final List<RenderedPhoto> photos;
  final List<PhotoItem> items;
  final String signature;
  Uint8List? _pdf;
  PreparedOutput(this.pages, this.photos, this.items, this.signature);

  Future<Uint8List> pdf(bool cutLines) async =>
      _pdf ??= await OutputService.buildPdf(pages, photos, cutLines);
}

class LayoutScreen extends StatefulWidget {
  const LayoutScreen({super.key});

  @override
  State<LayoutScreen> createState() => _LayoutScreenState();
}

class _LayoutScreenState extends State<LayoutScreen> {
  PreparedOutput? _cache;
  int _page = 0;

  String _signature(StudioState st, List<PhotoItem> items) {
    final b = StringBuffer()
      ..write('${st.sizeId}|${st.customW}|${st.customH}|${st.dpi}|${st.bgColor.toARGB32()}|')
      ..write('${st.sheet.paperId}|${st.sheet.orientation}|${st.sheet.marginMm}|')
      ..write('${st.sheet.gapMm}|${st.sheet.cutLines}|${st.sheet.copiesPerPhoto}|');
    for (final p in items) {
      b.write('${p.id},${p.cropCx.toStringAsFixed(1)},${p.cropCy.toStringAsFixed(1)},'
          '${p.cropH.toStringAsFixed(1)},${p.removeBackground},${p.bgOverride?.toARGB32()},'
          '${p.brightness},${p.contrast};');
    }
    return b.toString();
  }

  Future<PreparedOutput> _prepare(void Function(String) up) async {
    final st = context.read<StudioState>();
    final items = st.ready;
    final sig = _signature(st, items);
    if (_cache != null && _cache!.signature == sig) return _cache!;
    final size = st.size;
    final pages = LayoutEngine.build(st.sheet, items.length, size.widthMm, size.heightMm);
    if (pages.isEmpty) throw Exception('এই পেপারে ছবি ধরছে না — মার্জিন কমান বা বড় পেপার নিন');
    final rendered = await OutputService.renderAll(items, size, st.dpi, st.bgColor,
        onProgress: (d, t) => up('HD রেন্ডার $d/$t'));
    return _cache = PreparedOutput(pages, rendered, items, sig);
  }

  Future<void> _savePdf({required bool share}) async {
    final st = context.read<StudioState>();
    final r = await withProgress<bool>(context, 'PDF তৈরি হচ্ছে…', (up) async {
      final prep = await _prepare(up);
      up('PDF তৈরি হচ্ছে…');
      final pdf = await prep.pdf(st.sheet.cutLines);
      if (share) {
        final f = await OutputService.writeTemp(pdf, 'photo_sheet', 'pdf');
        await OutputService.share([f]);
        return true;
      }
      return OutputService.saveFileAs(pdf, 'photo_sheet', 'pdf', 'application/pdf');
    });
    if (r == true && !share && mounted) toast(context, 'PDF সেভ হয়েছে');
  }

  Future<void> _saveSheets(ImageFormat f, {bool share = false}) async {
    final st = context.read<StudioState>();
    final n = await withProgress<int>(context, 'শিট তৈরি হচ্ছে…', (up) async {
      final prep = await _prepare(up);
      up('${prep.pages.length}টি শিট তৈরি হচ্ছে…');
      final imgs = await OutputService.buildSheetImages(
          prep.pages, prep.photos, st.dpi, st.sheet.cutLines, f,
          jpgQuality: st.jpgQuality);
      if (share) {
        final ext = f == ImageFormat.png ? 'png' : 'jpg';
        final files = [
          for (final b in imgs) await OutputService.writeTemp(b, 'photo_sheet', ext)
        ];
        await OutputService.share(files);
        return files.length;
      }
      return OutputService.saveToGallery(imgs, 'photo_sheet');
    });
    if (n != null && !share && mounted) {
      toast(context, '$nটি শিট গ্যালারির "Photo Studio" অ্যালবামে সেভ হয়েছে');
    }
  }

  Future<void> _print() async {
    final prep = await withProgress<PreparedOutput>(context, 'প্রস্তুত হচ্ছে…', _prepare);
    if (prep == null || !mounted) return;
    Navigator.push(context, MaterialPageRoute(builder: (_) => PrintScreen(output: prep)));
  }

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    final sh = st.sheet;
    final size = st.size;
    final items = st.ready;
    final fit = LayoutEngine.fit(sh, size.widthMm, size.heightMm);
    final pages = LayoutEngine.build(sh, items.length, size.widthMm, size.heightMm);
    if (_page >= pages.length) _page = 0;
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('প্রিন্ট লেআউট')),
      body: ListView(children: [
        // Page preview.
        Container(
          color: cs.surfaceContainerHighest,
          height: 340,
          padding: const EdgeInsets.all(16),
          child: pages.isEmpty
              ? const Center(child: Text('এই পেপারে ছবি ধরছে না'))
              : Column(children: [
                  Expanded(
                    child: PageView.builder(
                      itemCount: pages.length,
                      onPageChanged: (i) => setState(() => _page = i),
                      itemBuilder: (_, i) => Center(
                        child: _PagePreview(
                            page: pages[i], items: items, cutLines: sh.cutLines),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text('পেজ ${_page + 1}/${pages.length} • প্রতি পেজে ${fit.perPage}টি '
                      '(${fit.cols}×${fit.rows}) • মোট ${pages.fold<int>(0, (a, p) => a + p.slots.length)}টি ছবি'),
                ]),
        ),
        const SectionTitle('পেপার সাইজ'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Wrap(spacing: 8, runSpacing: 4, children: [
            for (final p in PaperSize.presets)
              ChoiceChip(
                label: Text(p.name),
                selected: sh.paperId == p.id,
                onSelected: (_) => st.update(() => sh.paperId = p.id),
              ),
          ]),
        ),
        const SectionTitle('দিক (Orientation)'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: SegmentedButton<SheetOrientation>(
            segments: const [
              ButtonSegment(value: SheetOrientation.auto, label: Text('অটো')),
              ButtonSegment(value: SheetOrientation.portrait, label: Text('খাড়া')),
              ButtonSegment(value: SheetOrientation.landscape, label: Text('আড়াআড়ি')),
            ],
            selected: {sh.orientation},
            onSelectionChanged: (s) => st.update(() => sh.orientation = s.first),
          ),
        ),
        const SectionTitle('কপি সংখ্যা'),
        ListTile(
          title: Text(sh.copiesPerPhoto == 0
              ? 'প্রতিটি ছবি দিয়ে পুরো পেজ ভরাট (${fit.perPage}টি)'
              : 'প্রতিটি ছবির ${sh.copiesPerPhoto} কপি'),
          trailing: Row(mainAxisSize: MainAxisSize.min, children: [
            IconButton(
              icon: const Icon(Icons.remove_circle_outline),
              onPressed: sh.copiesPerPhoto > 0
                  ? () => st.update(() => sh.copiesPerPhoto--)
                  : null,
            ),
            Text(sh.copiesPerPhoto == 0 ? 'Full' : '${sh.copiesPerPhoto}',
                style: Theme.of(context).textTheme.titleMedium),
            IconButton(
              icon: const Icon(Icons.add_circle_outline),
              onPressed: sh.copiesPerPhoto < 48
                  ? () => st.update(() => sh.copiesPerPhoto++)
                  : null,
            ),
          ]),
        ),
        _MmSlider(
            label: 'মার্জিন',
            value: sh.marginMm,
            max: 15,
            onChanged: (v) => st.update(() => sh.marginMm = v)),
        _MmSlider(
            label: 'ছবির মাঝে ফাঁক',
            value: sh.gapMm,
            max: 10,
            onChanged: (v) => st.update(() => sh.gapMm = v)),
        SwitchListTile(
          title: const Text('কাটিং লাইন'),
          subtitle: const Text('ছবির চারপাশে হালকা ধূসর দাগ'),
          value: sh.cutLines,
          onChanged: (v) => st.update(() => sh.cutLines = v),
        ),
        ListTile(
          leading: const Icon(Icons.high_quality_outlined),
          title: Text('রেজোলিউশন: ${st.dpi} DPI'),
          subtitle: Text('${size.name} ${size.label}'),
          trailing: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 300, label: Text('300')),
              ButtonSegment(value: 600, label: Text('600')),
            ],
            selected: {st.dpi},
            onSelectionChanged: (s) => st.update(() => st.dpi = s.first),
          ),
        ),
        const SizedBox(height: 120),
      ]),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
          child: Row(children: [
            Expanded(
              child: FilledButton.icon(
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                icon: const Icon(Icons.print),
                label: const Text('প্রিন্ট'),
                onPressed: pages.isEmpty ? null : _print,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton.tonalIcon(
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                icon: const Icon(Icons.download),
                label: const Text('এক্সপোর্ট'),
                onPressed: pages.isEmpty ? null : () => _exportSheet(context),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  void _exportSheet(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.picture_as_pdf),
            title: const Text('PDF সেভ করুন'),
            subtitle: const Text('Downloads বা যেকোনো ফোল্ডারে'),
            onTap: () {
              Navigator.pop(ctx);
              _savePdf(share: false);
            },
          ),
          ListTile(
            leading: const Icon(Icons.share),
            title: const Text('PDF শেয়ার'),
            subtitle: const Text('WhatsApp, Email, Drive…'),
            onTap: () {
              Navigator.pop(ctx);
              _savePdf(share: true);
            },
          ),
          ListTile(
            leading: const Icon(Icons.image),
            title: const Text('শিট HD JPG — গ্যালারিতে'),
            onTap: () {
              Navigator.pop(ctx);
              _saveSheets(ImageFormat.jpg);
            },
          ),
          ListTile(
            leading: const Icon(Icons.image_outlined),
            title: const Text('শিট PNG — গ্যালারিতে'),
            onTap: () {
              Navigator.pop(ctx);
              _saveSheets(ImageFormat.png);
            },
          ),
          ListTile(
            leading: const Icon(Icons.ios_share),
            title: const Text('শিট JPG শেয়ার'),
            onTap: () {
              Navigator.pop(ctx);
              _saveSheets(ImageFormat.jpg, share: true);
            },
          ),
        ]),
      ),
    );
  }
}

class _MmSlider extends StatelessWidget {
  final String label;
  final double value, max;
  final ValueChanged<double> onChanged;
  const _MmSlider(
      {required this.label, required this.value, required this.max, required this.onChanged});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(children: [
          SizedBox(width: 110, child: Text(label)),
          Expanded(
            child: Slider(
              value: value.clamp(0, max),
              max: max,
              divisions: (max * 2).round(),
              label: '${value.toStringAsFixed(1)} mm',
              onChanged: onChanged,
            ),
          ),
          SizedBox(width: 56, child: Text('${value.toStringAsFixed(1)} mm')),
        ]),
      );
}

class _PagePreview extends StatelessWidget {
  final PageLayout page;
  final List<PhotoItem> items;
  final bool cutLines;
  const _PagePreview({required this.page, required this.items, required this.cutLines});

  @override
  Widget build(BuildContext context) {
    final st = context.read<StudioState>();
    return AspectRatio(
      aspectRatio: page.widthMm / page.heightMm,
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          boxShadow: [BoxShadow(blurRadius: 8, color: Colors.black26)],
        ),
        child: LayoutBuilder(builder: (context, c) {
          final k = c.maxWidth / page.widthMm;
          return Stack(children: [
            for (final s in page.slots)
              Positioned(
                left: s.x * k,
                top: s.y * k,
                width: s.w * k,
                height: s.h * k,
                child: PassportPreview(
                  photo: items[s.photoIndex],
                  size: st.size,
                  bg: st.bgColor,
                  showBorder: cutLines,
                ),
              ),
          ]);
        }),
      ),
    );
  }
}
