import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/studio_state.dart';
import '../widgets/ui_helpers.dart';
import 'batch_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  static Future<void> pickPhotos(BuildContext context,
      {bool camera = false}) async {
    final st = context.read<StudioState>();
    final remaining = kMaxBatch - st.photos.length;
    if (remaining <= 0) {
      toast(context, 'একসাথে সর্বোচ্চ $kMaxBatchটি ছবি', error: true);
      return;
    }
    final picker = ImagePicker();
    List<String> paths;
    try {
      if (camera) {
        final x = await picker.pickImage(
            source: ImageSource.camera,
            preferredCameraDevice: CameraDevice.rear);
        paths = x == null ? [] : [x.path];
      } else {
        final xs = remaining == 1
            ? [await picker.pickImage(source: ImageSource.gallery)]
                .whereType<XFile>()
                .toList()
            : await picker.pickMultiImage(limit: remaining);
        paths = xs.map((e) => e.path).toList();
      }
    } catch (e) {
      if (context.mounted) toast(context, 'ছবি নেওয়া যায়নি: $e', error: true);
      return;
    }
    if (paths.isEmpty || !context.mounted) return;
    final skipped = st.addPhotos(paths);
    if (skipped > 0) {
      toast(context, '$skippedটি বাদ — সর্বোচ্চ $kMaxBatchটি ছবি');
    }
    st.processAll();
  }

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    final cs = Theme.of(context).colorScheme;

    void openBatch() => Navigator.push(
        context, MaterialPageRoute(builder: (_) => const BatchScreen()));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Photo Studio'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'সেটিংস',
            onPressed: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => const SettingsScreen())),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [cs.primary, cs.tertiary],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.portrait_rounded, size: 48, color: cs.onPrimary),
                const SizedBox(height: 8),
                Text('AI পাসপোর্ট ফটো স্টুডিও',
                    style: Theme.of(context)
                        .textTheme
                        .headlineSmall
                        ?.copyWith(color: cs.onPrimary, fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                Text(
                  'একসাথে $kMaxBatchটি পর্যন্ত ছবি • অটো ব্যাকগ্রাউন্ড রিমুভ • '
                  'পাসপোর্ট সাইজ • 4×6 / A4 প্রিন্ট — সম্পূর্ণ অফলাইন',
                  style: TextStyle(color: cs.onPrimary.withValues(alpha: 0.9)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          _BigButton(
            icon: Icons.photo_library_rounded,
            title: 'গ্যালারি থেকে ছবি বাছাই',
            subtitle: 'একসাথে ২০–৩০টি ছবি নির্বাচন করুন',
            onTap: () async {
              await pickPhotos(context);
              if (context.mounted && st.photos.isNotEmpty) openBatch();
            },
          ),
          _BigButton(
            icon: Icons.photo_camera_rounded,
            title: 'ক্যামেরা দিয়ে তুলুন',
            subtitle: 'হালকা ব্যাকগ্রাউন্ডের সামনে সোজা হয়ে দাঁড়ান',
            onTap: () async {
              await pickPhotos(context, camera: true);
              if (context.mounted && st.photos.isNotEmpty) openBatch();
            },
          ),
          if (st.photos.isNotEmpty)
            _BigButton(
              icon: Icons.grid_view_rounded,
              title: 'চলমান ব্যাচ খুলুন',
              subtitle:
                  '${st.photos.length}টি ছবি • ${st.ready.length}টি প্রস্তুত',
              onTap: openBatch,
            ),
          const SizedBox(height: 12),
          const SectionTitle('যা যা করা যায়'),
          const _Feature(Icons.auto_fix_high, 'AI ব্যাকগ্রাউন্ড রিমুভ',
              'ডিভাইসেই (অফলাইন) ML Kit দিয়ে'),
          const _Feature(Icons.palette_outlined, 'কাস্টম ব্যাকগ্রাউন্ড কালার',
              'সাদা, নীল বা যেকোনো রঙ'),
          const _Feature(Icons.face_retouching_natural, 'অটো ফেস ক্রপ',
              'মাথা ও মুখ নিয়ম অনুযায়ী মাঝখানে'),
          const _Feature(Icons.crop_portrait, 'পাসপোর্ট, 2×2 inch, স্ট্যাম্প',
              '৩৫×৪৫, ৪০×৫০, ৪৫×৫৫ mm ও কাস্টম'),
          const _Feature(Icons.grid_on, '4×6, A4, 5×7 প্রিন্ট শিট',
              'অটো লেআউট ও কাটিং লাইন'),
          const _Feature(Icons.print_outlined, 'Wi-Fi / USB / Bluetooth প্রিন্ট',
              'সিস্টেম প্রিন্ট, সরাসরি IP প্রিন্ট, থার্মাল প্রিন্টার'),
          const _Feature(Icons.picture_as_pdf_outlined, 'PDF, HD JPG ও PNG',
              'গ্যালারিতে সেভ বা শেয়ার'),
        ],
      ),
    );
  }
}

class _BigButton extends StatelessWidget {
  final IconData icon;
  final String title, subtitle;
  final VoidCallback onTap;
  const _BigButton(
      {required this.icon,
      required this.title,
      required this.subtitle,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: cs.surfaceContainerHigh,
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        leading: CircleAvatar(
          radius: 26,
          backgroundColor: cs.primaryContainer,
          child: Icon(icon, color: cs.onPrimaryContainer, size: 28),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _Feature extends StatelessWidget {
  final IconData icon;
  final String title, subtitle;
  const _Feature(this.icon, this.title, this.subtitle);
  @override
  Widget build(BuildContext context) => ListTile(
        dense: true,
        leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
        title: Text(title),
        subtitle: Text(subtitle),
      );
}
