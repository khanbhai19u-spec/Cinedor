import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/studio_state.dart';
import '../widgets/color_picker.dart';
import '../widgets/ui_helpers.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    return Scaffold(
      appBar: AppBar(title: const Text('সেটিংস')),
      body: ListView(children: [
        const SectionTitle('আউটপুট'),
        ListTile(
          title: const Text('রেজোলিউশন (DPI)'),
          subtitle: const Text('300 = সাধারণ প্রিন্ট, 600 = অতিরিক্ত শার্প'),
          trailing: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 300, label: Text('300')),
              ButtonSegment(value: 600, label: Text('600')),
            ],
            selected: {st.dpi},
            onSelectionChanged: (s) => st.update(() => st.dpi = s.first),
          ),
        ),
        ListTile(
          title: Text('JPG কোয়ালিটি: ${st.jpgQuality}'),
          subtitle: Slider(
            value: st.jpgQuality.toDouble(),
            min: 70,
            max: 100,
            divisions: 30,
            onChanged: (v) => st.update(() => st.jpgQuality = v.round()),
          ),
        ),
        ListTile(
          leading: CircleAvatar(backgroundColor: st.bgColor),
          title: const Text('ডিফল্ট ব্যাকগ্রাউন্ড কালার'),
          trailing: const Icon(Icons.palette_outlined),
          onTap: () async {
            final c = await pickColor(context, st.bgColor);
            if (c != null) st.update(() => st.bgColor = c);
          },
        ),
        const SectionTitle('AI ব্যাকগ্রাউন্ড রিমুভ'),
        ListTile(
          title: Text('এজ স্মুথনেস: ${(st.edgeSoftness * 100).round()}%'),
          subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Slider(
              value: st.edgeSoftness,
              onChanged: (v) => st.update(() => st.edgeSoftness = v),
            ),
            const Text('কম = ধারালো কিনারা, বেশি = নরম কিনারা (চুলের জন্য ভালো)। '
                'নতুন প্রসেস করা ছবিতে প্রযোজ্য।'),
          ]),
        ),
        const SectionTitle('ভালো ফলাফলের টিপস'),
        const ListTile(
          dense: true,
          leading: Icon(Icons.lightbulb_outline),
          title: Text('সামনে থেকে, সমান আলোয় ছবি তুলুন; মাথা ও কাঁধ পুরো দেখা যাক।'),
        ),
        const ListTile(
          dense: true,
          leading: Icon(Icons.lightbulb_outline),
          title: Text('পেছনের দেয়াল সাদামাটা হলে AI কিনারা সবচেয়ে পরিষ্কার হয়।'),
        ),
        const ListTile(
          dense: true,
          leading: Icon(Icons.lightbulb_outline),
          title: Text('প্রিন্টের সময় পেপার সাইজ মিলিয়ে স্কেল 100% / Actual size দিন।'),
        ),
        const SectionTitle('সম্পর্কে'),
        const ListTile(
          title: Text('Photo Studio 1.0'),
          subtitle: Text('সব প্রসেসিং ফোনেই হয় — ইন্টারনেট লাগে না, ছবি কোথাও আপলোড হয় না।'),
        ),
      ]),
    );
  }
}
