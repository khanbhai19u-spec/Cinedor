// Cinedor Console — the admin panel for your server (served at /console).
// Everything here sits behind a password + session cookie, except the two small
// public endpoints the app uses: POST /api/feedback and GET /api/announcement.
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DATA = path.join(__dirname, 'data');
const PUBLIC = path.join(__dirname, 'public', 'console');
const ADMIN_FILE = path.join(DATA, 'admin.json');
const FEEDBACK_FILE = path.join(DATA, 'feedback.json');
const ANNOUNCE_FILE = path.join(DATA, 'announcement.json');

const COOKIE = 'cinedor_console';
const SESSION_MS = 7 * 24 * 3600 * 1000;
const VIDEO_EXT = new Set(['.mp4', '.m4v', '.mkv', '.webm', '.mov', '.m3u8', '.ts', '.m4s']);
const IMAGE_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp']);
const SUB_EXT = new Set(['.vtt', '.srt']);
const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml', '.json': 'application/json',
};
const CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: https://image.tmdb.org; " +
  "media-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

fs.mkdirSync(DATA, { recursive: true });

// ---------- small helpers ----------
const readJson = (f, d) => { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return d; } };
function writeJson(f, v) {
  const tmp = f + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(v, null, 2), { mode: 0o600 });
  fs.renameSync(tmp, f);
}
const sha = (s) => crypto.createHash('sha256').update(String(s)).digest();
const safeEq = (a, b) => crypto.timingSafeEqual(sha(a), sha(b));

/** A single folder / file name: no slashes, no dots at the start, no odd characters. */
function safeName(s) {
  s = String(s == null ? '' : s).trim();
  if (!s || s.length > 150 || s === '.' || s === '..' || s.startsWith('.')) return null;
  if (/[\/\\\0<>:"|?*\u0000-\u001f]/.test(s)) return null;
  return s;
}
const within = (base, p) => { const r = path.resolve(p); return r === base || r.startsWith(base + path.sep); };

function dirSize(dir, depth = 0) {
  let total = 0;
  let entries = [];
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return 0; }
  for (const e of entries) {
    const f = path.join(dir, e.name);
    try {
      if (e.isFile()) total += fs.statSync(f).size;
      else if (e.isDirectory() && depth < 4) total += dirSize(f, depth + 1);
    } catch {}
  }
  return total;
}

function parseCookies(req) {
  const out = {};
  for (const part of String(req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

const ip = (req) => req.socket.remoteAddress || '';
function isLoopback(req) {
  const a = ip(req);
  const direct = a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1';
  // A tunnel / reverse proxy on this machine looks like loopback — those add forwarding headers.
  const proxied = ['x-forwarded-for', 'forwarded', 'cf-connecting-ip', 'x-real-ip'].some((h) => req.headers[h]);
  return direct && !proxied;
}

module.exports = function createConsole(ctx) {
  const { MEDIA, send, readBody, serveFile, tmdb } = ctx;
  const sessions = new Map();          // sid -> expires
  const failures = new Map();          // ip -> { n, until }
  const feedbackHits = new Map();      // ip -> [timestamps]
  const started = Date.now();

  // ---------- password ----------
  const envPass = () => process.env.ADMIN_PASSWORD || '';
  const configured = () => envPass().length > 0 || fs.existsSync(ADMIN_FILE);

  function hashPassword(pw, salt = crypto.randomBytes(16).toString('hex')) {
    return { salt, hash: crypto.scryptSync(pw, salt, 64).toString('hex') };
  }
  function checkPassword(pw) {
    if (envPass()) return safeEq(pw, envPass());
    const a = readJson(ADMIN_FILE, null);
    if (!a) return false;
    const h = crypto.scryptSync(pw, a.salt, 64);
    const want = Buffer.from(a.hash, 'hex');
    return want.length === h.length && crypto.timingSafeEqual(h, want);
  }

  // ---------- sessions ----------
  function startSession(req, res) {
    const sid = crypto.randomBytes(32).toString('hex');
    sessions.set(sid, Date.now() + SESSION_MS);
    const secure = req.socket.encrypted || req.headers['x-forwarded-proto'] === 'https';
    const cookie = `${COOKIE}=${sid}; HttpOnly; SameSite=Strict; Path=/console; Max-Age=${SESSION_MS / 1000}${secure ? '; Secure' : ''}`;
    res.setHeader('Set-Cookie', cookie);
  }
  function sessionId(req) { return parseCookies(req)[COOKIE] || ''; }
  function loggedIn(req) {
    const sid = sessionId(req);
    const exp = sessions.get(sid);
    if (!exp) return false;
    if (exp < Date.now()) { sessions.delete(sid); return false; }
    return true;
  }
  setInterval(() => { const now = Date.now(); for (const [k, v] of sessions) if (v < now) sessions.delete(k); }, 3600e3).unref();

  function lockedOut(req) {
    const f = failures.get(ip(req));
    return f && f.until > Date.now() ? Math.ceil((f.until - Date.now()) / 1000) : 0;
  }
  function recordFailure(req) {
    const f = failures.get(ip(req)) || { n: 0, until: 0 };
    f.n += 1;
    if (f.n >= 5) f.until = Date.now() + Math.min(15 * 60e3, 30e3 * 2 ** (f.n - 5));
    failures.set(ip(req), f);
  }

  // ---------- feedback & announcement ----------
  const feedback = () => readJson(FEEDBACK_FILE, []);

  /** Public: the app posts here. Returns [status, body]. */
  function addFeedback(req, body) {
    const now = Date.now();
    const hits = (feedbackHits.get(ip(req)) || []).filter((t) => now - t < 3600e3);
    if (hits.length >= 5) return [429, { error: 'Too many messages — try again later' }];
    const text = String(body.text || '').trim();
    if (text.length < 5) return [400, { error: 'Please write a little more' }];
    if (text.length > 2000) return [400, { error: 'Message is too long' }];
    hits.push(now); feedbackHits.set(ip(req), hits);
    const list = feedback();
    list.unshift({
      id: crypto.randomBytes(6).toString('hex'),
      time: now,
      text,
      contact: String(body.contact || '').trim().slice(0, 120),
      app: String(body.app || '').trim().slice(0, 40),
      read: false,
    });
    writeJson(FEEDBACK_FILE, list.slice(0, 500));
    return [201, { ok: true }];
  }

  function publicAnnouncement() {
    const a = readJson(ANNOUNCE_FILE, null);
    if (!a || !a.enabled || !(a.title || a.text)) return { active: false };
    return { active: true, id: a.id, title: a.title || '', text: a.text || '', link: a.link || '', linkLabel: a.linkLabel || '' };
  }

  // ---------- library ----------
  function listFiles(dir, rel = '', depth = 0) {
    const out = [];
    let entries = [];
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
    for (const e of entries) {
      if (e.name.startsWith('.')) continue;
      const full = path.join(dir, e.name);
      const r = rel ? rel + '/' + e.name : e.name;
      if (e.isDirectory()) {
        if (depth < 2) out.push(...listFiles(full, r, depth + 1));
      } else if (e.isFile()) {
        const ext = path.extname(e.name).toLowerCase();
        const kind = VIDEO_EXT.has(ext) ? 'video' : IMAGE_EXT.has(ext) ? 'image' : SUB_EXT.has(ext) ? 'subtitle' : e.name === 'info.json' ? 'info' : 'other';
        let size = 0;
        try { size = fs.statSync(full).size; } catch {}
        out.push({ path: r, name: e.name, kind, size });
      }
    }
    return out;
  }

  function titleSummary(name) {
    const dir = path.join(MEDIA, name);
    const info = readJson(path.join(dir, 'info.json'), {});
    const files = listFiles(dir);
    const videos = files.filter((f) => f.kind === 'video');
    const poster = files.find((f) => f.kind === 'image' && /^(poster|cover|folder|thumb)\./i.test(f.name));
    const hasEpisodes = files.some((f) => f.path.includes('/') && f.kind === 'video');
    return {
      folder: name,
      info,
      title: info.title || name,
      type: info.type || (hasEpisodes ? 'series' : 'movie'),
      year: info.year ? String(info.year) : '',
      featured: info.featured !== false,
      files: files.length,
      videos: videos.length,
      size: files.reduce((a, f) => a + f.size, 0),
      poster: poster ? poster.path : '',
      tmdb: info.tmdb ? String(info.tmdb) : '',
    };
  }

  function allTitles() {
    let entries = [];
    try { entries = fs.readdirSync(MEDIA, { withFileTypes: true }); } catch {}
    return entries.filter((e) => e.isDirectory() && !e.name.startsWith('.')).map((e) => titleSummary(e.name))
      .sort((a, b) => a.title.localeCompare(b.title, undefined, { numeric: true }));
  }

  function titleDir(name) {
    const n = safeName(name);
    if (!n) return null;
    const dir = path.join(MEDIA, n);
    if (!within(MEDIA, dir)) return null;
    return dir;
  }

  /** Moves something into media/.trash instead of deleting it for good. */
  function trash(full, label) {
    const dir = path.join(MEDIA, '.trash');
    fs.mkdirSync(dir, { recursive: true });
    const dest = path.join(dir, `${Date.now()}-${label.replace(/[^\p{L}\p{N}._-]+/gu, '_')}`.slice(0, 200));
    fs.renameSync(full, dest);
    return path.basename(dest);
  }

  const INFO_KEYS = ['title', 'year', 'type', 'genre', 'description', 'featured', 'tmdb'];
  function cleanInfo(body, old) {
    const info = { ...old };
    for (const k of INFO_KEYS) {
      if (!(k in body)) continue;
      let v = body[k];
      if (k === 'featured') { info.featured = Boolean(v); continue; }
      v = String(v == null ? '' : v).trim();
      if (k === 'type' && v && !['movie', 'series'].includes(v)) throw httpError(400, 'Type must be movie or series');
      if (k === 'year' && v && !/^\d{4}$/.test(v)) throw httpError(400, 'Year must look like 2024');
      if (k === 'tmdb' && v && !/^((movie|tv)\/)?\d+$/.test(v)) throw httpError(400, 'TMDB id looks like 603 or tv/1399');
      if (k === 'description' && v.length > 3000) throw httpError(400, 'Description is too long');
      if ((k === 'title' || k === 'genre') && v.length > 200) throw httpError(400, k + ' is too long');
      if (v === '') delete info[k]; else info[k] = k === 'year' ? Number(v) : v;
    }
    return info;
  }

  function httpError(status, message) { const e = new Error(message); e.status = status; return e; }

  // ---------- upload ----------
  function upload(req, res, url) {
    const folder = safeName(url.searchParams.get('folder'));
    const sub = url.searchParams.get('sub') ? safeName(url.searchParams.get('sub')) : '';
    const name = safeName(url.searchParams.get('name'));
    if (!folder || sub === null || !name) return send(res, 400, { error: 'Bad folder or file name' });
    const ext = path.extname(name).toLowerCase();
    if (!VIDEO_EXT.has(ext) && !IMAGE_EXT.has(ext) && !SUB_EXT.has(ext)) {
      return send(res, 400, { error: 'Only video, image and subtitle files can be uploaded' });
    }
    const base = path.join(MEDIA, folder);
    if (!within(MEDIA, base) || !fs.existsSync(base)) return send(res, 404, { error: 'Create the title folder first' });
    const dir = sub ? path.join(base, sub) : base;
    fs.mkdirSync(dir, { recursive: true });
    // never follow a symlink out of the media folder
    try {
      if (!within(fs.realpathSync(MEDIA), fs.realpathSync(dir))) return send(res, 403, { error: 'forbidden' });
    } catch { return send(res, 500, { error: 'Folder not writable' }); }

    const dest = path.join(dir, name);
    const tmp = dest + '.part';
    const ws = fs.createWriteStream(tmp, { flags: 'w' });
    let done = false;
    const cleanup = () => { try { fs.unlinkSync(tmp); } catch {} };
    req.on('aborted', () => { if (!done) { ws.destroy(); cleanup(); } });
    ws.on('error', (e) => { if (!done) { done = true; cleanup(); send(res, 500, { error: 'Could not write the file: ' + e.code }); } });
    ws.on('finish', () => {
      if (done) return;
      if (!req.complete) { cleanup(); return; }
      done = true;
      try {
        // replacing poster.jpg with poster.png (etc.): move the old variants away
        const stem = path.basename(name, ext).toLowerCase();
        if (IMAGE_EXT.has(ext) && ['poster', 'backdrop'].includes(stem)) {
          for (const e of fs.readdirSync(dir)) {
            if (e !== name && path.basename(e, path.extname(e)).toLowerCase() === stem && IMAGE_EXT.has(path.extname(e).toLowerCase())) trash(path.join(dir, e), folder + '-' + e);
          }
        }
        fs.renameSync(tmp, dest);
        ctx.invalidate();
        send(res, 201, { ok: true, size: fs.statSync(dest).size });
      } catch (e) { cleanup(); send(res, 500, { error: 'Could not save the file' }); }
    });
    req.pipe(ws);
  }

  // ---------- routes ----------
  function serveAsset(req, res, p) {
    const rel = p === '/console' || p === '/console/' ? 'index.html' : p.slice('/console/'.length);
    const file = path.resolve(PUBLIC, rel);
    if (!within(PUBLIC, file) || !fs.existsSync(file) || !fs.statSync(file).isFile()) return send(res, 404, { error: 'not found' });
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Content-Security-Policy': CSP, 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
      'Cache-Control': 'no-cache',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(file).pipe(res);
  }

  async function api(req, res, url) {
    const p = url.pathname.slice('/console/api'.length) || '/';
    const method = req.method;

    // Every write needs our custom header: a web page on another site can't send it.
    if (method !== 'GET' && method !== 'HEAD' && req.headers['x-console'] !== '1') return send(res, 403, { error: 'forbidden' });

    if (p === '/state' && method === 'GET') {
      return send(res, 200, { configured: configured(), setupAllowed: !configured() && isLoopback(req), loggedIn: loggedIn(req), envPassword: Boolean(envPass()) });
    }
    if (p === '/setup' && method === 'POST') {
      if (configured()) return send(res, 400, { error: 'A password is already set' });
      if (!isLoopback(req)) return send(res, 403, { error: 'First-time setup is only allowed on the device running the server (open http://localhost:PORT/console there), or start the server with ADMIN_PASSWORD=...' });
      const body = await readBody(req);
      const pw = String(body.password || '');
      if (pw.length < 8) return send(res, 400, { error: 'Use at least 8 characters' });
      writeJson(ADMIN_FILE, hashPassword(pw));
      startSession(req, res);
      return send(res, 200, { ok: true });
    }
    if (p === '/login' && method === 'POST') {
      const wait = lockedOut(req);
      if (wait) return send(res, 429, { error: `Too many attempts — wait ${wait}s` });
      if (!configured()) return send(res, 400, { error: 'No password set yet' });
      const body = await readBody(req);
      if (!checkPassword(String(body.password || ''))) { recordFailure(req); return send(res, 401, { error: 'Wrong password' }); }
      failures.delete(ip(req));
      startSession(req, res);
      return send(res, 200, { ok: true });
    }
    if (p === '/logout' && method === 'POST') {
      sessions.delete(sessionId(req));
      res.setHeader('Set-Cookie', `${COOKIE}=; HttpOnly; SameSite=Strict; Path=/console; Max-Age=0`);
      return send(res, 200, { ok: true });
    }

    if (!loggedIn(req)) return send(res, 401, { error: 'Please sign in' });

    // ----- dashboard -----
    if (p === '/stats' && method === 'GET') {
      const titles = allTitles();
      let free = null;
      try { const s = fs.statfsSync(MEDIA); free = Number(s.bavail) * Number(s.bsize); } catch {}
      const fb = feedback();
      return send(res, 200, {
        titles: titles.length,
        movies: titles.filter((t) => t.type !== 'series').length,
        series: titles.filter((t) => t.type === 'series').length,
        size: titles.reduce((a, t) => a + t.size, 0),
        freeDisk: free,
        noPoster: titles.filter((t) => !t.poster).length,
        noVideo: titles.filter((t) => !t.videos).length,
        feedback: fb.length, unread: fb.filter((f) => !f.read).length,
        tmdb: tmdb.enabled(),
        uptime: Math.round((Date.now() - started) / 1000),
        node: process.version,
        announcement: publicAnnouncement().active,
      });
    }
    if (p === '/rescan' && method === 'POST') { ctx.invalidate(); return send(res, 200, { ok: true, titles: ctx.catalog().length }); }

    // ----- library -----
    if (p === '/titles' && method === 'GET') return send(res, 200, { items: allTitles() });
    if (p === '/titles' && method === 'POST') {
      const body = await readBody(req);
      const name = safeName(body.folder);
      if (!name) return send(res, 400, { error: 'Use a simple folder name (no / \\ : * ? " < > |)' });
      const dir = path.join(MEDIA, name);
      if (fs.existsSync(dir)) return send(res, 409, { error: 'That folder already exists' });
      fs.mkdirSync(dir);
      const info = {};
      if (body.title) info.title = String(body.title).trim().slice(0, 200);
      if (body.type === 'series') info.type = 'series';
      if (Object.keys(info).length) writeJson(path.join(dir, 'info.json'), info);
      ctx.invalidate();
      return send(res, 201, { ok: true, folder: name });
    }
    let m = p.match(/^\/titles\/([^/]+)$/);
    if (m) {
      const dir = titleDir(decodeURIComponent(m[1]));
      if (!dir || !fs.existsSync(dir)) return send(res, 404, { error: 'not found' });
      const folder = path.basename(dir);
      if (method === 'GET') return send(res, 200, { ...titleSummary(folder), list: listFiles(dir) });
      if (method === 'DELETE') {
        const where = trash(dir, folder);
        ctx.invalidate();
        return send(res, 200, { ok: true, trash: where });
      }
    }
    m = p.match(/^\/titles\/([^/]+)\/info$/);
    if (m && method === 'PUT') {
      const dir = titleDir(decodeURIComponent(m[1]));
      if (!dir || !fs.existsSync(dir)) return send(res, 404, { error: 'not found' });
      const file = path.join(dir, 'info.json');
      const info = cleanInfo(await readBody(req), readJson(file, {}));
      if (Object.keys(info).length) writeJson(file, info); else { try { fs.unlinkSync(file); } catch {} }
      ctx.invalidate();
      return send(res, 200, { ok: true, info });
    }
    if (p === '/upload' && method === 'PUT') return upload(req, res, url);
    if (p === '/file' && method === 'DELETE') {
      const folder = safeName(url.searchParams.get('folder'));
      const rel = String(url.searchParams.get('path') || '').split('/');
      if (!folder || !rel.length || rel.length > 3 || rel.some((x) => !safeName(x))) return send(res, 400, { error: 'Bad path' });
      const full = path.join(MEDIA, folder, ...rel);
      if (!within(path.join(MEDIA, folder), full) || !fs.existsSync(full)) return send(res, 404, { error: 'not found' });
      trash(full, folder + '-' + rel.join('-'));
      ctx.invalidate();
      return send(res, 200, { ok: true });
    }
    if (p === '/file' && method === 'GET') {
      const folder = safeName(url.searchParams.get('folder'));
      const rel = String(url.searchParams.get('path') || '').split('/');
      if (!folder || rel.length > 3 || rel.some((x) => !safeName(x))) return send(res, 400, { error: 'Bad path' });
      return serveFile(req, res, path.join(folder, ...rel));
    }

    // ----- feedback -----
    if (p === '/feedback' && method === 'GET') return send(res, 200, { items: feedback() });
    m = p.match(/^\/feedback\/([a-f0-9]+)$/);
    if (m) {
      const list = feedback();
      const i = list.findIndex((f) => f.id === m[1]);
      if (i < 0) return send(res, 404, { error: 'not found' });
      if (method === 'DELETE') list.splice(i, 1);
      else if (method === 'PUT') list[i].read = Boolean((await readBody(req)).read);
      else return send(res, 405, { error: 'method not allowed' });
      writeJson(FEEDBACK_FILE, list);
      return send(res, 200, { ok: true });
    }
    if (p === '/feedback' && method === 'DELETE') { writeJson(FEEDBACK_FILE, []); return send(res, 200, { ok: true }); }

    // ----- announcement -----
    if (p === '/announcement' && method === 'GET') return send(res, 200, readJson(ANNOUNCE_FILE, { enabled: false, title: '', text: '', link: '', linkLabel: '' }));
    if (p === '/announcement' && method === 'PUT') {
      const b = await readBody(req);
      const a = {
        enabled: Boolean(b.enabled),
        title: String(b.title || '').trim().slice(0, 100),
        text: String(b.text || '').trim().slice(0, 1000),
        link: String(b.link || '').trim().slice(0, 300),
        linkLabel: String(b.linkLabel || '').trim().slice(0, 30),
      };
      if (a.link && !/^https?:\/\//i.test(a.link)) return send(res, 400, { error: 'Link must start with http:// or https://' });
      a.id = String(Date.now());           // a new id makes the app show it again
      a.updated = Date.now();
      writeJson(ANNOUNCE_FILE, a);
      return send(res, 200, a);
    }

    // ----- settings -----
    if (p === '/settings' && method === 'GET') {
      return send(res, 200, {
        tmdb: tmdb.publicConfig(),
        appToken: Boolean(process.env.APP_TOKEN),
        publicBase: process.env.PUBLIC_BASE_URL || '',
        mediaDir: MEDIA,
        port: ctx.PORT,
        passwordFromEnv: Boolean(envPass()),
      });
    }
    if (p === '/settings' && method === 'POST') {
      const b = await readBody(req);
      const patch = {};
      if (typeof b.tmdbKey === 'string') {
        const k = b.tmdbKey.trim();
        if (k) {
          try { await tmdb.testKey(k); } catch (e) { return send(res, 400, { error: e.message }); }
        }
        patch.tmdbKey = k;
      }
      if (typeof b.language === 'string' && /^[a-z]{2}(-[A-Z]{2})?$/.test(b.language)) patch.language = b.language;
      tmdb.saveConfig(patch);
      ctx.invalidate();
      return send(res, 200, tmdb.publicConfig());
    }
    if (p === '/password' && method === 'POST') {
      if (envPass()) return send(res, 400, { error: 'The password comes from ADMIN_PASSWORD — change it where you start the server' });
      const b = await readBody(req);
      if (!checkPassword(String(b.current || ''))) return send(res, 401, { error: 'Current password is wrong' });
      const next = String(b.next || '');
      if (next.length < 8) return send(res, 400, { error: 'Use at least 8 characters' });
      writeJson(ADMIN_FILE, hashPassword(next));
      sessions.clear();
      startSession(req, res);
      return send(res, 200, { ok: true });
    }

    return send(res, 404, { error: 'not found' });
  }

  async function handle(req, res, url) {
    const p = url.pathname;
    if (p === '/console') { res.writeHead(302, { Location: '/console/' }); return res.end(); }
    if (p === '/console/api' || p.startsWith('/console/api/')) {
      try { return await api(req, res, url); }
      catch (e) {
        if (!res.headersSent) return send(res, e.status || (e.message === 'bad json' || e.message === 'too big' ? 400 : 500), { error: e.message || 'error' });
        return res.end();
      }
    }
    if (req.method !== 'GET' && req.method !== 'HEAD') return send(res, 405, { error: 'method not allowed' });
    return serveAsset(req, res, p);
  }

  return { handle, addFeedback, publicAnnouncement };
};
