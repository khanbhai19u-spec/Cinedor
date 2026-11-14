import 'dart:io';
import 'dart:typed_data';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';
import '../services/output_service.dart';
import '../state/studio_state.dart';
import '../widgets/color_picker.dart';
import '../widgets/passport_preview.dart';
import '../widgets/ui_helpers.dart';
import 'editor_screen.dart';
import 'home_screen.dart';
import 'layout_screen.dart';

class BatchScreen extends StatelessWidget {
  const BatchScreen({super.key});

  Future<void> _pickSize(BuildContext context) async {
    final st = context.read<StudioState>();
    final id = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (_) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.7,
        builder: (context, sc) => ListView(controller: sc, children: [
          for (final p in PhotoSize.presets)
            ListTile(
              leading: Icon(st.sizeId == p.id
                  ? Icons.radio_button_checked
                  : Icons.radio_button_unchecked),
              onTap: () => Navigator.pop(context, p.id),
              title: Text(p.name),
              subtitle: Text(p.subtitle),
            ),
          ListTile(
            leading: Icon(st.sizeId == 'custom'
                ? Icons.radio_button_checked
                : Icons.radio_button_unchecked),
            onTap: () => Navigator.pop(context, 'custom'),
            title: const Text('Custom'),
            subtitle: Text(
                '${st.customW.toStringAsFixed(1)}×${st.customH.toStringAsFixed(1)} mm — নিজের মাপ দিন'),
          ),
        ]),
      ),
    );
    if (id == null || !context.mounted) return;
    if (id == 'custom') {
      final ok = await _customSizeDialog(context, st);
      if (!ok) return;
    }
    st.setSize(id);
  }

  Future<bool> _customSizeDialog(BuildContext context, StudioState st) async {
    final w = TextEditingController(text: st.customW.toStringAsFixed(1));
    final h = TextEditingController(text: st.customH.toStringAsFixed(1));
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('কাস্টম সাইজ (mm)'),
        content: Row(children: [
          Expanded(
              child: TextField(
                  controller: w,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'চওড়া'))),
          const Padding(padding: EdgeInsets.all(8), child: Text('×')),
          Expanded(
              child: TextField(
                  controller: h,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'উচ্চতা'))),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('বাতিল')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('ঠিক আছে')),
        ],
      ),
    );
    final wv = double.tryParse(w.text), hv = double.tryParse(h.text);
    w.dispose();
    h.dispose();
    if (ok != true || wv == null || hv == null || wv < 10 || hv < 10 || wv > 200 || hv > 200) {
      return false;
    }
    st.customW = wv;
    st.customH = hv;
    return true;
  }

  Future<void> _saveAll(BuildContext context, ImageFormat f) async {
    final st = context.read<StudioState>();
    final ready = st.ready;
    if (ready.isEmpty) return;
    final n = await withProgress<int>(context, 'রেন্ডার হচ্ছে…', (up) async {
      final r = await OutputService.renderAll(ready, st.size, st.dpi, st.bgColor,
          onProgress: (d, t) => up('রেন্ডার $d/$t'));
      up('সেভ হচ্ছে…');
      final files = <Uint8List>[];
      for (final x in r) {
        files.add(await OutputService.encode(x.image, f, st.jpgQuality));
      }
      return OutputService.saveToGallery(files, 'passport');
    });
    if (n != null && context.mounted) {
      toast(context, '$nটি ${f == ImageFormat.png ? 'PNG' : 'JPG'} গ্যালারির "Photo Studio" অ্যালবামে সেভ হয়েছে');
    }
  }

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    final cs = Theme.of(context).colorScheme;
    final size = st.size;
    final pending = st.photos
        .where((p) => p.status == PhotoStatus.pending || p.status == PhotoStatus.failed)
        .length;

    return Scaffold(
      appBar: AppBar(
        title: Text('ব্যাচ (${st.photos.length}/$kMaxBatch)'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_photo_alternate_outlined),
            tooltip: 'আরও ছবি',
            onPressed: () => HomeScreen.pickPhotos(context),
          ),
          PopupMenuButton<String>(
            onSelected: (v) async {
              switch (v) {
                case 'jpg':
                  await _saveAll(context, ImageFormat.jpg);
                case 'png':
                  await _saveAll(context, ImageFormat.png);
                case 'clear':
                  st.clear();
                  if (context.mounted) Navigator.pop(context);
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'jpg', child: Text('সব ছবি HD JPG সেভ')),
              PopupMenuItem(value: 'png', child: Text('সব ছবি PNG সেভ')),
              PopupMenuItem(value: 'clear', child: Text('ব্যাচ মুছুন')),
            ],
          ),
        ],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
          child: Row(children: [
            Expanded(
              child: ActionChip(
                avatar: const Icon(Icons.crop_portrait, size: 18),
                label: Text('${size.name} • ${size.label}', overflow: TextOverflow.ellipsis),
                onPressed: () => _pickSize(context),
              ),
            ),
            const SizedBox(width: 8),
            ActionChip(
              avatar: CircleAvatar(backgroundColor: st.bgColor, radius: 9),
              label: const Text('ব্যাকগ্রাউন্ড'),
              onPressed: () async {
                final c = await pickColor(context, st.bgColor);
                if (c != null) st.update(() => st.bgColor = c);
              },
            ),
          ]),
        ),
        if (st.processing)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('AI প্রসেসিং… ${st.processedCount}/${st.processedCount + st.photos.where((p) => p.status == PhotoStatus.pending || p.status == PhotoStatus.processing).length}'),
                  const SizedBox(height: 6),
                  LinearProgressIndicator(
                    value: st.photos.isEmpty
                        ? null
                        : st.photos.where((p) => p.status == PhotoStatus.done || p.status == PhotoStatus.failed).length /
                            st.photos.length,
                  ),
                ]),
              ),
              TextButton(onPressed: st.cancel, child: const Text('থামান')),
            ]),
          ),
        Expanded(
          child: st.photos.isEmpty
              ? Center(
                  child: FilledButton.icon(
                    icon: const Icon(Icons.photo_library),
                    label: const Text('ছবি যোগ করুন'),
                    onPressed: () => HomeScreen.pickPhotos(context),
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.all(12),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 3,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: size.aspect * 0.86,
                  ),
                  itemCount: st.photos.length,
                  itemBuilder: (context, i) => _Tile(photo: st.photos[i], index: i),
                ),
        ),
      ]),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
          child: Row(children: [
            if (pending > 0 && !st.processing)
              Expanded(
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.auto_fix_high),
                  label: Text('প্রসেস ($pending)'),
                  onPressed: () => st.processAll(),
                ),
              ),
            if (pending > 0 && !st.processing) const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: FilledButton.icon(
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                icon: const Icon(Icons.print),
                label: Text('প্রিন্ট / এক্সপোর্ট (${st.ready.length})'),
                onPressed: st.ready.isEmpty
                    ? null
                    : () => Navigator.push(context,
                        MaterialPageRoute(builder: (_) => const LayoutScreen())),
              ),
            ),
          ]),
        ),
      ),
      backgroundColor: cs.surface,
    );
  }
}

class _Tile extends StatelessWidget {
  final PhotoItem photo;
  final int index;
  const _Tile({required this.photo, required this.index});

  @override
  Widget build(BuildContext context) {
    final st = context.read<StudioState>();
    final cs = Theme.of(context).colorScheme;
    Widget body;
    switch (photo.status) {
      case PhotoStatus.done:
        body = PassportPreview(photo: photo, size: st.size, bg: st.bgColor);
      case PhotoStatus.processing:
      case PhotoStatus.pending:
        body = Stack(fit: StackFit.expand, children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: Image.file(File(photo.sourcePath),
                fit: BoxFit.cover, cacheWidth: 240,
                errorBuilder: (_, _, _) => const ColoredBox(color: Colors.black12)),
          ),
          Container(color: Colors.black38),
          Center(
            child: photo.status == PhotoStatus.processing
                ? const CircularProgressIndicator(color: Colors.white)
                : const Icon(Icons.hourglass_empty, color: Colors.white),
          ),
        ]);
      case PhotoStatus.failed:
        body = Container(
          decoration: BoxDecoration(
              color: cs.errorContainer, borderRadius: BorderRadius.circular(6)),
          padding: const EdgeInsets.all(6),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(Icons.error_outline, color: cs.onErrorContainer),
            Text(photo.error ?? 'ব্যর্থ',
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11, color: cs.onErrorContainer)),
          ]),
        );
    }

    return GestureDetector(
      onTap: photo.isReady
          ? () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => EditorScreen(initialIndex: index)))
          : null,
      onLongPress: () async {
        final del = await showDialog<bool>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('ছবিটি বাদ দেবেন?'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('না')),
              FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('বাদ দিন')),
            ],
          ),
        );
        if (del == true) st.remove(photo);
      },
      child: Column(children: [
        Expanded(child: Center(child: body)),
        const SizedBox(height: 4),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Text('#${index + 1}', style: Theme.of(context).textTheme.labelSmall),
          if (photo.isReady && !photo.faceFound) ...[
            const SizedBox(width: 4),
            Tooltip(
                message: 'মুখ পাওয়া যায়নি — হাতে ঠিক করুন',
                child: Icon(Icons.warning_amber, size: 14, color: cs.error)),
          ],
        ]),
      ]),
    );
  }
}
