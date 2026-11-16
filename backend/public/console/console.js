/* Cinedor Console — talks only to this server. Built with DOM calls (no innerHTML),
   so text from feedback or file names can never run as code. */
(() => {
  'use strict';

  // ---------- helpers ----------
  const $ = (s, r = document) => r.querySelector(s);
  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (k === 'value') el.value = v;
      else if (k === 'checked') el.checked = Boolean(v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }

  // Material-style icons (24x24 paths)
  const ICON = {
    dashboard: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
    library: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z',
    feedback: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z',
    announce: 'M18 11v2h4v-2h-4zm-2 6.61c.96.71 2.21 1.65 3.2 2.39l1.2-1.6c-.99-.74-2.24-1.68-3.2-2.4l-1.2 1.61zM20.4 5.6l-1.2-1.6c-.99.74-2.24 1.68-3.2 2.4l1.2 1.6c.96-.72 2.21-1.65 3.2-2.4zM4 9c-1.1 0-2 .9-2 2v2c0 1.1.9 2 2 2h1v4h2v-4h1l5 3V6L8 9H4zm11.5 3c0-1.33-.58-2.53-1.5-3.35v6.69c.92-.81 1.5-2.01 1.5-3.34z',
    settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.48.48 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z',
    add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
    search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
    upload: 'M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z',
    trash: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
    refresh: 'M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
    logout: 'M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5-5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z',
    image: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z',
    warn: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
    open: 'M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z',
    movie: 'M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z',
    video: 'M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z',
    sub: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z',
    tv: 'M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z',
    cloud: 'M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
    lock: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM12 17c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z',
  };
  function ic(name, cls) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'ic' + (cls ? ' ' + cls : ''));
    svg.setAttribute('aria-hidden', 'true');
    const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('d', ICON[name] || '');
    svg.append(p);
    return svg;
  }
  function logoMark() {   // the same mark as the app icon
    const NS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 108 108');
    const mk = (d, fill) => { const p = document.createElementNS(NS, 'path'); p.setAttribute('d', d); p.setAttribute('fill', fill); svg.append(p); };
    mk('M54,14a40,40 0 1,0 0.001,0z', '#F5A524'); mk('M44,34v38l32,-19z', '#15161B'); mk('M26,88h56v7h-56z', '#F5A524');
    return svg;
  }

  const size = (b) => b >= 2 ** 30 ? (b / 2 ** 30).toFixed(1) + ' GB' : b >= 2 ** 20 ? Math.round(b / 2 ** 20) + ' MB' : b >= 1024 ? Math.round(b / 1024) + ' KB' : b + ' B';
  const dur = (s) => { const d = Math.floor(s / 86400), hr = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60); return d ? `${d}d ${hr}h` : hr ? `${hr}h ${m}m` : `${m}m`; };
  const ago = (t) => { const s = (Date.now() - t) / 1000; return s < 60 ? 'just now' : s < 3600 ? Math.floor(s / 60) + ' min ago' : s < 86400 ? Math.floor(s / 3600) + ' h ago' : new Date(t).toLocaleDateString(); };
  let toastTimer;
  function toast(msg) {
    const t = $('#toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2400);
  }

  async function api(path, opts = {}) {
    const headers = { Accept: 'application/json', 'X-Console': '1' };
    let body = opts.body;
    if (body && typeof body === 'object' && !(body instanceof Blob)) { body = JSON.stringify(body); headers['Content-Type'] = 'application/json'; }
    const res = await fetch('/console/api' + path, { method: opts.method || 'GET', headers, body });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 && state.user) { state.user = false; render(); }
    if (!res.ok) throw new Error(data.error || 'Server error ' + res.status);
    return data;
  }
  const fileUrl = (folder, path) => `/console/api/file?folder=${encodeURIComponent(folder)}&path=${encodeURIComponent(path)}`;

  const state = { user: false, tab: 'dashboard', info: null, unread: 0, search: '', filter: 'all', fbFilter: 'all' };
  const TABS = [['dashboard', 'Dashboard'], ['library', 'Library'], ['feedback', 'Feedback'], ['announce', 'Announce'], ['settings', 'Settings']];
  const TITLES = { dashboard: 'Dashboard', library: 'Library', feedback: 'Feedback', announce: 'Announcement', settings: 'Settings' };
  const go = (tab) => { state.tab = tab; render(); };

  // ---------- login / setup ----------
  function loginView() {
    const setup = !state.info.configured;
    const pw = h('input', { class: 'field', type: 'password', placeholder: setup ? 'Choose a password (8+ characters)' : 'Password', autocomplete: setup ? 'new-password' : 'current-password' });
    const status = h('div', { class: 'status' });
    const submit = async () => {
      status.className = 'status'; status.textContent = '';
      try { await api(setup ? '/setup' : '/login', { method: 'POST', body: { password: pw.value } }); await boot(); }
      catch (e) { status.className = 'status err'; status.textContent = e.message; }
    };
    pw.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
    if (setup && !state.info.setupAllowed) {
      status.className = 'status err';
      status.textContent = 'First-time setup is only allowed on the device that runs the server. Open http://localhost:PORT/console there, or start the server with ADMIN_PASSWORD=yourpassword.';
    }
    setTimeout(() => pw.focus(), 50);
    return h('div', { class: 'login' }, h('div', { class: 'box' },
      h('div', { class: 'mark' }, logoMark()),
      h('h1', {}, 'Cinedor Console'),
      h('p', {}, setup ? 'Welcome! Create an admin password to protect your server.' : 'Sign in to manage your server.'),
      pw, h('button', { class: 'btn block', style: null, onclick: submit }, setup ? 'Create password' : 'Sign in'), status));
  }

  // ---------- shell ----------
  function navButton(id, name, withLabel) {
    return h('button', { class: state.tab === id ? 'on' : '', onclick: () => go(id), 'aria-label': name },
      ic(id), h('span', { class: 'lbl' }, name),
      id === 'feedback' && state.unread ? h('span', { class: 'dot' }, state.unread) : null);
  }
  function shell(actions) {
    return h('div', { class: 'shell' },
      h('aside', { class: 'side' },
        h('div', { class: 'brand' }, h('div', { class: 'mk' }, logoMark()), h('div', {}, 'Cinedor', h('small', {}, 'Console'))),
        h('nav', {}, TABS.map(([id, name]) => h('button', { class: state.tab === id ? 'on' : '', onclick: () => go(id) },
          ic(id), name, id === 'feedback' && state.unread ? h('span', { class: 'dot' }, state.unread) : null))),
        h('div', { class: 'foot' }, h('button', { onclick: logout }, ic('logout'), 'Sign out'))),
      h('div', { class: 'content' },
        h('header', { class: 'topbar' }, h('h1', {}, TITLES[state.tab]), actions),
        h('main', { class: 'main', id: 'page' })),
      h('nav', { class: 'bottom', 'aria-label': 'Main' }, TABS.map(([id, name]) => navButton(id, name))));
  }
  async function logout() { try { await api('/logout', { method: 'POST' }); } catch {} state.user = false; await boot(); }

  const empty = (icon, title, sub) => h('div', { class: 'empty' }, ic(icon), h('b', {}, title), sub || null);
  const section = (title, ...right) => h('div', { class: 'section-title' }, title, h('span', { class: 'grow' }), ...right);

  // ---------- dashboard ----------
  async function dashboard(page) {
    const [s, t] = await Promise.all([api('/stats'), api('/titles')]);
    state.unread = s.unread;
    const used = s.freeDisk == null ? 0 : s.size / (s.size + s.freeDisk);
    const total = Math.max(1, s.movies + s.series);
    const tile = (kind, icon, big, small, onclick) => h('button', { class: 'tile ' + kind, onclick }, h('div', { class: 'ico' }, ic(icon)), h('div', {}, h('b', {}, big), h('small', {}, small)));
    const attention = t.items.filter((x) => !x.videos || !x.poster).slice(0, 5);

    page.append(
      h('div', { class: 'hero-card' },
        h('div', { class: 'big' }, s.titles, h('small', {}, s.titles === 1 ? 'title in your library' : 'titles in your library')),
        h('div', { class: 'split' }, h('i', { class: 'a', style: null }), h('i', { class: 'b', style: null })),
        h('div', { class: 'legend' },
          h('span', {}, h('em', { class: 'dotA' }), h('b', {}, s.movies), ' movies'),
          h('span', {}, h('em', { class: 'dotB' }), h('b', {}, s.series), ' series'),
          h('span', {}, h('b', {}, size(s.size)), ' used')),
        s.freeDisk == null ? null : h('div', { class: 'disk' },
          h('small', {}, 'Storage'), h('div', { class: 'bar' }, h('i', { class: used > 0.85 ? 'hot' : '' })),
          h('small', {}, `${size(s.freeDisk)} free on this device`))),
      h('div', { class: 'tiles' },
        tile(s.unread ? 'accent' : '', 'feedback', s.unread, s.unread === 1 ? 'new message' : 'new messages', () => go('feedback')),
        tile(s.noPoster ? 'warn' : 'ok', 'image', s.noPoster, 'missing poster', () => { state.filter = 'attention'; go('library'); }),
        tile(s.noVideo ? 'bad' : 'ok', 'video', s.noVideo, 'without video', () => { state.filter = 'attention'; go('library'); }),
        tile(s.tmdb ? 'ok' : 'warn', 'cloud', s.tmdb ? 'On' : 'Off', 'TMDB data', () => go('settings'))),
      h('div', { class: 'section-title' }, 'Quick actions'),
      h('div', { class: 'row' },
        h('button', { class: 'btn', onclick: () => { state.filter = 'all'; state.newTitle = true; go('library'); } }, ic('add', 'sm'), 'New title'),
        h('button', { class: 'btn flat', onclick: async () => { const r = await api('/rescan', { method: 'POST' }); toast(`Rescanned — ${r.titles} titles`); render(); } }, ic('refresh', 'sm'), 'Rescan'),
        h('button', { class: 'btn flat', onclick: () => window.open('/', '_blank', 'noopener') }, ic('open', 'sm'), 'Open web app')),
      attention.length ? section('Needs attention') : null,
      ...attention.map((x) => h('button', { class: 'mini', onclick: () => editor(x.folder, () => render()) },
        h('div', { class: 'mico' }, ic(!x.videos ? 'video' : 'image')),
        h('div', { class: 't' }, h('b', {}, x.title), h('small', {}, [!x.videos ? 'No video file' : null, !x.poster ? 'No poster' : null].filter(Boolean).join(' · '))),
        ic('warn', 'sm'))),
      h('p', { class: 'status' }, `Server up for ${dur(s.uptime)} · Node ${s.node}${s.announcement ? ' · announcement is live' : ''}`));
    // widths via CSSOM (allowed by the CSP, unlike inline style attributes)
    const [a, b] = page.querySelectorAll('.split i');
    a.style.width = (s.movies / total * 100) + '%'; b.style.width = (s.series / total * 100) + '%';
    const bar = page.querySelector('.disk .bar i'); if (bar) bar.style.width = Math.max(2, used * 100) + '%';
    const dots = page.querySelectorAll('.legend em');
    if (dots[0]) dots[0].style.background = '#F5A524'; if (dots[1]) dots[1].style.background = '#5E81F4';
  }

  // ---------- library ----------
  async function library(page) {
    let items = (await api('/titles')).items;
    const q = h('input', { class: 'field', type: 'search', placeholder: 'Search your library', value: state.search });
    const chips = h('div', { class: 'chips' });
    const grid = h('div', { class: 'posters' });
    const refresh = async () => { items = (await api('/titles')).items; draw(); };
    page.append(h('div', { class: 'searchbar' }, ic('search'), q), chips, grid,
      h('button', { class: 'fab', onclick: newTitle, 'aria-label': 'New title' }, ic('add'), h('span', {}, 'New title')));

    const FILTERS = [['all', 'All'], ['movie', 'Movies'], ['series', 'Series'], ['attention', 'Needs attention']];
    function draw() {
      chips.replaceChildren(...FILTERS.map(([id, name]) => {
        const n = id === 'all' ? items.length : id === 'attention' ? items.filter((x) => !x.videos || !x.poster).length : items.filter((x) => (x.type === 'series') === (id === 'series')).length;
        return h('button', { class: 'chip' + (state.filter === id ? ' on' : ''), onclick: () => { state.filter = id; draw(); } }, `${name} · ${n}`);
      }));
      const f = q.value.trim().toLowerCase();
      const shown = items.filter((t) => {
        if (state.filter === 'movie' && t.type === 'series') return false;
        if (state.filter === 'series' && t.type !== 'series') return false;
        if (state.filter === 'attention' && t.videos && t.poster) return false;
        return !f || (t.title + ' ' + t.folder + ' ' + t.year).toLowerCase().includes(f);
      });
      grid.replaceChildren();
      if (!shown.length) { grid.className = ''; grid.append(items.length ? empty('search', 'Nothing matches') : empty('library', 'No titles yet', 'Tap “New title”, then upload a video.')); return; }
      grid.className = 'posters';
      for (const t of shown) {
        grid.append(h('button', { class: 'pcard', onclick: () => editor(t.folder, refresh) },
          t.poster ? h('img', { src: fileUrl(t.folder, t.poster), alt: '', loading: 'lazy' }) : h('div', { class: 'noimg' }, ic('movie')),
          h('div', { class: 'tags' },
            !t.videos ? h('span', { class: 'pill bad' }, 'No video') : null,
            !t.poster && t.videos ? h('span', { class: 'pill warn' }, 'No poster') : null,
            t.featured ? null : h('span', { class: 'pill' }, 'Hidden')),
          h('div', { class: 'meta' }, h('b', {}, t.title), h('small', {}, [t.year, t.type === 'series' ? 'Series' : 'Movie', size(t.size)].filter(Boolean).join(' · ')))));
      }
    }
    q.addEventListener('input', () => { state.search = q.value; draw(); });
    draw();
    function newTitle() {
      const name = h('input', { class: 'field', placeholder: 'e.g. My Trip 2026', maxlength: '120' });
      const type = h('select', { class: 'field' }, h('option', { value: 'movie' }, 'Movie'), h('option', { value: 'series' }, 'Series (episodes in sub-folders)'));
      const status = h('div', { class: 'status' });
      const m = sheet('New title', h('div', {},
        h('label', { class: 'field-wrap' }, h('span', {}, 'Folder name'), name), h('label', { class: 'field-wrap' }, h('span', {}, 'Type'), type), status),
      h('button', { class: 'btn', onclick: async () => {
        try {
          const r = await api('/titles', { method: 'POST', body: { folder: name.value, type: type.value, title: name.value.trim() } });
          m.close(); editor(r.folder, refresh);
        } catch (e) { status.className = 'status err'; status.textContent = e.message; }
      } }, 'Create'));
      setTimeout(() => name.focus(), 60);
    }
    if (state.newTitle) { state.newTitle = false; newTitle(); }
  }

  // ---------- sheet (bottom sheet on phones, dialog on desktop) ----------
  function sheet(title, body, footer, onClose) {
    const overlay = h('div', { class: 'overlay' });
    const close = () => { overlay.remove(); if (onClose) onClose(); };
    overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
    const footEl = footer ? h('div', { class: 'sheet-foot' }, footer) : null;
    const bodyEl = h('div', { class: 'sheet-body' }, body);
    overlay.append(h('div', { class: 'sheet', role: 'dialog', 'aria-label': title },
      h('div', { class: 'grab' }),
      h('div', { class: 'sheet-head' }, h('h2', {}, title), h('button', { class: 'icon-btn', onclick: close, 'aria-label': 'Close' }, ic('close'))),
      bodyEl, footEl));
    document.body.append(overlay);
    return { close, body: bodyEl, foot: footEl };
  }

  async function editor(folder, onClose) {
    const queue = h('div', {});            // upload progress rows survive reloads
    let busy = 0;
    const holder = h('div', {});
    const foot = h('div', { class: 'row grow' });
    const m = sheet(folder, holder, foot, onClose);
    const load = async () => {
      let t;
      try { t = await api('/titles/' + encodeURIComponent(folder)); } catch (e) { holder.replaceChildren(h('div', { class: 'status err' }, e.message)); return; }
      const info = t.info || {};
      const isSeries = (info.type || t.type) === 'series';
      const f = {
        title: h('input', { class: 'field', value: info.title || '', placeholder: folder }),
        year: h('input', { class: 'field', value: info.year || '', inputmode: 'numeric', maxlength: '4', placeholder: '2026' }),
        type: h('select', { class: 'field' }, h('option', { value: '' }, 'Auto'), h('option', { value: 'movie' }, 'Movie'), h('option', { value: 'series' }, 'Series')),
        genre: h('input', { class: 'field', value: info.genre || '', placeholder: 'Drama • Comedy' }),
        tmdb: h('input', { class: 'field', value: info.tmdb || '', placeholder: '603 or tv/1399 (optional)' }),
        description: h('textarea', { class: 'field', placeholder: 'Leave empty to use the TMDB description' }, info.description || ''),
        featured: h('input', { type: 'checkbox', checked: info.featured !== false }),
      };
      f.type.value = info.type || '';
      const status = h('div', { class: 'status' });
      foot.replaceChildren(status, h('button', { class: 'btn', onclick: async () => {
        status.className = 'status'; status.textContent = 'Saving…';
        try {
          await api(`/titles/${encodeURIComponent(folder)}/info`, { method: 'PUT', body: {
            title: f.title.value, year: f.year.value, type: f.type.value, genre: f.genre.value, tmdb: f.tmdb.value, description: f.description.value, featured: f.featured.checked } });
          status.className = 'status ok'; status.textContent = 'Saved ✓';
        } catch (e) { status.className = 'status err'; status.textContent = e.message; }
      } }, 'Save changes'));

      // ----- upload -----
      let episodeMode = isSeries;
      const sub = h('input', { class: 'field', placeholder: 'Episode folder, e.g. E01 Arrival', maxlength: '100' });
      const subWrap = h('label', { class: 'field-wrap' + (episodeMode ? '' : ' hidden') }, h('span', {}, 'Episode folder'), sub);
      const epToggle = h('input', { type: 'checkbox', checked: episodeMode });
      epToggle.addEventListener('change', () => { episodeMode = epToggle.checked; subWrap.classList.toggle('hidden', !episodeMode); });
      const picker = h('input', { type: 'file', multiple: true, class: 'hidden', accept: '.mp4,.m4v,.mkv,.webm,.mov,.m3u8,.ts,.m4s,.jpg,.jpeg,.png,.webp,.vtt,.srt' });
      const posterPicker = h('input', { type: 'file', class: 'hidden', accept: '.jpg,.jpeg,.png,.webp' });
      const drop = h('div', { class: 'drop', tabindex: '0', role: 'button', onclick: () => picker.click() },
        ic('upload'), h('b', {}, 'Tap to choose files'), h('small', {}, 'Name them 360p.mp4, 720p.mp4, 1080p.mp4 so the quality is labelled'));
      drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('over'); });
      drop.addEventListener('dragleave', () => drop.classList.remove('over'));
      drop.addEventListener('drop', (e) => { e.preventDefault(); drop.classList.remove('over'); send([...e.dataTransfer.files], episodeMode ? sub.value.trim() : ''); });
      picker.addEventListener('change', () => { send([...picker.files], episodeMode ? sub.value.trim() : ''); picker.value = ''; });
      posterPicker.addEventListener('change', () => {
        const file = posterPicker.files[0]; posterPicker.value = '';
        if (file) send([file], '', 'poster' + ((file.name.match(/\.[^.]+$/) || ['.jpg'])[0].toLowerCase()));
      });
      function send(files, subfolder, rename) {
        for (const file of files) {
          const name = rename || file.name;
          const bar = h('i', {}); const label = h('span', {}, `${name} · ${size(file.size)}`);
          queue.append(h('div', { class: 'up-item' }, label, h('div', { class: 'bar' }, bar)));
          const xhr = new XMLHttpRequest();
          xhr.open('PUT', `/console/api/upload?folder=${encodeURIComponent(folder)}&name=${encodeURIComponent(name)}` + (subfolder ? `&sub=${encodeURIComponent(subfolder)}` : ''));
          xhr.setRequestHeader('X-Console', '1');
          xhr.upload.onprogress = (e) => { if (e.lengthComputable) bar.style.width = Math.round(e.loaded / e.total * 100) + '%'; };
          xhr.onload = () => {
            busy--;
            if (xhr.status === 201) { bar.style.width = '100%'; label.textContent = `✓ ${name}`; }
            else { let msg = 'failed'; try { msg = JSON.parse(xhr.responseText).error; } catch {} label.textContent = `✗ ${name} — ${msg}`; }
            if (!busy) load();
          };
          xhr.onerror = () => { busy--; label.textContent = `✗ ${name} — connection lost`; };
          busy++; xhr.send(file);
        }
      }

      // ----- files grouped by episode folder -----
      const groups = new Map();
      for (const x of t.list) { const g = x.path.includes('/') ? x.path.slice(0, x.path.lastIndexOf('/')) : ''; if (!groups.has(g)) groups.set(g, []); groups.get(g).push(x); }
      const files = h('div', { class: 'files' });
      if (!t.list.length) files.append(h('div', { class: 'status' }, 'No files yet — upload a video above.'));
      for (const [g, list] of groups) {
        if (g) files.append(h('div', { class: 'group' }, g));
        for (const x of list) {
          files.append(h('div', { class: 'file' },
            h('div', { class: 'ico ' + x.kind }, ic(x.kind === 'video' ? 'video' : x.kind === 'image' ? 'image' : 'sub', 'sm')),
            h('div', { class: 'nm' }, x.name, h('small', {}, `${size(x.size)} · ${x.kind}`)),
            x.kind === 'info' ? null : h('button', { class: 'icon-btn plain', 'aria-label': 'Remove ' + x.name, onclick: async () => {
              if (!confirm(`Remove ${x.path}? It moves to media/.trash and can be restored from there.`)) return;
              try { await api(`/file?folder=${encodeURIComponent(folder)}&path=${encodeURIComponent(x.path)}`, { method: 'DELETE' }); toast('Removed'); load(); } catch (e) { toast(e.message); }
            } }, ic('trash', 'sm'))));
        }
      }

      holder.replaceChildren(
        h('div', { class: 'ed-top' },
          h('div', { class: 'poster' }, t.poster ? h('img', { src: fileUrl(folder, t.poster) + '&t=' + Date.now(), alt: '' }) : ic('movie')),
          h('div', { class: 'info' },
            h('div', { class: 'row' }, h('span', { class: 'pill accent' }, size(t.size)), h('span', { class: t.videos ? 'pill ok' : 'pill bad' }, `${t.videos} video${t.videos === 1 ? '' : 's'}`)),
            h('div', { class: 'row' }, h('button', { class: 'btn flat sm', onclick: () => posterPicker.click() }, ic('image', 'sm'), t.poster ? 'Change poster' : 'Add poster')))),
        section('Details'),
        h('div', { class: 'card' },
          h('div', { class: 'two' }, h('label', { class: 'field-wrap' }, h('span', {}, 'Title'), f.title), h('label', { class: 'field-wrap' }, h('span', {}, 'Year'), f.year)),
          h('div', { class: 'two' }, h('label', { class: 'field-wrap' }, h('span', {}, 'Type'), f.type), h('label', { class: 'field-wrap' }, h('span', {}, 'Genre'), f.genre)),
          h('label', { class: 'field-wrap' }, h('span', {}, 'TMDB id'), f.tmdb),
          h('label', { class: 'field-wrap' }, h('span', {}, 'Description'), f.description),
          h('label', { class: 'switch-row' }, h('div', { class: 't' }, 'Show in the big Home carousel', h('small', {}, 'Turn off to keep it in the rows only')),
            h('span', { class: 'switch' }, f.featured, h('i', {})))),
        section('Upload'),
        h('div', { class: 'card' },
          h('label', { class: 'switch-row' }, h('div', { class: 't' }, 'This file is an episode', h('small', {}, 'Series: each episode lives in its own folder')), h('span', { class: 'switch' }, epToggle, h('i', {}))),
          subWrap, drop, picker, posterPicker, queue),
        section('Files'), h('div', { class: 'card' }, files),
        section('Danger zone'),
        h('div', { class: 'card' }, h('p', { class: 'hint' }, 'Nothing is erased: the folder moves to media/.trash.'),
          h('button', { class: 'btn bad', onclick: async () => {
            if (!confirm(`Move “${folder}” to the trash?`)) return;
            try { await api('/titles/' + encodeURIComponent(folder), { method: 'DELETE' }); m.close(); toast('Moved to trash'); } catch (e) { toast(e.message); }
          } }, ic('trash', 'sm'), 'Delete title')));
    };
    load();
  }

  // ---------- feedback ----------
  async function feedback(page) {
    const items = (await api('/feedback')).items;
    state.unread = items.filter((i) => !i.read).length;
    const chips = h('div', { class: 'chips' }, [['all', `All · ${items.length}`], ['unread', `Unread · ${state.unread}`]].map(([id, name]) =>
      h('button', { class: 'chip' + (state.fbFilter === id ? ' on' : ''), onclick: () => { state.fbFilter = id; render(); } }, name)));
    page.append(chips);
    const shown = items.filter((i) => state.fbFilter === 'all' || !i.read);
    if (!shown.length) { page.append(empty('feedback', items.length ? 'All caught up' : 'No messages yet', 'Messages sent from the app show up here.')); return; }
    for (const fb of shown) {
      const name = fb.contact || 'Someone';
      page.append(h('div', { class: 'fb' + (fb.read ? '' : ' unread') },
        h('div', { class: 'av' }, name.replace(/^@/, '').charAt(0).toUpperCase() || '?'),
        h('div', { class: 'body' },
          h('div', { class: 'who' }, h('b', {}, name), h('small', {}, ago(fb.time) + (fb.app ? ' · app ' + fb.app : ''))),
          h('p', {}, fb.text),
          h('div', { class: 'acts' },
            h('button', { class: 'btn flat sm', onclick: async () => { await api('/feedback/' + fb.id, { method: 'PUT', body: { read: !fb.read } }); render(); } }, ic('check', 'sm'), fb.read ? 'Mark unread' : 'Mark read'),
            h('button', { class: 'btn bad sm', onclick: async () => { await api('/feedback/' + fb.id, { method: 'DELETE' }); render(); } }, ic('trash', 'sm'), 'Delete')))));
    }
    if (items.length > 1) page.append(h('div', { class: 'row' }, h('button', { class: 'btn bad sm', onclick: async () => { if (confirm('Delete ALL feedback?')) { await api('/feedback', { method: 'DELETE' }); render(); } } }, 'Clear all')));
  }

  // ---------- announcement ----------
  async function announce(page) {
    const a = await api('/announcement');
    const f = {
      enabled: h('input', { type: 'checkbox', checked: a.enabled }),
      title: h('input', { class: 'field', value: a.title || '', maxlength: '100', placeholder: 'e.g. New update is out!' }),
      text: h('textarea', { class: 'field', maxlength: '1000', placeholder: 'What do you want to tell everyone?' }, a.text || ''),
      link: h('input', { class: 'field', value: a.link || '', placeholder: 'https://… (optional)' }),
      linkLabel: h('input', { class: 'field', value: a.linkLabel || '', maxlength: '30', placeholder: 'Button text, e.g. Download' }),
    };
    const pvTitle = h('h3', {}), pvText = h('p', {}), pvBtns = h('div', { class: 'btns' });
    const paint = () => {
      pvTitle.textContent = f.title.value || 'Announcement';
      pvText.textContent = f.text.value || 'Your message appears here.';
      pvBtns.replaceChildren(h('span', {}, 'Close'), ...(f.link.value.trim() ? [h('span', {}, f.linkLabel.value || 'Open')] : []));
    };
    for (const k of ['title', 'text', 'link', 'linkLabel']) f[k].addEventListener('input', paint);
    paint();
    const status = h('div', { class: 'status' });
    page.append(
      h('div', { class: 'card' }, h('h2', {}, 'Message'), h('p', { class: 'hint' }, 'Shown once as a pop-up in the app. Saving again shows it again to everyone.'),
        h('label', { class: 'switch-row' }, h('div', { class: 't' }, 'Show this announcement'), h('span', { class: 'switch' }, f.enabled, h('i', {}))),
        h('label', { class: 'field-wrap' }, h('span', {}, 'Title'), f.title), h('label', { class: 'field-wrap' }, h('span', {}, 'Message'), f.text),
        h('div', { class: 'two' }, h('label', { class: 'field-wrap' }, h('span', {}, 'Link'), f.link), h('label', { class: 'field-wrap' }, h('span', {}, 'Button text'), f.linkLabel)),
        h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
          try { await api('/announcement', { method: 'PUT', body: { enabled: f.enabled.checked, title: f.title.value, text: f.text.value, link: f.link.value, linkLabel: f.linkLabel.value } }); status.className = 'status ok'; status.textContent = 'Saved ✓'; }
          catch (e) { status.className = 'status err'; status.textContent = e.message; }
        } }, 'Save announcement')), status),
      section('Preview'), h('div', { class: 'preview' }, pvTitle, pvText, pvBtns));
  }

  // ---------- settings ----------
  async function settings(page) {
    const s = await api('/settings');
    const key = h('input', { class: 'field', type: 'password', placeholder: s.tmdb.tmdbEnabled ? 'Saved (' + s.tmdb.tmdbKeyMasked + ') — paste to replace' : 'TMDB API key', autocomplete: 'off' });
    const lang = h('select', { class: 'field' }, [['en-US', 'English'], ['bn-BD', 'বাংলা'], ['hi-IN', 'हिन्दी'], ['ko-KR', '한국어'], ['ar-SA', 'العربية'], ['es-ES', 'Español']].map(([c, n]) => h('option', { value: c }, n)));
    lang.value = s.tmdb.language;
    const st = h('div', { class: 'status' });
    const cur = h('input', { class: 'field', type: 'password', placeholder: 'Current password', autocomplete: 'current-password' });
    const nxt = h('input', { class: 'field', type: 'password', placeholder: 'New password (8+ characters)', autocomplete: 'new-password' });
    const st2 = h('div', { class: 'status' });
    const row = (k, v, small) => h('div', { class: 'r' }, h('div', { class: 'k' }, k, small ? h('small', {}, small) : null), h('div', { class: 'v' }, v));

    page.append(
      section('TMDB'),
      h('div', { class: 'card' }, h('p', { class: 'hint' }, 'Fills posters and descriptions for your titles.' + (s.tmdb.tmdbFromEnv ? ' (Currently set by TMDB_API_KEY.)' : '')),
        key, h('label', { class: 'field-wrap' }, h('span', {}, 'Language'), lang),
        h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
          st.className = 'status'; st.textContent = 'Saving…';
          try { const body = { language: lang.value }; if (key.value.trim()) body.tmdbKey = key.value.trim(); await api('/settings', { method: 'POST', body }); key.value = ''; st.className = 'status ok'; st.textContent = 'Saved ✓'; }
          catch (e) { st.className = 'status err'; st.textContent = e.message; }
        } }, 'Save')), st),
      section('Security'),
      h('div', { class: 'card' }, s.passwordFromEnv ? h('p', { class: 'hint' }, 'The password is set by ADMIN_PASSWORD when the server starts.') : [
        h('p', { class: 'hint' }, 'Change the admin password. You stay signed in on this device.'), cur, nxt,
        h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
          try { await api('/password', { method: 'POST', body: { current: cur.value, next: nxt.value } }); cur.value = ''; nxt.value = ''; st2.className = 'status ok'; st2.textContent = 'Password changed ✓'; }
          catch (e) { st2.className = 'status err'; st2.textContent = e.message; }
        } }, 'Change password')), st2]),
      section('Server'),
      h('div', { class: 'rows' },
        row('Media folder', s.mediaDir), row('Port', String(s.port)),
        row('App token', s.appToken ? 'On' : 'Off', s.appToken ? null : 'Anyone on your network can read the library'),
        row('Public address', s.publicBase || 'From each request')),
      window.CinedorAdmin ? [section('This app'), h('div', { class: 'rows' },
        row('Cinedor Console app', window.CinedorAdmin.version()), row('Connected to', window.CinedorAdmin.server())),
        h('button', { class: 'btn flat block', onclick: () => window.CinedorAdmin.changeServer() }, 'Change server')] : null);
  }

  // ---------- render ----------
  const PAGES = { dashboard, library, feedback, announce, settings };
  async function render() {
    const app = $('#app');
    if (!state.info) return;
    if (!state.user) { app.replaceChildren(loginView()); return; }
    const actions = h('div', { class: 'row' },
      state.tab === 'dashboard' ? h('button', { class: 'icon-btn', 'aria-label': 'Refresh', onclick: () => render() }, ic('refresh', 'sm')) : null,
      h('button', { class: 'icon-btn plain topout', 'aria-label': 'Sign out', onclick: logout }, ic('logout', 'sm')));
    app.replaceChildren(shell(actions));
    const page = $('#page');
    page.append(h('div', { class: 'spinner' }));
    const holder = h('div', {});
    try { await PAGES[state.tab](holder); }
    catch (e) { holder.replaceChildren(h('div', { class: 'status err' }, e.message)); }
    page.replaceChildren(...holder.childNodes);
    window.scrollTo(0, 0);
    // the unread badge may have changed while loading
    if (state.unread && !document.querySelector('.bottom .dot')) $('.bottom button:nth-child(3)').append(h('span', { class: 'dot' }, state.unread));
  }

  async function boot() {
    try { state.info = await api('/state'); state.user = state.info.loggedIn; }
    catch (e) { $('#app').replaceChildren(empty('warn', 'Cannot reach the server', e.message)); return; }
    render();
  }
  boot();
})();
