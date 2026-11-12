# Cinedor — পারসোনাল স্ট্রিমিং অ্যাপ + সার্ভার

তোমার **নিজের ভিডিও** (ফ্যামিলি ভিডিও, নিজের বানানো কনটেন্ট, যেগুলোর রাইট তোমার আছে) সুন্দর একটা স্ট্রিমিং-অ্যাপ স্টাইলে চালানোর জন্য।
সার্ভার শুধু `backend/media` ফোল্ডারের ফাইল দেয়, বাইরের কোনো সাইট থেকে কিছু আনে না।

## Android অ্যাপ — সব কিছু TMDB থেকে
অ্যাপ এখন সরাসরি TMDB থেকে সব আনে: Trending, Popular, K-Drama, C-Drama, Anime, Hindi, Top rated, সার্চ, ডিটেইল, কাস্ট, ট্রেইলার।
কোনো সার্ভার লাগে না। নিজের ভিডিও ফাইল চালাতে চাইলে তবেই **Settings › App Settings › Personal server** এ সার্ভার দাও (ঐচ্ছিক)।
TMDB শুধু তথ্য আর ছবি দেয়, ভিডিও ফাইল দেয় না — তাই TMDB-র টাইটেলে **Watch trailer** থাকে।

### TMDB key → GitHub Secret (একবারই করতে হবে)
1. GitHub-এ repo খোলো → **Settings → Secrets and variables → Actions → New repository secret**
2. Name: `TMDB_API_KEY` , Secret: তোমার TMDB key → **Add secret**
3. বিল্ডের সময় key অ্যাপে ঢুকে যায়; কোডে বা repo-তে কোথাও লেখা থাকে না।
   (চাইলে অ্যাপের **App Settings**-এ অন্য key দিয়ে বদলানো যায়।)

### Release (নিজে থেকে)
- `main` ব্রাঞ্চে push করলেই **Releases**-এ নতুন APK আসে (version `2.1.<run নম্বর>`)।
- হাতে চাইলে: **Actions → Build Cinedor (Android) → Run workflow** (version খালি রাখলে নিজে বসবে)।
- আপডেট ঠিকমতো ইনস্টল হতে সব release একই key দিয়ে সাইন হওয়া দরকার — তাই `KEYSTORE_BASE64` আর `KEYSTORE_PASSWORD` secret দুটোও রাখো।

### Telegram লিংক
`android/res/values/strings.xml` এ `telegram_url` এর ভেতরে লিংক দাও (যেমন `https://t.me/yourchannel`) — তাহলে Settings-এ "Join the Telegram Channel" দেখাবে।

## দুইভাবে চালানো যায়
- **ওয়েব অ্যাপ (মোবাইল-রেসপন্সিভ):** সার্ভার চালু করে ফোনের ব্রাউজারে `http://<IP>:3000` খোলো। ছোট ফোন, বড় ফোন, ট্যাবলেট, ল্যাপটপ — সব সাইজে লেআউট নিজে মানিয়ে নেয়। ব্রাউজারের মেনু → **Add to Home screen** দিলে অ্যাপের মতো খোলে।
- **Android অ্যাপ (APK):** নিচে "APK বানানো" দেখো। স্ক্রিন সাইজ অনুযায়ী কলাম, পোস্টার আর carousel-এর মাপ নিজে বদলায়।

## TMDB
TMDB key দিলে:
- তোমার নিজের ভিডিওর পোস্টার, বিবরণ, জনরা, রেটিং, ট্রেইলার **নিজে থেকে** চলে আসে (ফোল্ডারের নাম দিয়ে খোঁজে; ভুল মিললে `info.json`-এ `"tmdb": 603` বা `"tmdb": "tv/1399"` দাও)।
- Home-এ **Trending / Popular movies / Popular series / Top rated** সারি, Search-এ TMDB রেজাল্ট, কাস্ট আর ট্রেইলার সহ ডিটেইল পেজ।
- TMDB-র টাইটেল শুধু তথ্য আর ট্রেইলার — ভিডিও চলে শুধু তখনই যখন ফাইলটা তোমার `media` ফোল্ডারে আছে ("Play from your library")।

**Key কোথায় দেবে** (key সার্ভারে থাকে, অ্যাপ/গিটে না — repo public তাই কোডে key রাখবে না):
1. সহজ উপায়: ওয়েব অ্যাপ বা Android অ্যাপের **Settings → TMDB** তে key পেস্ট করে Save। এটা `backend/config.json` এ সেভ হয়।
2. অথবা: `TMDB_API_KEY=তোমার_key npm start`

## অ্যাপে যা আছে
| স্ক্রিন | কী আছে |
|---|---|
| **Home** | বড় পোস্টার carousel (দুই পাশের কার্ড উঁকি দেয়, auto-slide, dot indicator), সাদা **Play** বাটন, **Continue watching** (প্রোগ্রেস বার সহ), Recently added / Movies / Series সারি, pull-to-refresh |
| **Search** | টাইপ করার সাথে সাথে রেজাল্ট (টাইটেল, জনরা, সাল), ৩ কলাম গ্রিড |
| **Library** | **Favorites / History / Downloads** ট্যাব (সংখ্যা সহ), লং-প্রেস করে রিমুভ |
| **Settings** | সার্ভারের ঠিকানা আর টোকেন অ্যাপের ভেতর থেকেই বদলানো যায়, কানেকশন টেস্ট, হিস্টরি/ফেভারিট মুছা |
| **Detail** | পোস্টার, Play / Resume, Download, বুকমার্ক, সিরিজ হলে এপিসোড লিস্ট |
| **Player** | ExoPlayer (MP4 + HLS), ফুলস্ক্রিন, ±১০ সেকেন্ড, যেখানে থেমেছিলে সেখান থেকে আবার চলে |

নিচে ভাসমান pill-shaped নেভিগেশন বার — সিলেক্ট করা ট্যাব চওড়া হয়ে নাম দেখায়।

---

## ১. সার্ভার চালানো (Termux বা PC)

```bash
pkg install nodejs -y          # Termux-এ (PC-তে nodejs.org থেকে Node 18+)
cd backend
npm start                      # কোনো npm install লাগবে না
```
- চেক: ব্রাউজারে `http://<ফোন/PC-এর IP>:3000/api/videos`
  (Termux-এ IP দেখতে: `ifconfig` → `wlan0` → `inet`)
- লক করতে চাইলে: `APP_TOKEN=myPass123 npm start` — তারপর অ্যাপের Settings-এ একই টোকেন দাও।
- পোর্ট বদলাতে: `PORT=5000 npm start`

### ভিডিও রাখার নিয়ম
```
backend/media/
  My Trip 2026/                     ← একটা মুভি
    info.json     (ঐচ্ছিক)
    poster.jpg    (ঐচ্ছিক, খাড়া পোস্টার — Home carousel-এ এটাই দেখায়)
    360p.mp4
    720p.mp4
    1080p.mp4
    hls/master.m3u8  (+ .ts ফাইল)   (ঐচ্ছিক)

  Family Vlog/                      ← একটা সিরিজ (ভেতরে এপিসোড ফোল্ডার)
    info.json
    poster.jpg
    E01 Arrival/720p.mp4
    E02 Beach/720p.mp4
```
`info.json` উদাহরণ:
```json
{ "title": "My Trip", "year": 2026, "genre": "Travel", "description": "Summer trip with family", "featured": true }
```
- ফাইলের নামে `360p` / `720p` / `1080p` থাকলে কোয়ালিটি নিজে চিনে নেয়।
- `"featured": false` দিলে Home-এর বড় carousel-এ দেখাবে না (সারিতে দেখাবে)।
- নতুন ফোল্ডার দিলে ৩০ সেকেন্ডের মধ্যে অ্যাপে চলে আসে (Home-এ refresh চাপো)।

### ffmpeg দিয়ে কোয়ালিটি আর HLS বানানো
```bash
pkg install ffmpeg -y
ffmpeg -i myvideo.mp4 -vf scale=-2:720 -c:v libx264 -crf 23 -preset veryfast -c:a aac -movflags +faststart 720p.mp4
ffmpeg -i myvideo.mp4 -vf scale=-2:360 -c:v libx264 -crf 26 -preset veryfast -c:a aac -movflags +faststart 360p.mp4
mkdir hls && ffmpeg -i 720p.mp4 -c copy -hls_time 6 -hls_playlist_type vod -hls_segment_filename "hls/seg_%03d.ts" hls/master.m3u8
```

---

## ২. APK বানানো (GitHub Actions)

- `android/` ফোল্ডারে কিছু বদলে push করলেই **Actions** ট্যাবে বিল্ড চলে। রান শেষ হলে রানের পেজের নিচে
  **Artifacts** থেকে `Cinedor-v….apk` নামাও।
- Release বানাতে: **Actions → Build Cinedor (Android) → Run workflow** → version দাও (যেমন `2.0.1`)।
- একই key দিয়ে সাইন করতে `KEYSTORE_BASE64` আর `KEYSTORE_PASSWORD` secret রাখো (alias `cinedor`)।

প্রথমবার অ্যাপ খুলে **Settings**-এ সার্ভারের ঠিকানা দাও (যেমন `http://192.168.0.104:3000`) → **Save & test connection**।
ডিফল্ট ঠিকানা বদলাতে চাইলে `android/java/com/cinedor/app/ApiConfig.java` এর `DEFAULT_BASE_URL`।

### ফোল্ডার
```
backend/server.js        Node সার্ভার (কোনো dependency নেই)
backend/tmdb.js          TMDB থেকে তথ্য আনা (key সার্ভারেই থাকে)
backend/public/          মোবাইল-রেসপন্সিভ ওয়েব অ্যাপ
android/AndroidManifest.xml
android/java/com/cinedor/app/
  MainActivity           নিচের নেভ বার + ৪টা ট্যাব
  HomeFragment           carousel, continue watching, সারি
  SearchFragment / LibraryFragment / SettingsFragment
  MovieDetailActivity    টাইটেল পেজ
  PlayerActivity         ExoPlayer, প্রোগ্রেস সেভ
  Playback               কোয়ালিটি বাছাই শিট, play/resume/download
  Catalog / Store        সার্ভারের লিস্ট / ফোনে favorites-history-downloads
android/res/             layout, drawable (আইকন), values (রং, থিম, টেক্সট)
```

**নোট:** HLS (.m3u8) শুধু স্ট্রিম হয়, ডাউনলোডের জন্য MP4 রাখো।
বাসার বাইরে থেকে চালাতে চাইলে সার্ভারে HTTPS দাও (যেমন Cloudflare Tunnel) আর `APP_TOKEN` অবশ্যই চালু রাখো।
