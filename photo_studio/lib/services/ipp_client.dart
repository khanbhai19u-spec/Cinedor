import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

/// Minimal IPP (Internet Printing Protocol) client, used to print directly to
/// network (Wi-Fi / LAN) printers without the Android print dialog.
/// Almost every modern Wi-Fi printer supports IPP ("AirPrint"/"IPP Everywhere"/
/// "Mopria") on port 631.
class IppClient {
  final String host;
  final int port;
  final String path;
  IppClient(this.host, {this.port = 631, this.path = '/ipp/print'});

  static const _tagOperation = 0x01;
  static const _tagJob = 0x02;
  static const _tagEnd = 0x03;
  static const _integer = 0x21;
  static const _keyword = 0x44;
  static const _uri = 0x45;
  static const _charset = 0x47;
  static const _language = 0x48;
  static const _mime = 0x49;
  static const _name = 0x42;

  int _reqId = 1;

  String get printerUri => 'ipp://$host:$port$path';

  void _attr(BytesBuilder b, int tag, String name, List<int> value) {
    final n = utf8.encode(name);
    b.addByte(tag);
    b.add([n.length >> 8, n.length & 0xff]);
    b.add(n);
    b.add([value.length >> 8, value.length & 0xff]);
    b.add(value);
  }

  void _str(BytesBuilder b, int tag, String name, String v) =>
      _attr(b, tag, name, utf8.encode(v));

  BytesBuilder _header(int op) {
    final b = BytesBuilder();
    final id = _reqId++;
    b.add([0x01, 0x01, op >> 8, op & 0xff]);
    b.add([id >> 24 & 0xff, id >> 16 & 0xff, id >> 8 & 0xff, id & 0xff]);
    b.addByte(_tagOperation);
    _str(b, _charset, 'attributes-charset', 'utf-8');
    _str(b, _language, 'attributes-natural-language', 'en');
    _str(b, _uri, 'printer-uri', printerUri);
    return b;
  }

  Future<Uint8List> _post(Uint8List body) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final req = await client.postUrl(Uri.parse('http://$host:$port$path'));
      req.headers.contentType = ContentType('application', 'ipp');
      req.contentLength = body.length;
      req.add(body);
      final res = await req.close().timeout(const Duration(seconds: 90));
      final bytes = await res.fold<BytesBuilder>(
          BytesBuilder(), (bb, d) => bb..add(d));
      if (res.statusCode != 200) {
        throw Exception('Printer HTTP ${res.statusCode}');
      }
      return bytes.takeBytes();
    } finally {
      client.close(force: true);
    }
  }

  /// Returns supported document formats and the printer's name.
  Future<PrinterInfo> getInfo() async {
    final b = _header(0x000B); // Get-Printer-Attributes
    const wanted = [
      'document-format-supported',
      'printer-make-and-model',
      'printer-name',
      'printer-state'
    ];
    for (var i = 0; i < wanted.length; i++) {
      // Additional values of a multi-valued attribute use an empty name.
      _str(b, _keyword, i == 0 ? 'requested-attributes' : '', wanted[i]);
    }
    b.addByte(_tagEnd);
    final res = await _post(b.takeBytes());
    final status = (res[2] << 8) | res[3];
    if (status > 0x00ff) throw Exception('IPP error 0x${status.toRadixString(16)}');
    final attrs = _parse(res);
    return PrinterInfo(
      formats: attrs['document-format-supported'] ?? const [],
      model: (attrs['printer-make-and-model'] ?? const ['']).first,
      name: (attrs['printer-name'] ?? const ['']).first,
    );
  }

  /// Sends a document. [mime] must be one of the printer's supported formats.
  Future<void> printJob(Uint8List data, String mime,
      {String jobName = 'Photo Studio', int copies = 1}) async {
    final b = _header(0x0002); // Print-Job
    _str(b, _name, 'requesting-user-name', 'PhotoStudio');
    _str(b, _name, 'job-name', jobName);
    _str(b, _mime, 'document-format', mime);
    if (copies > 1) {
      b.addByte(_tagJob);
      _attr(b, _integer, 'copies',
          [copies >> 24 & 0xff, copies >> 16 & 0xff, copies >> 8 & 0xff, copies & 0xff]);
    }
    b.addByte(_tagEnd);
    b.add(data);
    final res = await _post(b.takeBytes());
    final status = (res[2] << 8) | res[3];
    if (status > 0x00ff) {
      throw Exception('প্রিন্টার জব নেয়নি (IPP 0x${status.toRadixString(16)})');
    }
  }

  static Map<String, List<String>> _parse(Uint8List r) {
    final out = <String, List<String>>{};
    var i = 8;
    String? last;
    while (i < r.length) {
      final tag = r[i++];
      if (tag == _tagEnd) break;
      if (tag < 0x10) continue; // delimiter tag
      if (i + 2 > r.length) break;
      final nl = (r[i] << 8) | r[i + 1];
      i += 2;
      final name = nl == 0 ? last : utf8.decode(r.sublist(i, i + nl), allowMalformed: true);
      i += nl;
      if (i + 2 > r.length) break;
      final vl = (r[i] << 8) | r[i + 1];
      i += 2;
      if (i + vl > r.length) break;
      final v = r.sublist(i, i + vl);
      i += vl;
      if (name == null) continue;
      last = name;
      if (tag >= 0x40) {
        (out[name] ??= []).add(utf8.decode(v, allowMalformed: true));
      }
    }
    return out;
  }
}

class PrinterInfo {
  final List<String> formats;
  final String model;
  final String name;
  PrinterInfo({required this.formats, required this.model, required this.name});

  bool get pdf => formats.contains('application/pdf');
  bool get jpeg => formats.contains('image/jpeg');
}

/// Sends raw bytes to port 9100 ("JetDirect"). Works with printers that
/// understand PDF directly (many office laser printers).
Future<void> sendRaw9100(String host, Uint8List data, {int port = 9100}) async {
  final s = await Socket.connect(host, port, timeout: const Duration(seconds: 8));
  try {
    s.add(data);
    await s.flush();
  } finally {
    await s.close();
  }
}
