import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:photo_studio/services/ipp_client.dart';

/// Builds an IPP response with the given string attributes.
Uint8List ippResponse(int status, Map<String, List<String>> attrs) {
  final b = BytesBuilder();
  b.add([1, 1, status >> 8, status & 0xff, 0, 0, 0, 1]);
  b.addByte(0x01);
  void attr(int tag, String name, String v) {
    final n = utf8.encode(name), val = utf8.encode(v);
    b.addByte(tag);
    b.add([n.length >> 8, n.length & 0xff, ...n, val.length >> 8, val.length & 0xff, ...val]);
  }

  attr(0x47, 'attributes-charset', 'utf-8');
  b.addByte(0x04); // printer-attributes-tag
  attrs.forEach((k, vs) {
    for (var i = 0; i < vs.length; i++) {
      attr(k.startsWith('document') ? 0x49 : 0x41, i == 0 ? k : '', vs[i]);
    }
  });
  b.addByte(0x03);
  return b.takeBytes();
}

void main() {
  late HttpServer server;
  final received = <Uint8List>[];

  setUp(() async {
    received.clear();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((req) async {
      final body = await req.fold<BytesBuilder>(BytesBuilder(), (b, d) => b..add(d));
      final bytes = body.takeBytes();
      received.add(bytes);
      final op = (bytes[2] << 8) | bytes[3];
      req.response.headers.contentType = ContentType('application', 'ipp');
      if (op == 0x000B) {
        req.response.add(ippResponse(0, {
          'document-format-supported': ['application/octet-stream', 'image/jpeg', 'application/pdf'],
          'printer-make-and-model': ['Test Printer 9000'],
        }));
      } else {
        req.response.add(ippResponse(0, {}));
      }
      await req.response.close();
    });
  });

  tearDown(() => server.close(force: true));

  test('reads printer formats and sends a PDF job', () async {
    final c = IppClient('127.0.0.1', port: server.port, path: '/ipp/print');
    final info = await c.getInfo();
    expect(info.pdf, isTrue);
    expect(info.jpeg, isTrue);
    expect(info.model, 'Test Printer 9000');

    final doc = Uint8List.fromList(utf8.encode('%PDF-1.4 fake'));
    await c.printJob(doc, 'application/pdf', copies: 2);
    final job = received.last;
    expect((job[2] << 8) | job[3], 0x0002); // Print-Job
    final text = latin1.decode(job);
    expect(text, contains('application/pdf'));
    expect(text, contains('ipp://127.0.0.1:${server.port}/ipp/print'));
    expect(text, contains('copies'));
    expect(text.endsWith('%PDF-1.4 fake'), isTrue);
  });
}
