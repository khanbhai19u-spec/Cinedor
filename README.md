# Cinedor 🎬

মুভি আর সিরিজের অ্যাপ (Android)। সব তথ্য TMDB থেকে অটো আসে, Internet Archive-এর ফ্রি পাবলিক-ডোমেইন মুভি চালানো আর ডাউনলোড করা যায়, ফোনের নিজের ভিডিওও চলে। GitHub Actions দিয়ে APK বিল্ড আর রিলিজ হয়, আর অ্যাপের ভেতর থেকেই আপডেট নামানো যায়।

## একবারের সেটআপ (ফোন থেকেই করা যায়)

1. GitHub-এ নতুন repo খুলুন (যেমন `cinedor`) — **Public** রাখুন, তাহলে অ্যাপ নিজে নিজে নতুন আপডেট খুঁজে পাবে।
2. এই zip-এর ভেতরের সব ফাইল আর ফোল্ডার repo-তে আপলোড করুন (Add file → Upload files)।
   ⚠️ `.github` ফোল্ডারটাও যেন যায়। ফোনে লুকানো ফোল্ডার আপলোড না হলে: Add file → Create new file → নাম দিন `.github/workflows/release.yml` → zip-এর ওই ফাইলের লেখা পেস্ট করুন।
3. Repo → **Settings → Secrets and variables → Actions → New repository secret** — `cinedor-secrets.txt` ফাইল দেখে ৩টা secret বানান:
   - `KEYSTORE_PASSWORD`
   - `KEYSTORE_BASE64`
   - `TMDB_API_KEY` ← **আপনার TMDB API key এখানে দিন**
4. Repo → **Settings → Actions → General → Workflow permissions → "Read and write permissions"** → Save।

## APK বানানো / নতুন আপডেট দেওয়া

1. Repo → **Actions → "Build & Release Cinedor" → Run workflow**
2. Version লিখুন (প্রথমবার `1.0.0`, পরের বার `1.0.1`, `1.0.2` …) আর কী নতুন সেটা লিখুন → **Run**
3. ৫–১০ মিনিট পর **Releases**-এ `Cinedor-v1.0.0.apk` চলে আসবে → ডাউনলোড করে ইনস্টল করুন।

পরে নতুন ভার্সন রিলিজ করলে অ্যাপ খুললেই "নতুন আপডেট এসেছে!" দেখাবে → **এখনই আপডেট** চাপলে অ্যাপের ভেতরেই ডাউনলোড হয়ে ইনস্টল স্ক্রিন খুলবে। (প্রথমবার "Install unknown apps" অনুমতি চাইবে — Allow দিন।)

## ফোল্ডার

- `app/src/main/assets/www/` — অ্যাপের পুরো ডিজাইন আর ফিচার (`index.html`, `app.js`)
- `app/src/main/java/.../MainActivity.kt` — Android অংশ (প্লেয়ার ফুলস্ক্রিন, PiP, ডাউনলোড, আপডেট ইনস্টল)
- `.github/workflows/release.yml` — বিল্ড আর রিলিজ

This product uses the TMDB API but is not endorsed or certified by TMDB.
