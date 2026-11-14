/// A finished-photo size (passport, visa, stamp…). All sizes are in millimetres.
class PhotoSize {
  final String id;
  final String name;
  final String subtitle;
  final double widthMm;
  final double heightMm;

  /// Fraction of the photo height the head (chin → crown) should fill.
  final double headRatio;

  /// Gap between the top edge of the photo and the crown, as a fraction of height.
  final double topRatio;

  const PhotoSize({
    required this.id,
    required this.name,
    required this.subtitle,
    required this.widthMm,
    required this.heightMm,
    this.headRatio = 0.62,
    this.topRatio = 0.10,
  });

  double get aspect => widthMm / heightMm;

  String get label => '${_fmt(widthMm)}×${_fmt(heightMm)} mm';

  static String _fmt(double v) =>
      v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toStringAsFixed(1);

  PhotoSize copyWith({double? widthMm, double? heightMm}) => PhotoSize(
        id: id,
        name: name,
        subtitle: subtitle,
        widthMm: widthMm ?? this.widthMm,
        heightMm: heightMm ?? this.heightMm,
        headRatio: headRatio,
        topRatio: topRatio,
      );

  static const presets = <PhotoSize>[
    PhotoSize(
        id: 'p35x45',
        name: 'Passport / Visa',
        subtitle: '35×45 mm — ভারত, UK, EU, Schengen',
        widthMm: 35,
        heightMm: 45,
        headRatio: 0.71,
        topRatio: 0.08),
    PhotoSize(
        id: 'p40x50',
        name: 'Passport (BD MRP)',
        subtitle: '40×50 mm — বাংলাদেশ',
        widthMm: 40,
        heightMm: 50,
        headRatio: 0.66,
        topRatio: 0.09),
    PhotoSize(
        id: 'p45x55',
        name: 'e-Passport (BD)',
        subtitle: '45×55 mm — বাংলাদেশ ই-পাসপোর্ট',
        widthMm: 45,
        heightMm: 55,
        headRatio: 0.66,
        topRatio: 0.09),
    PhotoSize(
        id: 'p2x2',
        name: '2×2 inch',
        subtitle: '51×51 mm — USA Visa / Passport',
        widthMm: 50.8,
        heightMm: 50.8,
        headRatio: 0.60,
        topRatio: 0.12),
    PhotoSize(
        id: 'p30x40',
        name: 'Stamp / Form',
        subtitle: '30×40 mm — ফর্ম, ভর্তি, চাকরি',
        widthMm: 30,
        heightMm: 40,
        headRatio: 0.62,
        topRatio: 0.10),
    PhotoSize(
        id: 'p25x30',
        name: 'Small Stamp',
        subtitle: '25×30 mm — ID কার্ড',
        widthMm: 25,
        heightMm: 30,
        headRatio: 0.62,
        topRatio: 0.10),
    PhotoSize(
        id: 'p1x1',
        name: '1×1 inch',
        subtitle: '25.4×25.4 mm',
        widthMm: 25.4,
        heightMm: 25.4,
        headRatio: 0.58,
        topRatio: 0.12),
  ];

  static PhotoSize custom(double w, double h) => PhotoSize(
      id: 'custom',
      name: 'Custom',
      subtitle: 'নিজের মাপ',
      widthMm: w,
      heightMm: h);

  static PhotoSize byId(String id, {double? customW, double? customH}) {
    if (id == 'custom') return custom(customW ?? 35, customH ?? 45);
    return presets.firstWhere((p) => p.id == id, orElse: () => presets.first);
  }
}
