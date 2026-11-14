import 'package:material_ui/material_ui.dart';
import 'package:print_bluetooth_thermal/print_bluetooth_thermal.dart';
import 'package:provider/provider.dart';

import '../services/ipp_client.dart';
import '../services/output_service.dart';
import '../services/printer_service.dart';
import '../state/studio_state.dart';
import '../widgets/ui_helpers.dart';
import 'layout_screen.dart';

class PrintScreen extends StatefulWidget {
  final PreparedOutput output;
  const PrintScreen({super.key, required this.output});

  @override
  State<PrintScreen> createState() => _PrintScreenState();
}

class _PrintScreenState extends State<PrintScreen> {
  late final TextEditingController host;
  late final TextEditingController port;
  late final TextEditingController path;
  List<NetPrinter> found = [];
  bool scanning = false;
  List<BluetoothInfo> bt = [];
  bool btLoading = false;
  int copies = 1;

  @override
  void initState() {
    super.initState();
    final st = context.read<StudioState>();
    host = TextEditingController(text: st.lastPrinterHost);
    port = TextEditingController(text: '${st.lastPrinterPort}');
    path = TextEditingController(text: st.lastPrinterPath);
  }

  @override
  void dispose() {
    host.dispose();
    port.dispose();
    path.dispose();
    super.dispose();
  }

  PreparedOutput get out => widget.output;

  Future<void> _system() async {
    final st = context.read<StudioState>();
    final pdf = await withProgress(context, 'PDF তৈরি হচ্ছে…', (_) => out.pdf(st.sheet.cutLines));
    if (pdf == null) return;
    final pg = out.pages.first;
    try {
      await PrinterService.systemPrint(pdf, pg.widthMm, pg.heightMm, 'Photo Studio');
    } catch (e) {
      if (mounted) toast(context, 'প্রিন্ট ব্যর্থ: $e', error: true);
    }
  }

  Future<void> _scan() async {
    setState(() => scanning = true);
    try {
      final r = await PrinterService.discover();
      if (!mounted) return;
      setState(() => found = r);
      if (r.isEmpty) toast(context, 'কোনো Wi-Fi প্রিন্টার পাওয়া যায়নি — IP হাতে দিন');
    } catch (e) {
      if (mounted) toast(context, 'স্ক্যান ব্যর্থ: $e', error: true);
    } finally {
      if (mounted) setState(() => scanning = false);
    }
  }

  NetPrinter? _current() {
    final h = host.text.trim();
    if (h.isEmpty) return null;
    var pth = path.text.trim();
    if (!pth.startsWith('/')) pth = '/$pth';
    return NetPrinter(h, h, int.tryParse(port.text.trim()) ?? 631, pth);
  }

  void _remember(NetPrinter p) {
    final st = context.read<StudioState>();
    st.update(() {
      st.lastPrinterHost = p.host;
      st.lastPrinterPort = p.port;
      st.lastPrinterPath = p.path;
    });
  }

  Future<void> _ipp() async {
    final p = _current();
    if (p == null) return toast(context, 'প্রিন্টারের IP দিন', error: true);
    _remember(p);
    final st = context.read<StudioState>();
    final msg = await withProgress<String>(context, 'প্রিন্টারে সংযোগ হচ্ছে…', (up) async {
      final pdf = await out.pdf(st.sheet.cutLines);
      up('পাঠানো হচ্ছে…');
      return PrinterService.ippPrint(
        p,
        pdf,
        () => OutputService.buildSheetImages(
            out.pages, out.photos, st.dpi, st.sheet.cutLines, ImageFormat.jpg,
            jpgQuality: 95),
        copies: copies,
      );
    });
    if (msg != null && mounted) toast(context, msg);
  }

  Future<void> _raw() async {
    final p = _current();
    if (p == null) return toast(context, 'প্রিন্টারের IP দিন', error: true);
    _remember(p);
    final st = context.read<StudioState>();
    final ok = await withProgress<bool>(context, 'RAW 9100 পাঠানো হচ্ছে…', (_) async {
      final pdf = await out.pdf(st.sheet.cutLines);
      for (var i = 0; i < copies; i++) {
        await sendRaw9100(p.host, pdf);
      }
      return true;
    });
    if (ok == true && mounted) toast(context, 'পাঠানো হয়েছে (প্রিন্টার PDF বুঝলে প্রিন্ট হবে)');
  }

  Future<void> _loadBt() async {
    setState(() => btLoading = true);
    try {
      if (!await PrinterService.ensureBluetoothPermission()) {
        if (mounted) toast(context, 'Bluetooth অনুমতি দিন', error: true);
        return;
      }
      if (!await PrintBluetoothThermal.bluetoothEnabled) {
        if (mounted) toast(context, 'ফোনের Bluetooth চালু করুন', error: true);
        return;
      }
      final r = await PrinterService.pairedBluetooth();
      if (!mounted) return;
      setState(() => bt = r);
      if (r.isEmpty) {
        toast(context, 'কোনো পেয়ার করা ডিভাইস নেই — আগে ফোনের Bluetooth সেটিংসে প্রিন্টার পেয়ার করুন');
      }
    } catch (e) {
      if (mounted) toast(context, 'Bluetooth ত্রুটি: $e', error: true);
    } finally {
      if (mounted) setState(() => btLoading = false);
    }
  }

  Future<void> _btPrint(String mac) async {
    final st = context.read<StudioState>();
    st.update(() => st.lastBtMac = mac);
    // Count copies of each photo from the layout.
    final counts = List<int>.filled(out.photos.length, 0);
    for (final p in out.pages) {
      for (final s in p.slots) {
        counts[s.photoIndex]++;
      }
    }
    final size = st.size;
    final ok = await withProgress<bool>(context, 'Bluetooth প্রিন্টারে পাঠানো হচ্ছে…', (_) async {
      await PrinterService.bluetoothPrint(
        mac: mac,
        photos: out.photos.map((e) => e.image).toList(),
        copies: counts.map((c) => (c * copies).clamp(1, 99)).toList(),
        photoWmm: size.widthMm,
        photoHmm: size.heightMm,
        paper80: st.bt80mm,
      );
      return true;
    });
    if (ok == true && mounted) toast(context, 'প্রিন্ট পাঠানো হয়েছে');
  }

  @override
  Widget build(BuildContext context) {
    final st = context.watch<StudioState>();
    final cs = Theme.of(context).colorScheme;
    final pg = out.pages.first;

    return Scaffold(
      appBar: AppBar(title: const Text('প্রিন্টার')),
      body: ListView(padding: const EdgeInsets.only(bottom: 32), children: [
        ListTile(
          leading: const Icon(Icons.description_outlined),
          title: Text('${out.pages.length}টি পেজ • ${pg.widthMm.toStringAsFixed(0)}×${pg.heightMm.toStringAsFixed(0)} mm'),
          subtitle: const Text('প্রিন্ট ডায়ালগে একই পেপার সাইজ ও স্কেল 100% (Actual size) দিন'),
        ),
        ListTile(
          title: const Text('কপি'),
          trailing: Row(mainAxisSize: MainAxisSize.min, children: [
            IconButton(
                onPressed: copies > 1 ? () => setState(() => copies--) : null,
                icon: const Icon(Icons.remove_circle_outline)),
            Text('$copies', style: Theme.of(context).textTheme.titleMedium),
            IconButton(
                onPressed: copies < 20 ? () => setState(() => copies++) : null,
                icon: const Icon(Icons.add_circle_outline)),
          ]),
        ),

        // ---- System print ----
        _Card(
          icon: Icons.print,
          title: 'সিস্টেম প্রিন্ট (প্রস্তাবিত)',
          subtitle: 'Wi-Fi, Wi-Fi Direct, USB-OTG ও Bluetooth — Android Print Service '
              '(Mopria / HP / Epson / Canon / Brother প্লাগইন) যে প্রিন্টার দেখায়',
          child: FilledButton.icon(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
            onPressed: _system,
            icon: const Icon(Icons.print),
            label: const Text('প্রিন্ট ডায়ালগ খুলুন'),
          ),
        ),

        // ---- Direct Wi-Fi ----
        _Card(
          icon: Icons.wifi,
          title: 'সরাসরি Wi-Fi প্রিন্ট (IP)',
          subtitle: 'একই Wi-Fi-এ থাকা প্রিন্টারে ডায়ালগ ছাড়াই পাঠান (IPP / AirPrint / Mopria)',
          child: Column(children: [
            Row(children: [
              Expanded(
                flex: 3,
                child: TextField(
                  controller: host,
                  keyboardType: TextInputType.url,
                  decoration: const InputDecoration(
                      labelText: 'প্রিন্টার IP', hintText: '192.168.1.50', border: OutlineInputBorder()),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: port,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Port', border: OutlineInputBorder()),
                ),
              ),
            ]),
            const SizedBox(height: 8),
            TextField(
              controller: path,
              decoration: const InputDecoration(
                  labelText: 'IPP path', hintText: '/ipp/print', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 8),
            Row(children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: scanning ? null : _scan,
                  icon: scanning
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.search),
                  label: const Text('খুঁজুন'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.icon(
                  onPressed: _ipp,
                  icon: const Icon(Icons.send),
                  label: const Text('প্রিন্ট'),
                ),
              ),
            ]),
            for (final p in found)
              ListTile(
                dense: true,
                leading: const Icon(Icons.print_outlined),
                title: Text(p.name),
                subtitle: Text('${p.host}:${p.port}${p.path}'),
                onTap: () => setState(() {
                  host.text = p.host;
                  port.text = '${p.port}';
                  path.text = p.path;
                }),
              ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: _raw,
                child: const Text('RAW 9100-এ PDF পাঠান (লেজার প্রিন্টার)'),
              ),
            ),
          ]),
        ),

        // ---- Bluetooth thermal ----
        _Card(
          icon: Icons.bluetooth,
          title: 'Bluetooth থার্মাল প্রিন্টার',
          subtitle: 'ESC/POS মিনি প্রিন্টার (58/80 mm) — সাদা-কালো, আসল মাপে',
          child: Column(children: [
            SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: false, label: Text('58 mm')),
                ButtonSegment(value: true, label: Text('80 mm')),
              ],
              selected: {st.bt80mm},
              onSelectionChanged: (s) => st.update(() => st.bt80mm = s.first),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(44)),
              onPressed: btLoading ? null : _loadBt,
              icon: btLoading
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.bluetooth_searching),
              label: const Text('পেয়ার করা প্রিন্টার দেখুন'),
            ),
            for (final d in bt)
              ListTile(
                leading: Icon(Icons.print,
                    color: d.macAdress == st.lastBtMac ? cs.primary : null),
                title: Text(d.name.isEmpty ? d.macAdress : d.name),
                subtitle: Text(d.macAdress),
                trailing: const Icon(Icons.send),
                onTap: () => _btPrint(d.macAdress),
              ),
          ]),
        ),

        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            'USB প্রিন্টার: OTG কেবল দিয়ে প্রিন্টার লাগান, তারপর Play Store থেকে প্রিন্টারের '
            'কোম্পানির Print Service প্লাগইন (যেমন HP Print Service, Epson Print Enabler, '
            'Canon Print Service) বা Mopria ইনস্টল করে "সিস্টেম প্রিন্ট" ব্যবহার করুন। '
            'Bluetooth ফটো প্রিন্টারও (যেমন Canon Selphy, HP Sprocket) তাদের Print Service দিয়ে '
            'সিস্টেম প্রিন্টে দেখা যায়।',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
      ]),
    );
  }
}

class _Card extends StatelessWidget {
  final IconData icon;
  final String title, subtitle;
  final Widget child;
  const _Card({required this.icon, required this.title, required this.subtitle, required this.child});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      elevation: 0,
      color: cs.surfaceContainerHigh,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Icon(icon, color: cs.primary),
            const SizedBox(width: 10),
            Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
          ]),
          const SizedBox(height: 4),
          Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 12),
          child,
        ]),
      ),
    );
  }
}
