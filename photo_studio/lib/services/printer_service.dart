import 'dart:async';
import 'dart:isolate';
import 'dart:math' as math;

import 'package:esc_pos_utils_plus/esc_pos_utils_plus.dart' as esc;
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'package:multicast_dns/multicast_dns.dart';
import 'package:pdf/pdf.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:print_bluetooth_thermal/print_bluetooth_thermal.dart';
import 'package:printing/printing.dart';

import 'ipp_client.dart';

class NetPrinter {
  final String name;
  final String host;
  final int port;
  final String path;
  NetPrinter(this.name, this.host, this.port, this.path);
}

class PrinterService {
  static const _native = MethodChannel('photo_studio/net');

  /// Android print framework: shows the system dialog, which lists Wi-Fi,
  /// Wi-Fi Direct, USB-OTG and Bluetooth printers handled by installed print
  /// services (Mopria, HP, Epson, Canon, Brother…).
  static Future<bool> systemPrint(
      Uint8List pdf, double widthMm, double heightMm, String name) {
    return Printing.layoutPdf(
      name: name,
      format: PdfPageFormat(
          widthMm * PdfPageFormat.mm, heightMm * PdfPageFormat.mm,
          marginAll: 0),
      dynamicLayout: false,
      onLayout: (_) async => pdf,
    );
  }

  /// Finds IPP printers on the local Wi-Fi network via mDNS/Bonjour.
  static Future<List<NetPrinter>> discover(
      {Duration timeout = const Duration(seconds: 6)}) async {
    final found = <String, NetPrinter>{};
    try {
      await _native.invokeMethod('acquireMulticast');
    } catch (_) {}
    final client = MDnsClient();
    try {
      await client.start();
      for (final service in ['_ipp._tcp.local', '_ipps._tcp.local']) {
        final ptrs = await client
            .lookup<PtrResourceRecord>(
                ResourceRecordQuery.serverPointer(service),
                timeout: timeout)
            .toList();
        for (final ptr in ptrs) {
          var path = '/ipp/print';
          final txts = await client
              .lookup<TxtResourceRecord>(
                  ResourceRecordQuery.text(ptr.domainName),
                  timeout: const Duration(seconds: 2))
              .toList();
          for (final t in txts) {
            for (final line in t.text.split('\n')) {
              if (line.startsWith('rp=')) path = '/${line.substring(3)}';
            }
          }
          final srvs = await client
              .lookup<SrvResourceRecord>(
                  ResourceRecordQuery.service(ptr.domainName),
                  timeout: const Duration(seconds: 2))
              .toList();
          for (final srv in srvs) {
            final ips = await client
                .lookup<IPAddressResourceRecord>(
                    ResourceRecordQuery.addressIPv4(srv.target),
                    timeout: const Duration(seconds: 2))
                .toList();
            for (final ip in ips) {
              final name = ptr.domainName.split('._ipp').first;
              final host = ip.address.address;
              // Plain IPP (port 631) is what we speak; skip TLS-only entries.
              final port = service.startsWith('_ipps') ? 631 : srv.port;
              found.putIfAbsent(host, () => NetPrinter(name, host, port, path));
            }
          }
        }
      }
    } finally {
      client.stop();
      try {
        await _native.invokeMethod('releaseMulticast');
      } catch (_) {}
    }
    return found.values.toList();
  }

  /// Direct Wi-Fi print via IPP. Sends PDF if the printer accepts it,
  /// otherwise one JPG per page.
  static Future<String> ippPrint(NetPrinter p, Uint8List pdf,
      Future<List<Uint8List>> Function() pageJpgs,
      {int copies = 1}) async {
    final c = IppClient(p.host, port: p.port, path: p.path);
    final info = await c.getInfo();
    if (info.pdf) {
      await c.printJob(pdf, 'application/pdf', copies: copies);
      return 'PDF পাঠানো হয়েছে → ${info.model.isEmpty ? p.host : info.model}';
    }
    if (info.jpeg) {
      final pages = await pageJpgs();
      for (final pg in pages) {
        await c.printJob(pg, 'image/jpeg', copies: copies);
      }
      return '${pages.length}টি পেজ (JPG) পাঠানো হয়েছে → ${info.model.isEmpty ? p.host : info.model}';
    }
    throw Exception(
        'এই প্রিন্টার PDF/JPG সরাসরি নেয় না। "সিস্টেম প্রিন্ট" ব্যবহার করুন।');
  }

  // ---------------- Bluetooth (ESC/POS thermal) ----------------

  static Future<bool> ensureBluetoothPermission() async {
    final res = await [
      Permission.bluetoothConnect,
      Permission.bluetoothScan,
    ].request();
    final ok = res.values.every((s) => s.isGranted || s.isLimited);
    if (ok) return true;
    // Android ≤ 11 has no runtime Bluetooth permission.
    return PrintBluetoothThermal.isPermissionBluetoothGranted;
  }

  static Future<List<BluetoothInfo>> pairedBluetooth() =>
      PrintBluetoothThermal.pairedBluetooths;

  /// Prints the photos on a Bluetooth thermal (ESC/POS) printer in black &
  /// white, at true size (203 dpi), as many per row as fit.
  static Future<void> bluetoothPrint({
    required String mac,
    required List<img.Image> photos,
    required List<int> copies,
    required double photoWmm,
    required double photoHmm,
    required bool paper80,
  }) async {
    final profile = await esc.CapabilityProfile.load();
    final paper = paper80 ? esc.PaperSize.mm80 : esc.PaperSize.mm58;
    final paperDots = paper.width;
    final job = _ThermalJob(photos, copies, photoWmm, photoHmm, paperDots);
    final rows = await _thermalInIsolate(job);

    final gen = esc.Generator(paper, profile);
    final bytes = <int>[...gen.reset()];
    for (final r in rows) {
      bytes.addAll(gen.imageRaster(r));
      bytes.addAll(gen.feed(1));
    }
    bytes.addAll(gen.feed(3));
    bytes.addAll(gen.cut());

    if (!await PrintBluetoothThermal.connectionStatus) {
      final ok = await PrintBluetoothThermal.connect(macPrinterAddress: mac);
      if (!ok) throw Exception('প্রিন্টারের সাথে সংযোগ হয়নি');
    }
    // Send in chunks so cheap printers' buffers don't overflow.
    const chunk = 4096;
    for (var i = 0; i < bytes.length; i += chunk) {
      final ok = await PrintBluetoothThermal.writeBytes(
          bytes.sublist(i, math.min(bytes.length, i + chunk)));
      if (!ok) throw Exception('ডেটা পাঠাতে ব্যর্থ');
    }
  }
}

Future<List<img.Image>> _thermalInIsolate(_ThermalJob job) =>
    Isolate.run(() => _thermalRows(job));

class _ThermalJob {
  final List<img.Image> photos;
  final List<int> copies;
  final double wMm, hMm;
  final int paperDots;
  _ThermalJob(this.photos, this.copies, this.wMm, this.hMm, this.paperDots);
}

List<img.Image> _thermalRows(_ThermalJob j) {
  const dotsPerMm = 8.0; // 203 dpi
  var w = (j.wMm * dotsPerMm).round();
  var h = (j.hMm * dotsPerMm).round();
  if (w > j.paperDots) {
    h = (h * j.paperDots / w).round();
    w = j.paperDots;
  }
  const gap = 16;
  final perRow = math.max(1, (j.paperDots + gap) ~/ (w + gap));
  final seq = <img.Image>[];
  for (var i = 0; i < j.photos.length; i++) {
    final g = img.grayscale(img.copyResize(j.photos[i],
        width: w, height: h, interpolation: img.Interpolation.average));
    img.adjustColor(g, contrast: 1.15);
    for (var k = 0; k < math.max(1, j.copies[i]); k++) {
      seq.add(g);
    }
  }
  final rows = <img.Image>[];
  for (var s = 0; s < seq.length; s += perRow) {
    final row = img.Image(width: j.paperDots, height: h);
    img.fill(row, color: img.ColorRgb8(255, 255, 255));
    final n = math.min(perRow, seq.length - s);
    final total = n * w + (n - 1) * gap;
    var x = (j.paperDots - total) ~/ 2;
    for (var k = 0; k < n; k++) {
      img.compositeImage(row, seq[s + k], dstX: x);
      x += w + gap;
    }
    // Floyd–Steinberg dithering gives far better photos on thermal paper.
    rows.add(img.ditherImage(row,
        quantizer: img.BinaryQuantizer(),
        kernel: img.DitherKernel.floydSteinberg));
  }
  return rows;
}
