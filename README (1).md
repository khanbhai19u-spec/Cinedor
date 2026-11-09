# Cinedor — পারসোনাল ভিডিও সার্ভার + Android প্লেয়ার

এটা শুধু **তোমার নিজের ভিডিও** চালানোর জন্য। সার্ভার কেবল `media` ফোল্ডারের ফাইল দেয়, বাইরের কোনো সাইট থেকে কিছু আনে না।
সাপোর্ট করে: **MP4** (360p / 720p / 1080p) আর **HLS (.m3u8)**, ExoPlayer দিয়ে ফুলস্ক্রিন প্লে, আর DownloadManager দিয়ে ডাউনলোড।

---

## ১. সার্ভার চালানো (Termux বা PC)

```bash
pkg install nodejs -y          # Termux-এ (PC-তে nodejs.org থেকে Node 18+)
cd backend
npm install
PUBLIC_BASE_URL=http://192.168.1.100:3000 npm start
```
- `192.168.1.100` জায়গায় সার্ভার যে ফোন/PC-তে চলছে তার Wi-Fi IP দাও (Termux: `ifconfig` → `wlan0` → `inet`)।
- লক করতে চাইলে: `APP_TOKEN=myPass123 PUBLIC_BASE_URL=... npm start` — তাহলে অ্যাপেও একই টোকেন দিতে হবে।
- চেক: ব্রাউজারে `http://192.168.1.100:3000/api/videos`

### ভিডিও রাখার নিয়ম
```
backend/media/
  My Trip 2026/
    info.json      {"title":"My Trip","year":"2026","description":"..."}   (ঐচ্ছিক)
    poster.jpg                                                          (ঐচ্ছিক)
    360p.mp4
    720p.mp4
    1080p.mp4
    hls/master.m3u8  (+ .ts ফাইলগুলো)                                     (ঐচ্ছিক)
```
ফাইলের নামে `360p` / `720p` / `1080p` থাকলেই কোয়ালিটি নিজে চিনে নেবে। নতুন ফোল্ডার দিলে ৩০ সেকেন্ডের মধ্যে অ্যাপে চলে আসে।

### নিজের ভিডিও থেকে কোয়ালিটি আর HLS বানানো (ffmpeg)
```bash
pkg install ffmpeg -y
# 720p আর 360p কপি
ffmpeg -i myvideo.mp4 -vf scale=-2:720 -c:v libx264 -crf 23 -preset veryfast -c:a aac -movflags +faststart 720p.mp4
ffmpeg -i myvideo.mp4 -vf scale=-2:360 -c:v libx264 -crf 26 -preset veryfast -c:a aac -movflags +faststart 360p.mp4
# HLS (.m3u8) — স্ট্রিমিং দ্রুত শুরু হয়
mkdir hls && ffmpeg -i 720p.mp4 -c copy -hls_time 6 -hls_playlist_type vod -hls_segment_filename "hls/seg_%03d.ts" hls/master.m3u8
```
`-movflags +faststart` দিলে MP4 ডাউনলোড শেষ হওয়ার আগেই প্লে শুরু হয়।

---

## ২. Android অ্যাপে যোগ করা

1. `android/java/com/cinedor/app/` এর ৫টা `.java` ফাইল তোমার প্রজেক্টের `app/src/main/java/com/cinedor/app/` এ রাখো
   (তোমার package নাম আলাদা হলে প্রতিটা ফাইলের প্রথম লাইন বদলাও)।
2. `android/res/` এর `layout/`, `values/strings.xml`, `xml/network_security_config.xml` → `app/src/main/res/` এ।
3. `build.gradle-dependencies.txt` দেখে dependencies যোগ করো।
4. `AndroidManifest-additions.xml` দেখে permission, activity আর `networkSecurityConfig` যোগ করো।
5. `ApiConfig.java` তে `BASE_URL` (সার্ভারের IP) আর দরকার হলে `APP_TOKEN` বসাও।

### কোন ফাইল কী করে
| ফাইল | কাজ |
|---|---|
| `VideoListActivity` | সার্ভারের সব ভিডিওর লিস্ট |
| `MovieDetailActivity` | টাইটেল, পোস্টার, বিবরণ; **Watch / Download** চাপলে কোয়ালিটি BottomSheet |
| `PlayerActivity` | Media3 ExoPlayer — MP4 + HLS, ফুলস্ক্রিন ল্যান্ডস্কেপ, ±১০ সেকেন্ড, ঘোরালে/ফিরে এলে আগের জায়গা থেকে চলে |
| `VideoDownloader` | DownloadManager → `Movies/Cinedor/` ফোল্ডারে, নোটিফিকেশন সহ (শুধু তোমার সার্ভারের MP4) |
| `ApiConfig` | সার্ভারের ঠিকানা, টোকেন, HTTP helper |

**নোট:** HLS (.m3u8) শুধু স্ট্রিম হয়, DownloadManager দিয়ে নামানো যায় না — ডাউনলোডের জন্য MP4 কোয়ালিটি রাখো।
অনলাইনে (বাসার বাইরে থেকে) চালাতে চাইলে সার্ভারে HTTPS দাও (যেমন Cloudflare Tunnel) আর `APP_TOKEN` অবশ্যই চালু রাখো।
