import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart' show Color, Colors;
import 'package:shared_preferences/shared_preferences.dart';

import '../models/photo_item.dart';
import '../models/photo_size.dart';
import '../models/sheet_config.dart';
import '../services/ai_processor.dart';

const kMaxBatch = 30;

const kBgPresets = <Color>[
  Color(0xFFFFFFFF), // white
  Color(0xFFF2F2F2), // off-white
  Color(0xFF6FA8DC), // light blue
  Color(0xFF1E5AA8), // passport blue
  Color(0xFF3D85C6),
  Color(0xFFD9D9D9), // light grey
  Color(0xFFE06666), // red
  Color(0xFF93C47D), // green
  Color(0xFFFFE599), // cream
];

class StudioState extends ChangeNotifier {
  final List<PhotoItem> photos = [];
  Color bgColor = Colors.white;
  String sizeId = 'p40x50';
  double customW = 35, customH = 45;
  int dpi = 300;
  int jpgQuality = 95;
  double edgeSoftness = 0.5;
  final SheetConfig sheet = SheetConfig();

  // Remembered printers.
  String lastPrinterHost = '';
  int lastPrinterPort = 631;
  String lastPrinterPath = '/ipp/print';
  String lastBtMac = '';
  bool bt80mm = false;

  bool processing = false;
  int processedCount = 0;
  int _idSeq = 0;
  bool _cancel = false;

  PhotoSize get size => PhotoSize.byId(sizeId, customW: customW, customH: customH);
  List<PhotoItem> get ready => photos.where((p) => p.isReady).toList();
  int get pendingCount =>
      photos.where((p) => p.status == PhotoStatus.pending).length;

  Future<void> load() async {
    final sp = await SharedPreferences.getInstance();
    bgColor = Color(sp.getInt('bg') ?? 0xFFFFFFFF);
    sizeId = sp.getString('size') ?? sizeId;
    customW = sp.getDouble('customW') ?? customW;
    customH = sp.getDouble('customH') ?? customH;
    dpi = sp.getInt('dpi') ?? dpi;
    jpgQuality = sp.getInt('jpgQ') ?? jpgQuality;
    edgeSoftness = sp.getDouble('soft') ?? edgeSoftness;
    sheet.paperId = sp.getString('paper') ?? sheet.paperId;
    sheet.orientation =
        SheetOrientation.values[sp.getInt('orient') ?? sheet.orientation.index];
    sheet.marginMm = sp.getDouble('margin') ?? sheet.marginMm;
    sheet.gapMm = sp.getDouble('gap') ?? sheet.gapMm;
    sheet.cutLines = sp.getBool('cut') ?? sheet.cutLines;
    sheet.copiesPerPhoto = sp.getInt('copies') ?? sheet.copiesPerPhoto;
    lastPrinterHost = sp.getString('pHost') ?? '';
    lastPrinterPort = sp.getInt('pPort') ?? 631;
    lastPrinterPath = sp.getString('pPath') ?? '/ipp/print';
    lastBtMac = sp.getString('btMac') ?? '';
    bt80mm = sp.getBool('bt80') ?? false;
    notifyListeners();
  }

  Future<void> save() async {
    final sp = await SharedPreferences.getInstance();
    await sp.setInt('bg', bgColor.toARGB32());
    await sp.setString('size', sizeId);
    await sp.setDouble('customW', customW);
    await sp.setDouble('customH', customH);
    await sp.setInt('dpi', dpi);
    await sp.setInt('jpgQ', jpgQuality);
    await sp.setDouble('soft', edgeSoftness);
    await sp.setString('paper', sheet.paperId);
    await sp.setInt('orient', sheet.orientation.index);
    await sp.setDouble('margin', sheet.marginMm);
    await sp.setDouble('gap', sheet.gapMm);
    await sp.setBool('cut', sheet.cutLines);
    await sp.setInt('copies', sheet.copiesPerPhoto);
    await sp.setString('pHost', lastPrinterHost);
    await sp.setInt('pPort', lastPrinterPort);
    await sp.setString('pPath', lastPrinterPath);
    await sp.setString('btMac', lastBtMac);
    await sp.setBool('bt80', bt80mm);
  }

  void update(VoidCallback f) {
    f();
    notifyListeners();
    unawaited(save());
  }

  /// Adds photos; returns how many were skipped because of the batch limit.
  int addPhotos(List<String> paths) {
    var skipped = 0;
    for (final p in paths) {
      if (photos.length >= kMaxBatch) {
        skipped++;
        continue;
      }
      photos.add(PhotoItem(
          id: '${DateTime.now().millisecondsSinceEpoch}_${_idSeq++}',
          sourcePath: p));
    }
    notifyListeners();
    return skipped;
  }

  void remove(PhotoItem p) {
    photos.remove(p);
    notifyListeners();
  }

  void clear() {
    if (processing) _cancel = true;
    photos.clear();
    notifyListeners();
  }

  void setSize(String id) {
    sizeId = id;
    // Re-frame all photos for the new aspect / head ratio.
    for (final p in photos) {
      if (p.isReady) AiProcessor.autoCrop(p, size);
    }
    notifyListeners();
    unawaited(save());
  }

  void cancel() => _cancel = true;

  /// Processes all pending (and optionally failed) photos, 2 at a time.
  Future<void> processAll({bool retryFailed = true}) async {
    if (processing) return;
    processing = true;
    _cancel = false;
    final queue = photos
        .where((p) =>
            p.status == PhotoStatus.pending ||
            (retryFailed && p.status == PhotoStatus.failed))
        .toList();
    processedCount = 0;
    notifyListeners();

    var next = 0;
    Future<void> worker() async {
      while (!_cancel && next < queue.length) {
        final p = queue[next++];
        if (!photos.contains(p)) continue;
        p.status = PhotoStatus.processing;
        p.error = null;
        notifyListeners();
        try {
          await AiProcessor.instance.process(p, size, edgeSoftness);
          p.status = PhotoStatus.done;
        } catch (e) {
          p.status = PhotoStatus.failed;
          p.error = e.toString().replaceFirst('Exception: ', '');
        }
        processedCount++;
        notifyListeners();
      }
    }

    await Future.wait([worker(), worker()]);
    processing = false;
    notifyListeners();
  }

  /// Re-runs AI on one photo (e.g. after changing edge softness).
  Future<void> reprocess(PhotoItem p) async {
    p.status = PhotoStatus.processing;
    notifyListeners();
    try {
      p.removeBackground = true;
      await AiProcessor.instance.process(p, size, edgeSoftness);
      p.status = PhotoStatus.done;
    } catch (e) {
      p.status = PhotoStatus.failed;
      p.error = e.toString();
    }
    notifyListeners();
  }

  void touch() => notifyListeners();
}
