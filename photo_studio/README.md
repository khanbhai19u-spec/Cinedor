# Photo Studio — AI পাসপোর্ট ফটো অ্যাপ (Flutter, Android)

সম্পূর্ণ **অফলাইন** ফটো স্টুডিও অ্যাপ: একসাথে ২০–৩০টি ছবি নিয়ে AI দিয়ে ব্যাকগ্রাউন্ড রিমুভ, যেকোনো রঙের ব্যাকগ্রাউন্ড, অটো পাসপোর্ট ক্রপ, 4×6 / A4 প্রিন্ট শিট, Wi-Fi / USB / Bluetooth প্রিন্ট আর PDF / JPG / PNG এক্সপোর্ট।

## ফিচার

| ফিচার | কীভাবে কাজ করে |
|---|---|
| AI ব্যাকগ্রাউন্ড রিমুভ | Google ML Kit **Selfie Segmentation**। মডেল APK-এর ভেতরেই আছে, তাই ইন্টারনেট লাগে না। কিনারা পরিষ্কার করা হয় smoothstep, ছোট দাগ মুছে ফেলা আর feathering দিয়ে |
| অটো পাসপোর্ট ফ্রেম | ML Kit **Face Detection** মুখ খোঁজে আর সেগমেন্টেশন মাস্ক থেকে মাথার উপরের অংশ বের করে। তারপর প্রতিটি সাইজের নিয়ম অনুযায়ী মাথা কতটা জায়গা নেবে তা বসায় |
| ব্যাচ প্রসেসিং | একসাথে সর্বোচ্চ ৩০টি ছবি। একসাথে ২টি ছবি প্রসেস হয়, ভারী কাজ চলে background isolate-এ, তাই UI আটকায় না |
| কাস্টম ব্যাকগ্রাউন্ড | রেডিমেড রঙ, HSV কালার পিকার আর HEX কোড। পুরো ব্যাচে এক রঙ, বা প্রতিটি ছবিতে আলাদা রঙ |
| সাইজ | 35×45, 40×50 (BD), 45×55 (BD e-Passport), **2×2 inch**, 30×40, 25×30, 1×1 inch, আর কাস্টম মাপ |
| এডিটর | আঙুল দিয়ে টেনে / পিঞ্চ করে জুম, গাইড লাইন, উজ্জ্বলতা ও কনট্রাস্ট, before/after (AI চালু / বন্ধ) |
| প্রিন্ট শিট | **4×6**, **A4**, 5×7, A5, Letter। অটো orientation, মার্জিন, ফাঁক, কাটিং লাইন। "পুরো পেজ ভরাট" বা নির্দিষ্ট সংখ্যক কপি |
| রেজোলিউশন | 300 বা 600 DPI, আসল মিলিমিটার মাপে |
| এক্সপোর্ট | **PDF** (সঠিক পেজ সাইজ), শিট **HD JPG / PNG**, আলাদা আলাদা ছবি JPG / PNG। গ্যালারির "Photo Studio" অ্যালবামে সেভ, ফাইল সেভ বা শেয়ার |
| প্রিন্ট | ১) **সিস্টেম প্রিন্ট**: Android Print Service দিয়ে Wi-Fi, Wi-Fi Direct, USB-OTG আর Bluetooth<br>২) **সরাসরি Wi-Fi প্রিন্ট**: IPP (AirPrint / Mopria / IPP Everywhere) দিয়ে, mDNS দিয়ে প্রিন্টার খোঁজা যায়<br>৩) **RAW 9100**: লেজার প্রিন্টারের জন্য<br>৪) **Bluetooth থার্মাল** (ESC/POS 58 / 80 mm): dithering সহ আসল মাপে |

## APK বানানো (GitHub Actions, PC লাগবে না)

1. GitHub রিপোর পাতায় যান: **Actions → Build Photo Studio (Flutter) → Run workflow**।
2. Version দিন (যেমন `1.0.0`), তারপর Run চাপুন।
3. বিল্ড শেষ হলে **Releases**-এ `Photo Studio v1.0.0` পাবেন। সেখান থেকে APK ডাউনলোড করে ইনস্টল করুন।
   - `photo_studio/` ফোল্ডারে কিছু push করলেও বিল্ড চলে। তখন APK পাবেন Actions রানের **Artifacts**-এ।
4. সাইনিং: আগের মতোই `KEYSTORE_BASE64` আর `KEYSTORE_PASSWORD` secret ব্যবহার হয় (key alias `cinedor`)। secret না থাকলে debug key দিয়ে সাইন হয়।

## নিজের PC-তে বিল্ড

```bash
cd photo_studio
flutter pub get
flutter test
flutter build apk --release
# APK: build/app/outputs/flutter-apk/app-release.apk
```
লাগবে: Flutter 3.50+ (Dart 3.14) আর Android SDK।

## প্রিন্টার সেটআপ

- **Wi-Fi প্রিন্টার**: ফোন আর প্রিন্টার একই Wi-Fi-এ রাখুন। "সিস্টেম প্রিন্ট" চাপুন, অথবা "সরাসরি Wi-Fi প্রিন্ট"-এ **খুঁজুন** চাপুন বা IP লিখুন (প্রিন্টারের নেটওয়ার্ক কনফিগ পেজে IP পাবেন)।
- **USB প্রিন্টার**: OTG কেবল দিয়ে লাগান। প্রিন্টারের কোম্পানির Print Service (HP / Epson / Canon / Brother) বা Mopria ইনস্টল করুন, তারপর "সিস্টেম প্রিন্ট" ব্যবহার করুন।
- **Bluetooth ফটো প্রিন্টার** (Canon Selphy, HP Sprocket ইত্যাদি): কোম্পানির Print Service দিয়ে "সিস্টেম প্রিন্ট"।
- **Bluetooth থার্মাল / মিনি প্রিন্টার**: আগে ফোনের Bluetooth সেটিংসে পেয়ার করুন, তারপর অ্যাপে "পেয়ার করা প্রিন্টার দেখুন" চাপুন।
- প্রিন্ট ডায়ালগে পেপার সাইজ শিটের সাথে মিলিয়ে দিন আর **Scale 100% / Actual size** রাখুন। তাহলে ছবির মাপ মিলিমিটারে ঠিক থাকবে।

## কোডের গঠন

```
lib/
  main.dart                    অ্যাপ, থিম
  state/studio_state.dart      ব্যাচ, সেটিংস, প্রসেসিং কিউ
  models/                      PhotoItem, PhotoSize (প্রিসেট), SheetConfig + LayoutEngine
  services/
    ai_processor.dart          ML Kit: সেগমেন্টেশন + ফেস ডিটেকশন + অটো ক্রপ
    image_ops.dart             মাস্ক রিফাইন, ফাইনাল রেন্ডার (isolate-এ চলে)
    output_service.dart        PDF, শিট JPG/PNG, গ্যালারি, শেয়ার
    printer_service.dart       সিস্টেম প্রিন্ট, mDNS, IPP, Bluetooth ESC/POS
    ipp_client.dart            IPP প্রোটোকল ক্লায়েন্ট
  screens/                     Home, Batch, Editor, Layout, Print, Settings
  widgets/                     লাইভ প্রিভিউ, কালার পিকার
test/                          লেআউট, ইমেজ পাইপলাইন, PDF, IPP টেস্ট
```

## সীমাবদ্ধতা

- Selfie Segmentation মানুষের ছবির জন্য বানানো (পাসপোর্ট ছবির জন্য ঠিক এটাই দরকার)। খুব জটিল ব্যাকগ্রাউন্ড বা এলোমেলো চুলে কিনারা একটু নরম হতে পারে। তখন সেটিংসে "এজ স্মুথনেস" কমিয়ে বা বাড়িয়ে দেখুন।
- সরাসরি IPP প্রিন্টে প্রিন্টারকে PDF বা JPEG গ্রহণ করতে হয়। যে প্রিন্টার শুধু PWG raster নেয়, তার জন্য "সিস্টেম প্রিন্ট" ব্যবহার করুন।
- থার্মাল প্রিন্টারে ছবি শুধু সাদা-কালো আসে। এর বৈশিষ্ট্যই এমন।
