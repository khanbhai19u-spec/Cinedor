// TMDB (themoviedb.org) metadata: posters, descriptions, genres, ratings, trailers.
// Only metadata — never video streams. The key stays on this server.
'use strict';
const fs = require('fs');
const path = require('path');

const CONFIG_FILE = path.join(__dirname, 'config.json');
const CACHE_DIR = path.join(__dirname, '.cache');
const MATCH_FILE = path.join(CACHE_DIR, 'tmdb-match.json');
const API = process.env.TMDB_API_BASE || 'https://api.themoviedb.org/3'; // override only for testing
const IMG = process.env.TMDB_IMG_BASE || 'https://image.tmdb.org/t/p/';
const TTL_MS = 30 * 60 * 1000;          // discover/detail cache
const MISS_RETRY_MS = 24 * 3600 * 1000; // retry "not found" matches after a day

// ---------- config ----------
function readConfig() {
  try { return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8')); } catch { return {}; }
}
let config = readConfig();

function key() { return (process.env.TMDB_API_KEY || config.tmdbKey || '').trim(); }
function language() { return process.env.TMDB_LANGUAGE || config.language || 'en-US'; }
function enabled() { return key().length > 0; }

function saveConfig(patch) {
  config = { ...config, ...patch };
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
  memo.clear();
  genres = null;
  matches = {};
  saveMatches();
}

function publicConfig() {
  const k = key();
  return {
    tmdbEnabled: k.length > 0,
    tmdbKeyMasked: k ? k.slice(0, 4) + '…' + k.slice(-4) : '',
    tmdbFromEnv: Boolean(process.env.TMDB_API_KEY),
    language: language(),
  };
}

// ---------- http ----------
const memo = new Map();

async function get(p, params = {}, ttl = TTL_MS) {
  const k = key();
  if (!k) throw new Error('TMDB key not set');
  const u = new URL(API + p);
  const headers = { Accept: 'application/json' };
  // v4 "read access token" (long JWT) goes in a header, v3 key in the query
  if (k.length > 40) headers.Authorization = 'Bearer ' + k;
  else u.searchParams.set('api_key', k);
  u.searchParams.set('language', language());
  for (const [a, b] of Object.entries(params)) if (b !== undefined && b !== '') u.searchParams.set(a, String(b));

  const cacheKey = u.pathname + '?' + [...u.searchParams].filter(([a]) => a !== 'api_key').map((x) => x.join('=')).join('&');
  const hit = memo.get(cacheKey);
  if (hit && Date.now() - hit.at < ttl) return hit.data;

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 12_000);
  try {
    const res = await fetch(u, { headers, signal: ctrl.signal });
    if (res.status === 401) throw new Error('TMDB key is invalid');
    if (!res.ok) throw new Error('TMDB error ' + res.status);
    const data = await res.json();
    memo.set(cacheKey, { at: Date.now(), data });
    if (memo.size > 500) memo.delete(memo.keys().next().value);
    return data;
  } finally {
    clearTimeout(timer);
  }
}

/** Checks a key without saving it. */
async function testKey(k) {
  const u = new URL(API + '/configuration');
  const headers = { Accept: 'application/json' };
  if (k.length > 40) headers.Authorization = 'Bearer ' + k; else u.searchParams.set('api_key', k);
  const res = await fetch(u, { headers });
  if (res.status === 401) throw new Error('TMDB says this key is invalid');
  if (!res.ok) throw new Error('TMDB error ' + res.status);
}

// ---------- mapping ----------
const img = (p, size) => (p ? IMG + size + p : '');
const yearOf = (r) => (r.release_date || r.first_air_date || '').slice(0, 4);
const norm = (s) => String(s || '').toLowerCase().normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, '');

let genres = null;
async function genreMap() {
  if (genres) return genres;
  const [m, t] = await Promise.all([get('/genre/movie/list', {}, 24 * 3600e3), get('/genre/tv/list', {}, 24 * 3600e3)]);
  genres = { movie: {}, tv: {} };
  for (const g of m.genres || []) genres.movie[g.id] = g.name;
  for (const g of t.genres || []) genres.tv[g.id] = g.name;
  return genres;
}

/** A TMDB result shaped like a catalog title (no qualities: info only). */
function mapItem(r, typeHint, g, findLocal) {
  const type = r.media_type || typeHint;
  if (type !== 'movie' && type !== 'tv') return null;
  const title = r.title || r.name || '';
  const year = yearOf(r);
  const genreNames = (r.genre_ids || []).map((id) => g && g[type] && g[type][id]).filter(Boolean);
  const genre = (r.genres ? r.genres.map((x) => x.name) : genreNames).slice(0, 2).join(' • ');
  return {
    id: `tmdb:${type}:${r.id}`,
    tmdbId: r.id,
    mediaType: type,
    type: type === 'tv' ? 'series' : 'movie',
    title,
    year,
    genre,
    description: r.overview || '',
    poster: img(r.poster_path, 'w500'),
    backdrop: img(r.backdrop_path, 'w1280'),
    rating: r.vote_average ? Math.round(r.vote_average * 10) / 10 : 0,
    featured: false,
    added: 0,
    qualities: [],
    episodes: [],
    inLibrary: findLocal ? findLocal(type, r.id, title, year) : null,
  };
}

async function list(p, typeHint, findLocal, params) {
  const [data, g] = await Promise.all([get(p, params), genreMap().catch(() => null)]);
  return (data.results || []).map((r) => mapItem(r, typeHint, g, findLocal)).filter((x) => x && x.poster);
}

async function discover(findLocal) {
  const rows = [
    { key: 'trending', title: 'Trending this week', path: '/trending/all/week' },
    { key: 'popular-movies', title: 'Popular movies', path: '/movie/popular', type: 'movie' },
    { key: 'popular-tv', title: 'Popular series', path: '/tv/popular', type: 'tv' },
    { key: 'top-rated', title: 'Top rated movies', path: '/movie/top_rated', type: 'movie' },
  ];
  const out = await Promise.all(rows.map(async (r) => {
    try { return { key: r.key, title: r.title, items: await list(r.path, r.type, findLocal) }; }
    catch (e) { return { key: r.key, title: r.title, items: [], error: e.message }; }
  }));
  return out.filter((r) => r.items.length);
}

async function search(q, findLocal) {
  if (!q.trim()) return [];
  return list('/search/multi', null, findLocal, { query: q, include_adult: 'false' });
}

async function detail(type, id, findLocal) {
  if (type !== 'movie' && type !== 'tv') throw new Error('bad type');
  const r = await get(`/${type}/${encodeURIComponent(id)}`, { append_to_response: 'videos,credits', include_video_language: language().slice(0, 2) + ',en,null' });
  const item = mapItem({ ...r, media_type: type }, type, null, findLocal);
  const vids = ((r.videos && r.videos.results) || []).filter((v) => v.site === 'YouTube');
  const trailer = vids.find((v) => v.type === 'Trailer' && v.official) || vids.find((v) => v.type === 'Trailer') || vids.find((v) => v.type === 'Teaser') || vids[0];
  item.trailer = trailer ? trailer.key : '';
  item.runtime = r.runtime || (r.episode_run_time && r.episode_run_time[0]) || 0;
  item.seasons = r.number_of_seasons || 0;
  item.tagline = r.tagline || '';
  item.cast = ((r.credits && r.credits.cast) || []).slice(0, 12).map((c) => ({
    name: c.name, character: c.character || '', photo: img(c.profile_path, 'w185'),
  }));
  return item;
}

// ---------- matching your own folders ----------
let matches = {};
try { matches = JSON.parse(fs.readFileSync(MATCH_FILE, 'utf8')); } catch { matches = {}; }
function saveMatches() {
  try { fs.mkdirSync(CACHE_DIR, { recursive: true }); fs.writeFileSync(MATCH_FILE, JSON.stringify(matches)); } catch {}
}

const matchKey = (t) => [t.folder, t.tmdbHint || '', t.title, t.year, t.type].join('|');

/** Cached TMDB metadata for a local title, or null. */
function cachedMatch(t) {
  const m = matches[matchKey(t)];
  return m && m.data ? m.data : null;
}

let running = false;
/** Looks up TMDB for local titles that have no cached match yet (one at a time). */
async function enrich(titles) {
  if (!enabled() || running) return;
  running = true;
  try {
    let changed = false;
    for (const t of titles) {
      const k = matchKey(t);
      const m = matches[k];
      if (m && (m.data || Date.now() - m.at < MISS_RETRY_MS)) continue;
      try {
        matches[k] = { at: Date.now(), data: await lookup(t) };
      } catch (e) {
        if (/invalid|not set/.test(e.message)) break;
        matches[k] = { at: Date.now(), data: null };
      }
      changed = true;
    }
    if (changed) saveMatches();
  } finally {
    running = false;
  }
}

async function lookup(t) {
  let type = t.type === 'series' ? 'tv' : 'movie';
  let id = null;
  // info.json: "tmdb": 603  or  "tmdb": "tv/1399"
  if (t.tmdbHint) {
    const m = String(t.tmdbHint).match(/^(?:(movie|tv)\/)?(\d+)$/);
    if (m) { if (m[1]) type = m[1]; id = m[2]; }
  }
  if (!id) {
    const params = { query: cleanTitle(t.title) };
    if (t.year) params[type === 'tv' ? 'first_air_date_year' : 'year'] = t.year;
    let res = await get(`/search/${type}`, params);
    if (!res.results || !res.results.length) res = await get(`/search/${type}`, { query: cleanTitle(t.title) });
    const best = (res.results || []).find((r) => norm(r.title || r.name) === norm(cleanTitle(t.title))) || (res.results || [])[0];
    if (!best) return null;
    id = best.id;
  }
  const d = await detail(type, id, null);
  return {
    tmdbId: d.tmdbId, mediaType: d.mediaType, genre: d.genre, description: d.description,
    poster: d.poster, backdrop: d.backdrop, rating: d.rating, year: d.year, trailer: d.trailer,
  };
}

/** "My.Movie.2019.1080p" -> "My Movie" */
function cleanTitle(s) {
  return String(s)
    .replace(/[._]+/g, ' ')
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ')
    .replace(/\b(19|20)\d{2}\b.*$/, ' ')
    .replace(/\b(2160|1080|720|480|360)p\b.*$/i, ' ')
    .replace(/\s+/g, ' ')
    .trim() || String(s);
}

module.exports = {
  enabled, publicConfig, saveConfig, testKey, discover, search, detail, enrich, cachedMatch, norm,
};
