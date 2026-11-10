# CineDor — আধুনিক অটো মুভি অ্যাপ (Android)

অ্যাপ খুললেই মুভি, সিরিজ, অ্যানিমে আর K-Drama-র সব তথ্য **TMDB** থেকে নিজে চলে আসে। কোনো বিজ্ঞাপন নেই।

## কী কী আছে

| অংশ | কী হয় |
|---|---|
| **Home** | বড় ব্যানার, জনরা চিপস, Browse by Language, Top 10 Today, Popular, দেশ অনুযায়ী New Releases, Coming Soon, Top Rated |
| **Anime / K-Drama** | আলাদা ট্যাব, নিজস্ব Top 10 আর জনরা রো |
| **মুভির পেজ** | ব্যাকড্রপ, রেটিং, বয়সসীমা, সময়, পরিচালক, অভিনেতা, সিজন ও এপিসোড, More Like This |
| **ট্রেলার** | এক চাপে YouTube-এ খোলে |
| **কোথায় দেখবে** | কোন লিগ্যাল সার্ভিসে মুভিটা আছে তা দেখায় |
| **ফ্রি পুরো মুভি** | Internet Archive-এর পাবলিক-ডোমেইন মুভি Media3 ExoPlayer-এ চলে, সব কোয়ালিটিতে ডাউনলোড হয় |
| **Profile** | My List, Continue Watching, Downloads |
| **সার্চ** | মুভি, সিরিজ, অ্যানিমে |

রো-গুলো ফোনে ক্যাশ থাকে, তাই অ্যাপ সাথে সাথে খোলে; প্রতি ঘণ্টায় নিজে রিফ্রেশ হয়।

## APK বানানো

1. রিপোর **Settings → Secrets and variables → Actions**-এ `TMDB_API_KEY` নামে secret যোগ করো (একবারই লাগবে)।
2. **Actions → Build CineDor (Java) → Run workflow**, Version দাও (প্রতিবার বাড়াতে হবে)।
3. বিল্ড শেষে **Releases**-এ `CineDor-vX.Y.Z.apk` পাবে।

secret না দিলে Run workflow-এর `tmdb_key` ঘরেও key দেওয়া যায়। key কখনো রিপোতে commit করা হয় না; বিল্ডের সময় `Keys.java` ফাইলে বসে।

ঐচ্ছিক: `KEYSTORE_BASE64` আর `KEYSTORE_PASSWORD` secret দিলে প্রতিটা আপডেট একই key দিয়ে সাইন হয়।

## ফাইল

| ফাইল | কাজ |
|---|---|
| `MainActivity` | নিচের ট্যাব, সব ফিড, Profile |
| `BrowseActivity` | See All গ্রিড (অসীম স্ক্রল) আর সার্চ |
| `DetailActivity` | মুভি/সিরিজের পেজ |
| `PlayerActivity` | Media3 ExoPlayer, অটো রিজিউম |
| `Catalog` | কোন ট্যাবে কোন রো থাকবে |
| `Tmdb`, `MovieApi` | TMDB আর Internet Archive ক্লায়েন্ট |
| `PosterAdapter`, `Ui`, `ImageLoader` | কার্ড, রং, ছবি লোড |
| `Store` | My List, Continue Watching, ক্যাশ |
| `ic_*.xml` | আইকন (বিল্ডের সময় `res/drawable`-এ যায়) |

This product uses the TMDB API but is not endorsed or certified by TMDB.
