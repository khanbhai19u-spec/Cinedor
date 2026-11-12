// Cinedor personal media server — serves ONLY the files inside ./media
// No dependencies: Node 18+ is enough.  Run:  node server.js
//
//   PORT=3000            port to listen on
//   APP_TOKEN=secret     (optional) app must send the same token
//   MEDIA_DIR=/path      (optional) default: ./media next to this file
//   PUBLIC_BASE_URL=...  (optional) e.g. https://my.domain — otherwise taken from the request
//   TMDB_API_KEY=...     (optional) TMDB key — or set it once in the web app's Settings
//
// Open http://<this-ip>:PORT in a phone browser for the web app.
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const tmdb = require('./tmdb');

const PORT = Number(process.env.PORT) || 3000;
const MEDIA = path.resolve(process.env.MEDIA_DIR || path.join(__dirname, 'media'));
const TOKEN = process.env.APP_TOKEN || '';
const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
const CACHE_MS = 30_000;
const PUBLIC_DIR = path.join(__dirname, 'public');

const VIDEO_EXT = new Set(['.mp4', '.m4v', '.mkv', '.webm', '.mov']);
const IMAGE_EXT = ['.jpg', '.jpeg', '.png', '.webp'];
const MIME = {
  '.mp4': 'video/mp4', '.m4v': 'video/mp4', '.mkv': 'video/x-matroska', '.webm': 'video/webm',
  '.mov': 'video/quicktime', '.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t',
  '.m4s': 'video/iso.segment', '.vtt': 'text/vtt', '.srt': 'application/x-subrip',
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp',
  '.json': 'application/json', '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json',
  '.ico': 'image/x-icon',
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
    folder: name,
    tmdbHint: info.tmdb || '',
    hasLocalPoster: Boolean(poster),
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
  tmdb.enrich(items).catch(() => {});
  return items;
}

/** Finds the local title matching a TMDB result (by TMDB id, else by name + year). */
function findLocal(type, id, title, year) {
  const n = tmdb.norm(title);
  for (const t of catalog()) {
    const m = tmdb.cachedMatch(t);
    if (m && m.tmdbId === id && m.mediaType === type) return t.id;
  }
  for (const t of catalog()) {
    const isTv = t.type === 'series';
    if ((type === 'tv') !== isTv) continue;
    if (tmdb.norm(t.title) === n && (!year || !t.year || String(t.year) === String(year))) return t.id;
  }
  return null;
}

function publicTitle(t, base) {
  const u = (rel) => (rel ? base + '/media/' + encPath(rel) : '');
  const q = (list) => list.map((x) => ({ label: x.label, type: x.type, size: x.size, url: u(x.rel) }));
  const m = tmdb.cachedMatch(t) || {};
  const poster = t.hasLocalPoster ? u(t.poster) : (m.poster || '');
  return {
    id: t.id, title: t.title, year: t.year || m.year || '', type: t.type, genre: t.genre || m.genre || '',
    description: t.description || m.description || '', featured: t.featured, added: t.added,
    poster, backdrop: m.backdrop || (t.backdrop && t.backdrop !== t.poster ? u(t.backdrop) : poster),
    rating: m.rating || 0, tmdbId: m.tmdbId || 0, mediaType: m.mediaType || '', trailer: m.trailer || '',
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

function readBody(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(new Error('too big')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')); } catch { reject(new Error('bad json')); } });
    req.on('error', reject);
  });
}

function serveStatic(req, res, p) {
  let rel = p === '/' ? 'index.html' : decodeURIComponent(p.slice(1));
  let file = path.resolve(PUBLIC_DIR, rel);
  if (!file.startsWith(PUBLIC_DIR + path.sep)) return false;
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) return false;
  const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': type, 'Cache-Control': type.startsWith('text/html') ? 'no-cache' : 'public, max-age=300' });
  if (req.method === 'HEAD') return res.end(), true;
  fs.createReadStream(file).pipe(res);
  return true;
}

function authorized(req, url) {
  if (!TOKEN) return true;
  const given = req.headers['x-app-token'] || url.searchParams.get('token') || '';
  const a = Buffer.from(String(given)), b = Buffer.from(TOKEN);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  const isApi = p === '/api' || p.startsWith('/api/') || p.startsWith('/media/');

  // The web app's own files are public; the API and your media need the token.
  if (!isApi) {
    if ((req.method === 'GET' || req.method === 'HEAD') && serveStatic(req, res, p)) return;
    if (!p.includes('.') && serveStatic(req, res, '/')) return; // SPA fallback
    return send(res, 404, { error: 'not found' });
  }
  if (!authorized(req, url)) return send(res, 401, { error: 'bad token' });

  const base = PUBLIC_BASE || `http://${req.headers.host}`;
  const local = () => catalog().map((t) => publicTitle(t, base));

  if (p === '/api') return send(res, 200, { name: 'Cinedor', ok: true, titles: catalog().length, tmdb: tmdb.enabled() });
  if (p === '/api/videos') return send(res, 200, { items: local() });
  if (p.startsWith('/api/videos/')) {
    const id = decodeURIComponent(p.slice('/api/videos/'.length));
    const t = catalog().find((x) => x.id === id);
    return t ? send(res, 200, publicTitle(t, base)) : send(res, 404, { error: 'not found' });
  }

  if (p === '/api/config') {
    if (req.method === 'POST') {
      const body = await readBody(req);
      const patch = {};
      if (typeof body.tmdbKey === 'string') {
        const k = body.tmdbKey.trim();
        if (k) await tmdb.testKey(k);
        patch.tmdbKey = k;
      }
      if (typeof body.language === 'string' && /^[a-z]{2}(-[A-Z]{2})?$/.test(body.language)) patch.language = body.language;
      tmdb.saveConfig(patch);
      cache.at = 0; // re-scan so new metadata is fetched
    }
    return send(res, 200, tmdb.publicConfig());
  }

  if (p === '/api/discover') {
    if (!tmdb.enabled()) return send(res, 200, { enabled: false, rows: [] });
    return send(res, 200, { enabled: true, rows: await tmdb.discover(findLocal) });
  }

  if (p === '/api/search') {
    const q = (url.searchParams.get('q') || '').trim();
    const library = q ? local().filter((t) => {
      const s = (t.title + ' ' + t.genre + ' ' + t.year + ' ' + t.type).toLowerCase();
      return q.toLowerCase().split(/\s+/).every((w) => s.includes(w));
    }) : [];
    let online = [], error = '';
    if (q && tmdb.enabled()) {
      try { online = await tmdb.search(q, findLocal); } catch (e) { error = e.message; }
    }
    return send(res, 200, { library, tmdb: online, error });
  }

  const m = p.match(/^\/api\/tmdb\/(movie|tv)\/(\d+)$/);
  if (m) {
    if (!tmdb.enabled()) return send(res, 400, { error: 'TMDB key not set' });
    return send(res, 200, await tmdb.detail(m[1], m[2], findLocal));
  }

  if (p.startsWith('/media/')) {
    let rel;
    try { rel = decodeURIComponent(p.slice('/media/'.length)); } catch { return send(res, 400, { error: 'bad path' }); }
    return serveFile(req, res, rel);
  }
  send(res, 404, { error: 'not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((e) => {
    if (!res.headersSent) send(res, /TMDB|invalid|key/i.test(e.message) ? 502 : 500, { error: e.message });
    else res.end();
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Cinedor server on port ${PORT}`);
  console.log(`Media folder: ${MEDIA}`);
  console.log(TOKEN ? 'Token: ON' : 'Token: off (set APP_TOKEN to lock it)');
  console.log(tmdb.enabled() ? 'TMDB: ON' : 'TMDB: off (add your key in the web app → Settings, or TMDB_API_KEY=...)');
  for (const list of Object.values(require('os').networkInterfaces())) {
    for (const a of list || []) if (a.family === 'IPv4' && !a.internal) console.log(`Open in your phone: http://${a.address}:${PORT}`);
  }
});
