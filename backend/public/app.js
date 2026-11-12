/* Cinedor web app — talks only to this server. TMDB data arrives through the server. */
(() => {
  'use strict';

  // ---------- icons ----------
  const P = {
    play: 'M8 5v14l11-7z',
    refresh: 'M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
    history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.95 8.95 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
    back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
    bookmark: 'M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z',
    bookmarkO: 'M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z',
    download: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
    film: 'M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z',
    search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    trailer: 'M10 16.5l6-4.5-6-4.5v9zM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
    close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  };
  const icon = (n) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${P[n]}"/></svg>`;

  // ---------- helpers ----------
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmt = (ms) => {
    const s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
    return h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${m}:${String(x).padStart(2, '0')}`;
  };
  const size = (b) => (b >= 2 ** 30 ? (b / 2 ** 30).toFixed(1) + ' GB' : b >= 2 ** 20 ? Math.round(b / 2 ** 20) + ' MB' : Math.round(b / 1024) + ' KB');
  const meta = (v) => [v.year, v.type === 'series' ? 'SERIES' : 'MOVIE'].filter(Boolean).join('  •  ');
  const portrait = (v) => v.poster || v.backdrop || '';
  const wide = (v) => v.backdrop || v.poster || '';
  let toastTimer;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg; t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2200);
  }

  // ---------- storage (this browser) ----------
  const ls = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
  };
  const token = () => ls.get('cinedor.token', '');
  const Store = {
    fav: () => ls.get('cinedor.fav', []),
    hist: () => ls.get('cinedor.hist', []),
    dl: () => ls.get('cinedor.dl', []),
    isFav: (id) => Store.fav().some((e) => e.video.id === id),
    toggleFav(v) {
      const list = Store.fav(), rest = list.filter((e) => e.video.id !== v.id);
      const on = rest.length === list.length;
      ls.set('cinedor.fav', on ? [{ video: slim(v), time: Date.now() }, ...rest] : rest);
      return on;
    },
    last: (id) => Store.hist().find((e) => e.video.id === id),
    unfinished: (e) => e && e.pos > 5000 && (!e.dur || e.pos < e.dur * 0.95),
    cont: () => Store.hist().filter((e) => Store.unfinished(e)),
    save(v, ep, q, pos, dur) {
      const rest = Store.hist().filter((e) => e.video.id !== v.id);
      ls.set('cinedor.hist', [{ video: slim(v), ep: ep ? ep.id : '', epTitle: ep ? ep.title : '', url: q.url, label: q.label, type: q.type, pos, dur, time: Date.now() }, ...rest].slice(0, 100));
    },
    removeHist: (id) => ls.set('cinedor.hist', Store.hist().filter((e) => e.video.id !== id)),
    addDl(v, ep, q) { ls.set('cinedor.dl', [{ video: slim(v), epTitle: ep ? ep.title : '', label: q.label, url: q.url, time: Date.now() }, ...Store.dl()].slice(0, 300)); },
    removeDl: (t) => ls.set('cinedor.dl', Store.dl().filter((e) => e.time !== t)),
  };
  const slim = (v) => ({ id: v.id, title: v.title, year: v.year, type: v.type, genre: v.genre, poster: v.poster, backdrop: v.backdrop, rating: v.rating || 0, mediaType: v.mediaType, tmdbId: v.tmdbId });

  // ---------- api ----------
  class AuthError extends Error {}
  async function api(path, opts = {}) {
    const headers = { Accept: 'application/json', ...(opts.body ? { 'Content-Type': 'application/json' } : {}) };
    if (token()) headers['X-App-Token'] = token();
    const res = await fetch(path, { ...opts, headers });
    if (res.status === 401) throw new AuthError('This server is locked — enter the token in Settings.');
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Server error ' + res.status);
    return data;
  }
  /** Adds the token to media urls (a <video> can't send headers). */
  const withToken = (u) => {
    if (!u || !token()) return u;
    try { const x = new URL(u, location.href); if (x.origin !== location.origin) return u; x.searchParams.set('token', token()); return x.toString(); } catch { return u; }
  };

  const state = { items: null, error: '', discover: null, discoverAt: 0, search: '', libTab: 'fav', config: null };
  async function loadCatalog(force) {
    if (state.items && !force) return state.items;
    try { state.items = (await api('/api/videos')).items || []; state.error = ''; }
    catch (e) { state.items = state.items || []; state.error = e.message; if (e instanceof AuthError) state.auth = true; }
    return state.items;
  }
  async function loadDiscover(force) {
    if (state.discover && !force && Date.now() - state.discoverAt < 10 * 60e3) return state.discover;
    try { state.discover = await api('/api/discover'); state.discoverAt = Date.now(); }
    catch { state.discover = state.discover || { enabled: false, rows: [] }; }
    return state.discover;
  }
  const findLocal = (id) => (state.items || []).find((v) => v.id === id);

  // ---------- cards ----------
  function card(v, o = {}) {
    const href = o.href || (String(v.id).startsWith('tmdb:') ? `#/tmdb/${v.mediaType}/${v.tmdbId}` : `#/title/${encodeURIComponent(v.id)}`);
    const img = portrait(v);
    return `<a class="card" href="${esc(href)}" ${o.data || ''}>
      <div class="poster">
        ${img ? `<img src="${esc(withToken(img))}" alt="" loading="lazy">` : `<div class="fallback">${esc(v.title)}</div>`}
        ${o.rating && v.rating ? `<span class="rate"><span class="star">★</span> ${v.rating.toFixed(1)}</span>` : ''}
        ${o.own ? '<span class="own">Yours</span>' : ''}
        ${o.play ? `<span class="play">${icon('play')}</span>` : ''}
        ${o.progress != null ? `<span class="bar"><i style="width:${Math.max(2, Math.round(o.progress * 100))}%"></i></span>` : ''}
      </div>
      ${o.noTitle ? '' : `<div class="card-title">${esc(v.title)}</div><div class="card-sub">${esc(o.sub ?? meta(v).replace('  •  ', ' • '))}</div>`}
    </a>`;
  }
  const row = (title, items, o = {}) => items.length ? `<h2 class="section-title">${esc(title)}${o.tag ? ` <small>${esc(o.tag)}</small>` : ''}</h2><div class="row">${items.map((v) => card(v, o.card ? o.card(v) : {})).join('')}</div>` : '';
  const empty = (ic, title, sub) => `<div class="empty">${icon(ic)}<h3>${esc(title)}</h3><p>${esc(sub)}</p></div>`;

  // ---------- views ----------
  const view = () => $('#view');
  let cleanup = [];
  const onCleanup = (fn) => cleanup.push(fn);

  async function Home() {
    view().innerHTML = `<header class="top"><h1>Cinedor</h1><button class="icon-btn" id="refresh" aria-label="Refresh">${icon('refresh')}</button></header>
      <div id="home"><div class="spinner"></div></div>`;
    $('#refresh').onclick = async (e) => {
      e.currentTarget.classList.toggle('spin');
      await Promise.all([loadCatalog(true), loadDiscover(true)]);
      renderHome();
    };
    await loadCatalog();
    renderHome();
    loadDiscover().then(() => { if (location.hash.replace(/^#\/?/, '') === '' && $('#home')) renderHome(); });
  }

  function renderHome() {
    const box = $('#home'); if (!box) return;
    const items = state.items || [];
    const disc = state.discover && state.discover.rows ? state.discover.rows : [];
    let hero = items.filter((v) => v.featured !== false).slice(0, 10);
    let heroIsTmdb = false;
    if (!hero.length && disc.length) { hero = disc[0].items.slice(0, 10); heroIsTmdb = true; }

    let html = '';
    if (hero.length) {
      html += `<div class="hero" id="hero">${hero.map((v, i) => {
        const last = Store.last(v.id);
        const label = heroIsTmdb && !v.inLibrary ? 'Details' : Store.unfinished(last) ? 'Resume' : 'Play';
        return `<article class="hero-card${i === 0 ? ' active' : ''}" data-i="${i}">
          <img class="tall" src="${esc(withToken(portrait(v)))}" alt="" ${i > 1 ? 'loading="lazy"' : ''}>
          <img class="wide" src="${esc(withToken(wide(v)))}" alt="" loading="lazy">
          <div class="hero-info">
            ${v.genre ? `<div class="hero-genre">${esc(v.genre)}</div>` : ''}
            <h2 class="hero-title">${esc(v.title)}</h2>
            <div class="hero-meta">${esc(meta(v))}${v.rating ? `  •  <span class="star">★</span> ${v.rating.toFixed(1)}` : ''}</div>
            <button class="btn-play" data-play="${i}">${icon('play')}${label}</button>
          </div></article>`;
      }).join('')}</div><div class="dots" id="dots">${hero.length > 1 ? hero.map((_, i) => `<i class="${i === 0 ? 'on' : ''}"></i>`).join('') : ''}</div>`;
    } else if (state.error) {
      html += `<div class="message">Can't reach the server: ${esc(state.error)}<br><br><a class="btn" style="max-width:260px;margin:auto" href="#/settings">Open Settings</a></div>`;
    } else if (!disc.length) {
      html += `<div class="message">No videos yet. Put folders inside the server's <b>media</b> folder, then tap refresh.<br><br>Add your TMDB key in <a href="#/settings" style="color:var(--accent)">Settings</a> to see trending titles.</div>`;
    }

    const cont = Store.cont();
    if (cont.length) {
      html += `<section class="panel"><div class="panel-head">${icon('history')}<span>Continue watching</span><span class="badge">${cont.length}</span></div>
        <div class="row">${cont.map((e, i) => card(e.video, { href: '#/resume/' + i, play: true, progress: e.dur ? e.pos / e.dur : 0, noTitle: true })).join('')}</div></section>`;
    }
    if (items.length) {
      html += row('Recently added', items.slice(0, 20), { tag: 'YOUR LIBRARY' });
      const movies = items.filter((v) => v.type !== 'series'), series = items.filter((v) => v.type === 'series');
      if (movies.length && series.length) { html += row('Your movies', movies); html += row('Your series', series); }
    }
    for (const r of disc) {
      html += row(r.title, r.items, { tag: 'TMDB', card: (v) => ({ rating: true, own: Boolean(v.inLibrary) }) });
    }
    if (disc.length) html += `<p class="about">Movie & TV info and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.</p>`;
    box.innerHTML = html;

    box.querySelectorAll('[data-play]').forEach((b) => b.addEventListener('click', (e) => {
      e.stopPropagation();
      const v = hero[+b.dataset.play];
      if (String(v.id).startsWith('tmdb:')) {
        if (v.inLibrary) { const lv = findLocal(v.inLibrary); if (lv) return playDefault(lv); }
        location.hash = `#/tmdb/${v.mediaType}/${v.tmdbId}`;
      } else playDefault(v);
    }));
    box.querySelectorAll('.hero-card').forEach((c) => c.addEventListener('click', () => {
      const v = hero[+c.dataset.i];
      location.hash = String(v.id).startsWith('tmdb:') ? `#/tmdb/${v.mediaType}/${v.tmdbId}` : `#/title/${encodeURIComponent(v.id)}`;
    }));
    setupHero();
  }

  function setupHero() {
    const hero = $('#hero'); if (!hero) return;
    const cards = [...hero.children], dots = [...$('#dots').children];
    let current = 0, timer;
    const center = () => {
      const mid = hero.scrollLeft + hero.clientWidth / 2;
      let best = 0, bd = Infinity;
      cards.forEach((c, i) => { const d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid); if (d < bd) { bd = d; best = i; } });
      return best;
    };
    const mark = (i) => { current = i; cards.forEach((c, j) => c.classList.toggle('active', j === i)); dots.forEach((d, j) => d.classList.toggle('on', j === i)); };
    const go = (i) => { const c = cards[i]; hero.scrollTo({ left: c.offsetLeft - (hero.clientWidth - c.offsetWidth) / 2, behavior: 'smooth' }); };
    let raf;
    hero.addEventListener('scroll', () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(() => mark(center())); }, { passive: true });
    const auto = () => { clearInterval(timer); if (cards.length > 1) timer = setInterval(() => { if (!document.hidden) go((current + 1) % cards.length); }, 6000); };
    hero.addEventListener('pointerdown', auto, { passive: true });
    hero.addEventListener('touchstart', auto, { passive: true });
    auto();
    onCleanup(() => clearInterval(timer));
  }

  async function Search() {
    view().innerHTML = `<h1 class="page-title">Search</h1>
      <label class="search-box">${icon('search')}<input id="q" type="search" placeholder="Search movies, series…" autocomplete="off" enterkeyhint="search" value="${esc(state.search)}"></label>
      <div id="results"></div>`;
    const q = $('#q');
    let timer, seq = 0;
    const run = async () => {
      const text = q.value.trim(); state.search = q.value;
      const out = $('#results');
      if (!text) { out.innerHTML = empty('film', 'Search Cinedor', 'Your own videos — plus movie & series info from TMDB.'); return; }
      const my = ++seq;
      out.innerHTML = '<div class="spinner"></div>';
      let data;
      try { data = await api('/api/search?q=' + encodeURIComponent(text)); }
      catch (e) { if (my === seq) out.innerHTML = empty('search', 'Search failed', e.message); return; }
      if (my !== seq) return;
      let html = '';
      if (data.library.length) html += `<h2 class="section-title">In your library</h2><div class="grid">${data.library.map((v) => card(v)).join('')}</div>`;
      if (data.tmdb.length) html += `<h2 class="section-title">On TMDB <small>INFO & TRAILERS</small></h2><div class="grid">${data.tmdb.map((v) => card(v, { rating: true, own: Boolean(v.inLibrary) })).join('')}</div>`;
      out.innerHTML = html || empty('search', `Nothing matches “${text}”`, data.error || 'Try another word.');
    };
    q.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 300); });
    q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { q.blur(); clearTimeout(timer); run(); } });
    run();
    if (!state.search) setTimeout(() => q.focus({ preventScroll: true }), 50);
  }

  function Library() {
    const tabs = [['fav', 'Favorites', Store.fav()], ['hist', 'History', Store.hist()], ['dl', 'Downloads', Store.dl()]];
    const cur = tabs.find((t) => t[0] === state.libTab) || tabs[0];
    const list = cur[2];
    let body;
    if (!list.length) {
      body = cur[0] === 'fav' ? empty('bookmarkO', 'No favorites yet', 'Tap the bookmark on any title to keep it here.')
        : cur[0] === 'hist' ? empty('history', 'Nothing watched yet', 'Titles you play will show up here.')
        : empty('download', 'No downloads', 'Downloads you start from a title page are listed here.');
    } else {
      body = `<div class="grid">${list.map((e, i) => {
        const v = e.video;
        if (cur[0] === 'fav') return card(v, { data: `data-i="${i}"`, rating: true });
        if (cur[0] === 'hist') return card(v, {
          data: `data-i="${i}"`, href: Store.unfinished(e) ? '#/resume-h/' + i : undefined, progress: e.dur ? e.pos / e.dur : null,
          sub: (e.epTitle ? e.epTitle + ' • ' : '') + (Store.unfinished(e) ? `${fmt(e.pos)} / ${fmt(e.dur)}` : 'Watched'),
        });
        return card(v, { data: `data-i="${i}"`, href: withToken(e.url), sub: (e.epTitle ? e.epTitle + ' • ' : '') + e.label });
      }).join('')}</div><p class="hint">Long-press a title to remove it.</p>`;
    }
    view().innerHTML = `<h1 class="page-title">Library</h1>
      <div class="chips">${tabs.map((t) => `<button class="chip${t === cur ? ' on' : ''}" data-tab="${t[0]}">${t[1]} (${t[2].length})</button>`).join('')}</div>${body}`;
    view().querySelectorAll('.chip').forEach((b) => b.onclick = () => { state.libTab = b.dataset.tab; Library(); });
    view().querySelectorAll('.card[data-i]').forEach((c) => {
      let t;
      const remove = () => {
        const e = list[+c.dataset.i];
        if (!confirm(`Remove “${e.video.title}”?`)) return;
        if (cur[0] === 'fav') Store.toggleFav(e.video); else if (cur[0] === 'hist') Store.removeHist(e.video.id); else Store.removeDl(e.time);
        Library();
      };
      c.addEventListener('contextmenu', (ev) => { ev.preventDefault(); remove(); });
      c.addEventListener('touchstart', () => { t = setTimeout(() => { t = 'fired'; remove(); }, 600); }, { passive: true });
      c.addEventListener('touchend', (ev) => { if (t === 'fired') ev.preventDefault(); clearTimeout(t); }, { passive: false });
      c.addEventListener('touchmove', () => clearTimeout(t), { passive: true });
    });
  }

  async function Settings() {
    view().innerHTML = `<h1 class="page-title">Settings</h1>
      <section class="card-box"><h3>Server token</h3><p>Only needed if the server was started with APP_TOKEN.</p>
        <input class="field" id="tok" type="password" placeholder="Token (leave empty if not set)" value="${esc(token())}" autocomplete="off">
        <button class="btn" id="saveTok">Save & test</button><div class="status" id="tokStatus"></div></section>
      <section class="card-box"><h3>TMDB</h3><p>Adds posters, descriptions, ratings, trailers and trending lists. The key is stored on your server only.</p>
        <input class="field" id="tmdbKey" type="password" placeholder="TMDB API key (v3) or read access token" autocomplete="off">
        <select class="field" id="lang">
          ${[['en-US', 'English'], ['bn-BD', 'বাংলা'], ['hi-IN', 'हिन्दी'], ['ko-KR', '한국어'], ['ar-SA', 'العربية'], ['es-ES', 'Español']].map(([c, n]) => `<option value="${c}">${n}</option>`).join('')}
        </select>
        <button class="btn" id="saveTmdb">Save TMDB settings</button><div class="status" id="tmdbStatus"></div></section>
      <section class="card-box"><h3>Library</h3>
        <button class="btn flat" id="clrH">Clear watch history</button>
        <button class="btn flat" id="clrF" style="margin-top:8px">Clear favorites</button></section>
      <p class="about">Cinedor web • plays videos from your own server.<br>Tip: use “Add to Home screen” in your browser to open it like an app.</p>`;

    const st = $('#tmdbStatus');
    const showCfg = (c) => {
      state.config = c;
      st.textContent = c.tmdbEnabled ? `✓ TMDB on (key ${c.tmdbKeyMasked}${c.tmdbFromEnv ? ', from TMDB_API_KEY' : ''})` : 'TMDB is off';
      $('#lang').value = c.language || 'en-US';
    };
    api('/api/config').then(showCfg).catch((e) => { st.textContent = e.message; });

    $('#saveTok').onclick = async () => {
      ls.set('cinedor.token', $('#tok').value.trim());
      const s = $('#tokStatus'); s.textContent = 'Checking…';
      try { const r = await api('/api'); s.textContent = `✓ Connected — ${r.titles} titles`; state.items = null; state.discover = null; api('/api/config').then(showCfg).catch(() => {}); }
      catch (e) { s.textContent = '✗ ' + e.message; }
    };
    $('#saveTmdb').onclick = async () => {
      const body = { language: $('#lang').value };
      const k = $('#tmdbKey').value.trim();
      if (k) body.tmdbKey = k;
      st.textContent = k ? 'Checking the key with TMDB…' : 'Saving…';
      try { showCfg(await api('/api/config', { method: 'POST', body: JSON.stringify(body) })); $('#tmdbKey').value = ''; state.discover = null; state.items = null; toast('TMDB settings saved'); }
      catch (e) { st.textContent = '✗ ' + e.message; }
    };
    $('#clrH').onclick = () => { if (confirm('Clear watch history?')) { ls.set('cinedor.hist', []); toast('History cleared'); } };
    $('#clrF').onclick = () => { if (confirm('Clear all favorites?')) { ls.set('cinedor.fav', []); toast('Favorites cleared'); } };
  }

  // ----- local title page -----
  async function Title(id) {
    view().innerHTML = '<div class="spinner" style="margin-top:30vh"></div>';
    await loadCatalog();
    let v = findLocal(id);
    if (!v) { try { v = await api('/api/videos/' + encodeURIComponent(id)); } catch (e) { view().innerHTML = `<div class="message">${esc(e.message)}<br><br><a href="#/" style="color:var(--accent)">Back home</a></div>`; return; } }
    const last = Store.last(v.id);
    const canDl = (v.qualities || []).some((q) => q.type !== 'hls');
    const playLabel = Store.unfinished(last) ? `Resume  ${last.epTitle ? esc(last.epTitle) + ' • ' : ''}${fmt(last.pos)}` : 'Play';
    view().innerHTML = detailShell(v, `
      <button class="btn-play" id="play">${icon('play')}${playLabel}</button>
      ${canDl ? `<button class="btn-ghost" id="dl">${icon('download')}Download</button>` : ''}
      ${v.trailer ? `<button class="btn-ghost" id="trailer">${icon('trailer')}Watch trailer</button>` : ''}
      ${v.description ? `<p class="desc">${esc(v.description)}</p>` : ''}
      ${v.episodes && v.episodes.length ? `<h2 class="section-title" style="margin-inline:0">Episodes</h2>${v.episodes.map((ep, i) => {
        const info = ep.qualities.map((q) => q.label).join(' · ') + (last && last.ep === ep.id ? (Store.unfinished(last) ? `  •  at ${fmt(last.pos)}` : '  •  watched') : '');
        const dlq = ep.qualities.find((q) => q.type !== 'hls');
        return `<div class="ep" data-ep="${i}" role="button" tabindex="0"><span class="num">${i + 1}</span><span class="txt">${esc(ep.title)}<small>${esc(info)}</small></span>
          ${dlq ? `<a href="${esc(withToken(dlq.url))}" download data-dl="${i}" aria-label="Download">${icon('download')}</a>` : ''}</div>`;
      }).join('')}` : ''}`);
    wireDetail(v);
    $('#play').onclick = () => playDefault(v);
    if (canDl) $('#dl').onclick = () => download(v, null);
    if (v.trailer) $('#trailer').onclick = () => trailer(v.trailer);
    view().querySelectorAll('.ep').forEach((row) => row.addEventListener('click', (e) => {
      const ep = v.episodes[+row.dataset.ep];
      if (e.target.closest('[data-dl]')) { const q = ep.qualities.find((x) => x.type !== 'hls'); Store.addDl(v, ep, q); toast('Download started'); return; }
      if (last && last.ep === ep.id && Store.unfinished(last)) resume(last); else play(v, ep);
    }));
  }

  // ----- TMDB title page (info only) -----
  async function Tmdb(type, id) {
    view().innerHTML = '<div class="spinner" style="margin-top:30vh"></div>';
    let v;
    try { [v] = await Promise.all([api(`/api/tmdb/${type}/${id}`), loadCatalog()]); }
    catch (e) { view().innerHTML = `<div class="message">${esc(e.message)}<br><br><a href="#/" style="color:var(--accent)">Back home</a></div>`; return; }
    const own = v.inLibrary ? findLocal(v.inLibrary) : null;
    const extra = [v.runtime ? (type === 'tv' ? `${v.runtime} min / ep` : `${Math.floor(v.runtime / 60)}h ${v.runtime % 60}m`) : '', v.seasons ? `${v.seasons} season${v.seasons > 1 ? 's' : ''}` : ''].filter(Boolean);
    view().innerHTML = detailShell(v, `
      ${own ? `<button class="btn-play" id="play">${icon('play')}Play from your library</button>` : ''}
      ${v.trailer ? `<button class="${own ? 'btn-ghost' : 'btn-play'}" id="trailer">${icon('trailer')}Watch trailer</button>` : ''}
      ${!own ? `<div class="note">Not in your library. Add the file to your server's media folder to play it here.</div>` : ''}
      ${v.tagline ? `<p class="tagline">“${esc(v.tagline)}”</p>` : ''}
      ${v.description ? `<p class="desc">${esc(v.description)}</p>` : ''}
      ${v.cast && v.cast.length ? `<h2 class="section-title" style="margin-inline:0">Cast</h2><div class="cast">${v.cast.map((c) => `<div>${c.photo ? `<img src="${esc(c.photo)}" alt="" loading="lazy">` : '<span class="ph"></span>'}<b>${esc(c.name)}</b>${esc(c.character)}</div>`).join('')}</div>` : ''}
      <p class="about" style="margin-inline:0">Data and images: TMDB</p>`, extra);
    wireDetail(v);
    if (own) $('#play').onclick = () => playDefault(own);
    if (v.trailer) $('#trailer').onclick = () => trailer(v.trailer);
  }

  function detailShell(v, inner, extra = []) {
    const fav = Store.isFav(v.id);
    const m = [v.year, v.type === 'series' ? 'SERIES' : 'MOVIE', ...extra].filter(Boolean).map(esc);
    if (v.rating) m.push(`<span><span class="star">★</span> ${v.rating.toFixed(1)}</span>`);
    if (v.episodes && v.episodes.length) m.push(`${v.episodes.length} episodes`);
    return `<div class="detail-bar"><button class="icon-btn" id="back" aria-label="Back">${icon('back')}</button>
        <button class="icon-btn" id="fav" aria-label="Favorite">${icon(fav ? 'bookmark' : 'bookmarkO')}</button></div>
      <div class="detail-hero">${portrait(v) ? `<img class="tall" src="${esc(withToken(portrait(v)))}" alt=""><img class="wide" src="${esc(withToken(wide(v)))}" alt="">` : ''}</div>
      <div class="detail-body"><div class="center">
        ${v.genre ? `<div class="hero-genre">${esc(v.genre)}</div>` : ''}
        <h1 class="detail-title">${esc(v.title)}</h1>
        <div class="detail-meta">${m.map((x) => `<span>${x}</span>`).join('')}</div></div>
        ${inner}</div>`;
  }

  function wireDetail(v) {
    document.body.classList.add('hide-nav');
    onCleanup(() => document.body.classList.remove('hide-nav'));
    $('#back').onclick = () => (history.length > 1 ? history.back() : (location.hash = '#/'));
    $('#fav').onclick = () => {
      const on = Store.toggleFav(v);
      $('#fav').innerHTML = icon(on ? 'bookmark' : 'bookmarkO');
      toast(on ? 'Added to favorites' : 'Removed from favorites');
    };
  }

  // ---------- playback ----------
  function sheet(title, qs, pick) {
    const bd = document.createElement('div');
    bd.className = 'backdrop';
    bd.innerHTML = `<div class="sheet" role="dialog" aria-label="${esc(title)}"><h3>${esc(title)}</h3>${qs.map((q, i) =>
      `<button data-i="${i}"><b>${esc(q.label)}</b><span>${q.type === 'hls' ? 'HLS stream' : (q.type || 'mp4').toUpperCase()}${q.size ? ' • ' + size(q.size) : ''}</span></button>`).join('')}</div>`;
    bd.addEventListener('click', (e) => {
      const b = e.target.closest('button[data-i]');
      if (b) pick(qs[+b.dataset.i]);
      if (b || e.target === bd) bd.remove();
    });
    document.body.appendChild(bd);
  }

  function playDefault(v) {
    const last = Store.last(v.id);
    if (Store.unfinished(last)) return resume(last);
    if (v.episodes && v.episodes.length) {
      let i = 0;
      if (last && last.ep) { const j = v.episodes.findIndex((e) => e.id === last.ep); if (j >= 0) i = Math.min(j + 1, v.episodes.length - 1); }
      return play(v, v.episodes[i]);
    }
    play(v, null);
  }
  function play(v, ep) {
    const qs = (ep ? ep.qualities : v.qualities) || [];
    if (!qs.length) return toast('Nothing to play');
    if (qs.length === 1) return startPlayer(v, ep, qs[0], 0);
    sheet('Select quality to watch', qs, (q) => startPlayer(v, ep, q, 0));
  }
  async function resume(e) {
    await loadCatalog();
    const v = findLocal(e.video.id);
    if (!v) return toast('This title is no longer on the server');
    const ep = e.ep ? (v.episodes || []).find((x) => x.id === e.ep) : null;
    const qs = (ep ? ep.qualities : v.qualities) || [];
    const q = qs.find((x) => x.url === e.url) || qs.find((x) => x.label === e.label) || qs[0];
    if (!q) return toast('Nothing to play');
    startPlayer(v, ep, q, e.pos);
  }
  function download(v, ep) {
    const qs = ((ep ? ep.qualities : v.qualities) || []).filter((q) => q.type !== 'hls');
    if (!qs.length) return toast("HLS streams can't be downloaded");
    const go = (q) => {
      const a = document.createElement('a');
      a.href = withToken(q.url); a.download = ''; document.body.appendChild(a); a.click(); a.remove();
      Store.addDl(v, ep, q); toast(`Download started (${q.label})`);
    };
    qs.length === 1 ? go(qs[0]) : sheet('Select quality to download', qs, go);
  }

  let hlsLoader;
  function loadHls() {
    hlsLoader = hlsLoader || new Promise((ok, fail) => {
      const s = document.createElement('script');
      s.src = 'https://cdnjs.cloudflare.com/ajax/libs/hls.js/1.5.13/hls.min.js';
      s.onload = () => ok(window.Hls); s.onerror = () => { hlsLoader = null; fail(new Error('Could not load the HLS player')); };
      document.head.appendChild(s);
    });
    return hlsLoader;
  }

  function startPlayer(v, ep, q, startMs) {
    const el = document.createElement('div');
    el.className = 'player';
    el.innerHTML = `<video playsinline controls autoplay preload="auto"></video>
      <div class="ptop"><button class="icon-btn" aria-label="Close">${icon('back')}</button><span>${esc(v.title + (ep ? ' • ' + ep.title : '') + ' • ' + q.label)}</span></div>`;
    document.body.appendChild(el);
    document.body.classList.add('hide-nav');
    const video = $('video', el);
    let hls, saveTimer, idleTimer;
    const save = () => { if (video.currentTime > 0) Store.save(v, ep, q, Math.round(video.currentTime * 1000), Math.round((video.duration || 0) * 1000)); };
    const close = () => {
      save(); clearInterval(saveTimer); clearTimeout(idleTimer);
      if (hls) hls.destroy();
      video.pause(); video.removeAttribute('src'); video.load();
      el.remove(); document.body.classList.remove('hide-nav');
      if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
      window.removeEventListener('popstate', onPop);
      route();
    };
    const onPop = () => close();
    history.pushState({ player: true }, '');
    window.addEventListener('popstate', onPop);
    $('.ptop button', el).onclick = () => history.back();

    const src = withToken(q.url);
    const isHls = q.type === 'hls' || /\.m3u8(\?|$)/i.test(q.url);
    const begin = () => { if (startMs > 0) { video.currentTime = startMs / 1000; toast('Resuming from ' + fmt(startMs)); } };
    if (isHls && !video.canPlayType('application/vnd.apple.mpegurl')) {
      loadHls().then((Hls) => {
        if (!Hls.isSupported()) throw new Error('HLS is not supported in this browser');
        hls = new Hls({ xhrSetup: (xhr) => { if (token()) xhr.setRequestHeader('X-App-Token', token()); } });
        hls.loadSource(src); hls.attachMedia(video);
        hls.on(Hls.Events.MANIFEST_PARSED, () => { begin(); video.play().catch(() => {}); });
      }).catch((e) => toast(e.message));
    } else {
      video.src = src;
      video.addEventListener('loadedmetadata', begin, { once: true });
    }
    video.addEventListener('error', () => toast('Playback error — try another quality'));
    video.addEventListener('pause', save);
    video.addEventListener('ended', () => { Store.save(v, ep, q, Math.round(video.duration * 1000), Math.round(video.duration * 1000)); });
    saveTimer = setInterval(save, 5000);
    const wake = () => { el.classList.remove('idle'); clearTimeout(idleTimer); idleTimer = setTimeout(() => { if (!video.paused) el.classList.add('idle'); }, 3000); };
    el.addEventListener('pointermove', wake); el.addEventListener('touchstart', wake, { passive: true }); wake();
    try { screen.orientation && screen.orientation.lock && screen.orientation.lock('landscape').catch(() => {}); } catch {}
  }

  function trailer(key) {
    const bd = document.createElement('div');
    bd.className = 'backdrop';
    bd.innerHTML = `<div class="modal"><div class="frame"><iframe src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(key)}?autoplay=1&rel=0" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen title="Trailer"></iframe></div>
      <button class="icon-btn close" aria-label="Close">${icon('close')}</button></div>`;
    bd.addEventListener('click', (e) => { if (e.target === bd || e.target.closest('.close')) bd.remove(); });
    document.body.appendChild(bd);
  }

  // ---------- router ----------
  async function route() {
    if ($('.player')) return;
    cleanup.forEach((f) => { try { f(); } catch {} }); cleanup = [];
    document.querySelectorAll('.backdrop').forEach((b) => b.remove());
    const h = location.hash.replace(/^#\/?/, '');
    const [page, a, b] = h.split('/');
    const tab = { '': 'home', search: 'search', library: 'library', settings: 'settings' }[page];
    document.querySelectorAll('#nav a').forEach((x) => x.classList.toggle('on', x.dataset.tab === tab));
    window.scrollTo(0, 0);
    if (page === 'search') return Search();
    if (page === 'library') return Library();
    if (page === 'settings') return Settings();
    if (page === 'title') return Title(decodeURIComponent(a || ''));
    if (page === 'tmdb') return Tmdb(a, b);
    if (page === 'resume' || page === 'resume-h') {
      const list = page === 'resume' ? Store.cont() : Store.hist();
      const e = list[+a];
      history.replaceState(null, '', page === 'resume' ? '#/' : '#/library');
      await route();
      if (e) resume(e);
      return;
    }
    return Home();
  }
  window.addEventListener('hashchange', route);
  route();
})();
