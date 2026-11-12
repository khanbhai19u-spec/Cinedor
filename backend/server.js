// Cinedor personal media server — serves ONLY the files inside ./media
// No dependencies: Node 18+ is enough.  Run:  node server.js
//
//   PORT=3000            port to listen on
//   APP_TOKEN=secret     (optional) app must send the same token
//   MEDIA_DIR=/path      (optional) default: ./media next to this file
//   PUBLIC_BASE_URL=...  (optional) e.g. https://my.domain — otherwise taken from the request
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 3000;
const MEDIA = path.resolve(process.env.MEDIA_DIR || path.join(__dirname, 'media'));
const TOKEN = process.env.APP_TOKEN || '';
const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
const CACHE_MS = 30_000;

const VIDEO_EXT = new Set(['.mp4', '.m4v', '.mkv', '.webm', '.mov']);
const IMAGE_EXT = ['.jpg', '.jpeg', '.png', '.webp'];
const MIME = {
  '.mp4': 'video/mp4', '.m4v': 'video/mp4', '.mkv': 'video/x-matroska', '.webm': 'video/webm',
  '.mov': 'video/quicktime', '.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t',
  '.m4s': 'video/iso.segment', '.vtt': 'text/vtt', '.srt': 'application/x-subrip',
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp',
  '.json': 'application/json',
};

fs.mkdirSync(MEDIA, { recursive: true });

// ---------- catalog ----------
const slug = (s) => crypto.createHash('sha1').update(s).digest('hex').slice(0, 12);
const encPath = (rel) => rel.split(path.sep).map(encodeURIComponent).join('/');
const safeList = (dir) => { try { return fs.readdirSync(dir, { withFileTypes: true }); } catch { return []; } };
const readJson = (f) => { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return {}; } };

function findImage(dir, names) {
  for (const n of names) for (const e of IMAGE_EXT) {
    const f = path.join(dir, n + e);
    if (fs.existsSync(f)) return path.relative(MEDIA, f);
  }
  return null;
}

function qualityLabel(name) {
  const m = name.match(/(2160|1440|1080|720|576|480|360|240)p/i);
  if (m) return m[1] + 'p';
  if (/4k/i.test(name)) return '2160p';
  return 'Original';
}
const qualityRank = (l) => parseInt(l, 10) || 99999;

function findQualities(dir) {
  const out = [];
  for (const e of safeList(dir)) {
    const full = path.join(dir, e.name);
    if (e.isFile() && VIDEO_EXT.has(path.extname(e.name).toLowerCase())) {
      out.push({ label: qualityLabel(e.name), rel: path.relative(MEDIA, full), type: 'mp4', size: fs.statSync(full).size });
    } else if (e.isFile() && e.name.toLowerCase().endsWith('.m3u8')) {
      out.push({ label: 'Auto (HLS)', rel: path.relative(MEDIA, full), type: 'hls', size: 0 });
    } else if (e.isDirectory() && e.name.toLowerCase() === 'hls') {
      const lists = safeList(full).filter((x) => x.isFile() && x.name.toLowerCase().endsWith('.m3u8'));
      const pick = lists.find((x) => /master|index|playlist/i.test(x.name)) || lists[0];
      if (pick) out.push({ label: 'Auto (HLS)', rel: path.relative(MEDIA, path.join(full, pick.name)), type: 'hls', size: 0 });
    }
  }
  out.sort((a, b) => (a.type === 'hls') - (b.type === 'hls') || qualityRank(b.label) - qualityRank(a.label));
  return out;
}

function scanTitle(dir, name) {
  const info = readJson(path.join(dir, 'info.json'));
  const stat = fs.statSync(dir);
  const episodes = [];
  for (const e of safeList(dir)) {
    if (!e.isDirectory() || e.name.toLowerCase() === 'hls') continue;
    const epDir = path.join(dir, e.name);
    const q = findQualities(epDir);
    if (!q.length) continue;
    const epInfo = readJson(path.join(epDir, 'info.json'));
    episodes.push({ id: slug(name + '/' + e.name), folder: e.name, title: epInfo.title || e.name, qualities: q });
  }
  episodes.sort((a, b) => a.folder.localeCompare(b.folder, undefined, { numeric: true }));
  const qualities = findQualities(dir);
  if (!qualities.length && !episodes.length) return null;
  const poster = findImage(dir, ['poster', 'cover', 'folder', 'thumb']);
  return {
    id: slug(name),
    title: info.title || name,
    year: String(info.year || ''),
    type: info.type || (episodes.length ? 'series' : 'movie'),
    genre: info.genre || '',
    description: info.description || '',
    featured: info.featured !== false,
    added: Math.round((info.added ? Date.parse(info.added) : stat.mtimeMs) || stat.mtimeMs),
    poster,
    backdrop: findImage(dir, ['backdrop', 'fanart', 'banner']) || poster,
    qualities,
    episodes,
  };
}

let cache = { at: 0, items: [] };
function catalog() {
  if (Date.now() - cache.at < CACHE_MS) return cache.items;
  const items = [];
  for (const e of safeList(MEDIA)) {
    if (!e.isDirectory() || e.name.startsWith('.')) continue;
    try { const t = scanTitle(path.join(MEDIA, e.name), e.name); if (t) items.push(t); }
    catch (err) { console.warn('skip', e.name, err.message); }
  }
  items.sort((a, b) => b.added - a.added);
  cache = { at: Date.now(), items };
  return items;
}

function publicTitle(t, base) {
  const u = (rel) => (rel ? base + '/media/' + encPath(rel) : '');
  const q = (list) => list.map((x) => ({ label: x.label, type: x.type, size: x.size, url: u(x.rel) }));
  return {
    id: t.id, title: t.title, year: t.year, type: t.type, genre: t.genre, description: t.description,
    featured: t.featured, added: t.added, poster: u(t.poster), backdrop: u(t.backdrop),
    qualities: q(t.qualities),
    episodes: t.episodes.map((e) => ({ id: e.id, title: e.title, qualities: q(e.qualities) })),
  };
}

// ---------- http ----------
function send(res, code, body, type = 'application/json; charset=utf-8') {
  res.writeHead(code, { 'Content-Type': type, 'Cache-Control': 'no-store' });
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

function serveFile(req, res, rel) {
  const file = path.resolve(MEDIA, rel);
  if (file !== MEDIA && !file.startsWith(MEDIA + path.sep)) return send(res, 403, { error: 'forbidden' });
  let st;
  try { st = fs.statSync(file); } catch { return send(res, 404, { error: 'not found' }); }
  if (!st.isFile()) return send(res, 404, { error: 'not found' });
  const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
  const headers = { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Cache-Control': 'private, max-age=3600' };
  const range = req.headers.range && /^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
  if (range) {
    let start = range[1] === '' ? st.size - Number(range[2]) : Number(range[1]);
    let end = range[1] === '' || range[2] === '' ? st.size - 1 : Number(range[2]);
    start = Math.max(0, start); end = Math.min(end, st.size - 1);
    if (start > end) { res.writeHead(416, { 'Content-Range': `bytes */${st.size}` }); return res.end(); }
    res.writeHead(206, { ...headers, 'Content-Range': `bytes ${start}-${end}/${st.size}`, 'Content-Length': end - start + 1 });
    if (req.method === 'HEAD') return res.end();
    return fs.createReadStream(file, { start, end }).pipe(res);
  }
  res.writeHead(200, { ...headers, 'Content-Length': st.size });
  if (req.method === 'HEAD') return res.end();
  fs.createReadStream(file).pipe(res);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (TOKEN) {
    const given = req.headers['x-app-token'] || url.searchParams.get('token') || '';
    const a = Buffer.from(String(given)), b = Buffer.from(TOKEN);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return send(res, 401, { error: 'bad token' });
  }
  const base = PUBLIC_BASE || `http://${req.headers.host}`;
  const p = url.pathname;
  if (p === '/' || p === '/api') return send(res, 200, { name: 'Cinedor', ok: true, titles: catalog().length });
  if (p === '/api/videos') return send(res, 200, { items: catalog().map((t) => publicTitle(t, base)) });
  if (p.startsWith('/api/videos/')) {
    const id = decodeURIComponent(p.slice('/api/videos/'.length));
    const t = catalog().find((x) => x.id === id);
    return t ? send(res, 200, publicTitle(t, base)) : send(res, 404, { error: 'not found' });
  }
  if (p.startsWith('/media/')) {
    let rel;
    try { rel = decodeURIComponent(p.slice('/media/'.length)); } catch { return send(res, 400, { error: 'bad path' }); }
    return serveFile(req, res, rel);
  }
  send(res, 404, { error: 'not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Cinedor server on port ${PORT}`);
  console.log(`Media folder: ${MEDIA}`);
  console.log(TOKEN ? 'Token: ON' : 'Token: off (set APP_TOKEN to lock it)');
});
