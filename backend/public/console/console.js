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

  const state = { user: false, tab: 'dashboard', info: null, unread: 0, search: '' };
  const TABS = [['dashboard', '▦', 'Dashboard'], ['library', '🎬', 'Library'], ['feedback', '✉', 'Feedback'], ['announce', '📣', 'Announce'], ['settings', '⚙', 'Settings']];

  // ---------- login / setup ----------
  function loginView() {
    const info = state.info;
    const setup = !info.configured;
    const pw = h('input', { class: 'field', type: 'password', placeholder: setup ? 'Choose a password (8+ characters)' : 'Password', autocomplete: setup ? 'new-password' : 'current-password' });
    const status = h('div', { class: 'status' });
    const go = async () => {
      status.className = 'status'; status.textContent = '';
      try {
        await api(setup ? '/setup' : '/login', { method: 'POST', body: { password: pw.value } });
        await boot();
      } catch (e) { status.className = 'status err'; status.textContent = e.message; }
    };
    pw.addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); });
    const box = h('div', { class: 'box' },
      h('h1', {}, 'Cinedor Console'),
      h('p', {}, setup ? 'First time here — set an admin password.' : 'Sign in to manage your server.'),
      pw,
      h('button', { class: 'btn', onclick: go, style: null }, setup ? 'Create password' : 'Sign in'));
    box.lastChild.classList.add('full');
    box.append(status);
    if (setup && !info.setupAllowed) {
      status.className = 'status err';
      status.textContent = 'Setup is only allowed on the device that runs the server. Open http://localhost:PORT/console there, or start the server with ADMIN_PASSWORD=yourpassword.';
    }
    setTimeout(() => pw.focus(), 50);
    return h('div', { class: 'login' }, box);
  }

  // ---------- shell ----------
  function shell(content) {
    const nav = h('nav', { class: 'nav' }, TABS.map(([id, ic, name]) =>
      h('button', { class: state.tab === id ? 'on' : '', onclick: () => { state.tab = id; render(); } },
        h('span', { class: 'ic' }, ic), h('span', {}, name),
        id === 'feedback' && state.unread ? h('span', { class: 'n' }, state.unread) : null)));
    return h('div', { class: 'shell' },
      h('aside', { class: 'side' }, h('div', { class: 'brand' }, h('i', {}, '▶'), 'Cinedor Console'), nav,
        h('button', { class: 'out', onclick: logout }, '⎋  Sign out')),
      h('main', { class: 'main' }, content));
  }
  async function logout() { try { await api('/logout', { method: 'POST' }); } catch {} state.user = false; await boot(); }
  const head = (title, ...actions) => h('div', { class: 'page-head' }, h('h1', {}, title), ...actions);

  // ---------- dashboard ----------
  async function dashboard(box) {
    box.append(head('Dashboard', h('button', { class: 'btn flat small', onclick: async () => { const r = await api('/rescan', { method: 'POST' }); toast(`Rescanned — ${r.titles} titles`); render(); } }, '↻ Rescan library'),
      h('button', { class: 'btn flat small', onclick: () => window.open('/', '_blank', 'noopener') }, 'Open app ↗')));
    const s = await api('/stats');
    state.unread = s.unread;
    const stat = (n, label, warn) => h('div', { class: 'stat' + (warn ? ' warn' : '') }, h('b', {}, n), h('span', {}, label));
    box.append(h('div', { class: 'grid' },
      stat(s.titles, 'Titles'), stat(s.movies, 'Movies'), stat(s.series, 'Series'),
      stat(size(s.size), 'Library size'), stat(s.freeDisk == null ? '—' : size(s.freeDisk), 'Free disk'),
      stat(s.unread, 'Unread feedback', s.unread > 0), stat(s.noPoster, 'Missing poster', s.noPoster > 0), stat(s.noVideo, 'No video file', s.noVideo > 0),
      stat(s.tmdb ? 'On' : 'Off', 'TMDB', !s.tmdb), stat(s.announcement ? 'Live' : 'Off', 'Announcement'), stat(dur(s.uptime), 'Server uptime')));
    box.append(h('div', { class: 'card', style: null },
      h('h2', {}, 'Quick tips'),
      h('p', { class: 'sub' }, `Server ${s.node}. Titles with a missing poster or video are flagged in Library. Posters and descriptions are filled from TMDB automatically when it's On.`)));
    box.lastChild.classList.add('tips');
  }

  // ---------- library ----------
  async function library(box) {
    const list = h('div', { class: 'titles' });
    const q = h('input', { class: 'field', type: 'search', placeholder: 'Search your library…', value: state.search });
    box.append(head('Library', h('button', { class: 'btn small', onclick: newTitle }, '+ New title')), q, list);
    let items = (await api('/titles')).items;
    const draw = () => {
      list.replaceChildren();
      const f = q.value.trim().toLowerCase();
      const shown = items.filter((t) => !f || (t.title + ' ' + t.folder + ' ' + t.year).toLowerCase().includes(f));
      if (!shown.length) { list.append(h('div', { class: 'empty' }, items.length ? 'No match.' : 'No titles yet. Tap “+ New title”, then upload a video.')); return; }
      for (const t of shown) {
        list.append(h('button', { class: 'title-card', onclick: () => editor(t.folder, async () => { items = (await api('/titles')).items; draw(); }) },
          h('div', { class: 'thumb' }, t.poster ? h('img', { src: fileUrl(t.folder, t.poster), alt: '', loading: 'lazy' }) : 'no poster'),
          h('div', { class: 't' }, h('b', {}, t.title), h('small', {}, [t.year, t.type === 'series' ? 'Series' : 'Movie', size(t.size)].filter(Boolean).join(' · ')),
            h('div', { class: 'tags' },
              !t.videos ? h('span', { class: 'pill warn' }, 'no video') : h('span', { class: 'pill ok' }, t.videos + (t.videos === 1 ? ' video' : ' videos')),
              !t.poster ? h('span', { class: 'pill warn' }, 'no poster') : null,
              t.featured ? null : h('span', { class: 'pill' }, 'not featured'),
              t.tmdb ? h('span', { class: 'pill new' }, 'TMDB ' + t.tmdb) : null))));
      }
    };
    q.addEventListener('input', () => { state.search = q.value; draw(); });
    draw();
    function newTitle() {
      const name = h('input', { class: 'field', placeholder: 'Folder name, e.g. My Trip 2026', maxlength: '120' });
      const type = h('select', { class: 'field' }, h('option', { value: 'movie' }, 'Movie'), h('option', { value: 'series' }, 'Series (episodes in sub-folders)'));
      const status = h('div', { class: 'status' });
      const m = modal('New title', h('div', {}, name, type,
        h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
          try {
            const r = await api('/titles', { method: 'POST', body: { folder: name.value, type: type.value, title: name.value.trim() } });
            m.close(); editor(r.folder, async () => { items = (await api('/titles')).items; draw(); });
          } catch (e) { status.className = 'status err'; status.textContent = e.message; }
        } }, 'Create')), status));
      setTimeout(() => name.focus(), 50);
    }
  }

  function modal(title, body, onClose) {
    const overlay = h('div', { class: 'overlay' });
    const close = () => { overlay.remove(); document.body.style.overflow = ''; if (onClose) onClose(); };
    overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
    overlay.append(h('div', { class: 'modal', role: 'dialog', 'aria-label': title },
      h('div', { class: 'modal-head' }, h('h2', {}, title), h('button', { class: 'btn flat small', onclick: close, 'aria-label': 'Close' }, '✕')), body));
    document.body.append(overlay);
    return { close };
  }

  async function editor(folder, onClose) {
    const body = h('div', {});
    const m = modal(folder, body, onClose);
    const queue = h('div', { class: 'up' });   // kept between reloads so finished uploads stay visible
    let busy = 0;
    const load = async () => {
      let t;
      try { t = await api('/titles/' + encodeURIComponent(folder)); } catch (e) { body.replaceChildren(h('div', { class: 'status err' }, e.message)); return; }
      const info = t.info || {};
      const f = {
        title: h('input', { class: 'field', value: info.title || '', placeholder: folder }),
        year: h('input', { class: 'field', value: info.year || '', inputmode: 'numeric', maxlength: '4', placeholder: '2026' }),
        type: h('select', { class: 'field' }, h('option', { value: '' }, 'Auto'), h('option', { value: 'movie' }, 'Movie'), h('option', { value: 'series' }, 'Series')),
        genre: h('input', { class: 'field', value: info.genre || '', placeholder: 'Drama • Comedy' }),
        tmdb: h('input', { class: 'field', value: info.tmdb || '', placeholder: 'e.g. 603 or tv/1399 (optional)' }),
        description: h('textarea', { class: 'field', placeholder: 'Leave empty to use the TMDB description' }, info.description || ''),
        featured: h('input', { type: 'checkbox', checked: info.featured !== false }),
      };
      f.type.value = info.type || '';
      const status = h('div', { class: 'status' });
      const save = h('button', { class: 'btn', onclick: async () => {
        try {
          await api(`/titles/${encodeURIComponent(folder)}/info`, { method: 'PUT', body: {
            title: f.title.value, year: f.year.value, type: f.type.value, genre: f.genre.value, tmdb: f.tmdb.value, description: f.description.value, featured: f.featured.checked } });
          status.className = 'status ok'; status.textContent = 'Saved ✓';
        } catch (e) { status.className = 'status err'; status.textContent = e.message; }
      } }, 'Save details');

      // upload area
      const sub = h('input', { class: 'field', placeholder: t.type === 'series' ? 'Episode folder, e.g. E01 Arrival' : 'Episode folder (leave empty for a movie)', maxlength: '100' });
      const picker = h('input', { type: 'file', multiple: true, class: 'hidden', accept: '.mp4,.m4v,.mkv,.webm,.mov,.m3u8,.ts,.m4s,.jpg,.jpeg,.png,.webp,.vtt,.srt' });
      const posterPicker = h('input', { type: 'file', class: 'hidden', accept: '.jpg,.jpeg,.png,.webp' });
      const drop = h('div', { class: 'drop', tabindex: '0', role: 'button', onclick: () => picker.click() }, 'Tap or drop video files here (name them 360p.mp4, 720p.mp4, 1080p.mp4 to label the quality)');
      drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('over'); });
      drop.addEventListener('dragleave', () => drop.classList.remove('over'));
      drop.addEventListener('drop', (e) => { e.preventDefault(); drop.classList.remove('over'); send([...e.dataTransfer.files], sub.value.trim()); });
      picker.addEventListener('change', () => { send([...picker.files], sub.value.trim()); picker.value = ''; });
      posterPicker.addEventListener('change', () => {
        const file = posterPicker.files[0]; posterPicker.value = '';
        if (!file) return;
        const ext = (file.name.match(/\.[^.]+$/) || ['.jpg'])[0].toLowerCase();
        send([file], '', 'poster' + ext);
      });
      function send(files, subfolder, rename) {
        for (const file of files) {
          const name = rename || file.name;
          const bar = h('i', {}); const label = h('span', {}, `${name} · ${size(file.size)}`);
          queue.append(h('div', { class: 'item' }, label, h('div', { class: 'bar' }, bar)));
          const xhr = new XMLHttpRequest();
          const q = `?folder=${encodeURIComponent(folder)}&name=${encodeURIComponent(name)}` + (subfolder ? `&sub=${encodeURIComponent(subfolder)}` : '');
          xhr.open('PUT', '/console/api/upload' + q);
          xhr.setRequestHeader('X-Console', '1');
          xhr.upload.onprogress = (e) => { if (e.lengthComputable) bar.style.width = Math.round(e.loaded / e.total * 100) + '%'; };
          xhr.onload = () => {
            busy--;
            if (xhr.status === 201) { bar.style.width = '100%'; label.textContent = `✓ ${name}`; }
            else { let msg = 'failed'; try { msg = JSON.parse(xhr.responseText).error; } catch {} label.textContent = `✗ ${name} — ${msg}`; }
            if (!busy) load();
          };
          xhr.onerror = () => { busy--; label.textContent = `✗ ${name} — connection lost`; };
          busy++;
          xhr.send(file);
        }
      }

      const files = h('div', { class: 'files' }, t.list.length ? t.list.map((x) =>
        h('div', { class: 'file' },
          h('span', { class: 'nm' }, x.path), h('small', {}, size(x.size)),
          x.kind === 'info' ? null : h('button', { class: 'btn danger small', onclick: async () => {
            if (!confirm(`Remove ${x.path}? It moves to media/.trash, you can bring it back from there.`)) return;
            try { await api(`/file?folder=${encodeURIComponent(folder)}&path=${encodeURIComponent(x.path)}`, { method: 'DELETE' }); toast('Removed'); load(); }
            catch (e) { toast(e.message); }
          } }, 'Remove'))) : h('div', { class: 'status' }, 'No files yet.'));

      body.replaceChildren(
        h('div', { class: 'row' },
          h('div', { class: 'thumb' }, t.poster ? h('img', { src: fileUrl(folder, t.poster) + '&t=' + Date.now(), alt: '' }) : 'no poster'),
          h('div', {}, h('div', { class: 'pill' }, size(t.size)), h('div', { class: 'status' }, `${t.videos} video file${t.videos === 1 ? '' : 's'}`),
            h('button', { class: 'btn flat small', onclick: () => posterPicker.click() }, 'Set poster'))),
        h('div', { class: 'two' },
          h('label', { class: 'lbl' }, 'Title', f.title), h('label', { class: 'lbl' }, 'Year', f.year),
          h('label', { class: 'lbl' }, 'Type', f.type), h('label', { class: 'lbl' }, 'Genre', f.genre)),
        h('label', { class: 'lbl' }, 'TMDB id', f.tmdb),
        h('label', { class: 'lbl' }, 'Description', f.description),
        h('label', { class: 'check' }, f.featured, 'Show in the big Home carousel'),
        h('div', { class: 'row' }, save), status,
        h('h2', { class: 'section' }, 'Upload'), sub, drop, picker, posterPicker, queue,
        h('h2', { class: 'section' }, 'Files'), files,
        h('div', { class: 'row' }, h('button', { class: 'btn danger', onclick: async () => {
          if (!confirm(`Move “${folder}” to the trash? Nothing is erased — it goes to media/.trash.`)) return;
          try { await api('/titles/' + encodeURIComponent(folder), { method: 'DELETE' }); m.close(); toast('Moved to trash'); } catch (e) { toast(e.message); }
        } }, 'Delete title')));
      body.querySelectorAll('.section').forEach((s) => { s.className = 'section'; });
    };
    load();
  }

  // ---------- feedback ----------
  async function feedback(box) {
    const list = h('div', {});
    box.append(head('Feedback', h('button', { class: 'btn danger small', onclick: async () => {
      if (!confirm('Delete ALL feedback?')) return;
      await api('/feedback', { method: 'DELETE' }); render();
    } }, 'Clear all')), list);
    const items = (await api('/feedback')).items;
    state.unread = items.filter((i) => !i.read).length;
    if (!items.length) { list.append(h('div', { class: 'empty' }, 'No messages yet. Feedback sent from the app (when it uses your server) shows up here.')); return; }
    for (const fb of items) {
      list.append(h('div', { class: 'fb' + (fb.read ? '' : ' unread') },
        h('div', { class: 'meta' }, fb.read ? null : h('span', { class: 'pill new' }, 'new'), ago(fb.time), fb.app ? 'App ' + fb.app : null, fb.contact ? 'From: ' + fb.contact : null),
        h('p', {}, fb.text),
        h('div', { class: 'row' },
          h('button', { class: 'btn flat small', onclick: async () => { await api('/feedback/' + fb.id, { method: 'PUT', body: { read: !fb.read } }); render(); } }, fb.read ? 'Mark unread' : 'Mark read'),
          h('button', { class: 'btn danger small', onclick: async () => { await api('/feedback/' + fb.id, { method: 'DELETE' }); render(); } }, 'Delete'))));
    }
  }

  // ---------- announcement ----------
  async function announce(box) {
    const a = await api('/announcement');
    const f = {
      enabled: h('input', { type: 'checkbox', checked: a.enabled }),
      title: h('input', { class: 'field', value: a.title || '', maxlength: '100', placeholder: 'e.g. New update is out!' }),
      text: h('textarea', { class: 'field', maxlength: '1000', placeholder: 'What do you want to tell everyone?' }, a.text || ''),
      link: h('input', { class: 'field', value: a.link || '', placeholder: 'https://… (optional)' }),
      linkLabel: h('input', { class: 'field', value: a.linkLabel || '', maxlength: '30', placeholder: 'Button text, e.g. Download' }),
    };
    const status = h('div', { class: 'status' });
    box.append(head('Announcement'), h('div', { class: 'card' },
      h('p', { class: 'sub' }, 'Shows once as a pop-up in the app (when it uses your server). Saving again shows it again to everyone.'),
      h('label', { class: 'check' }, f.enabled, 'Show this announcement'),
      h('label', { class: 'lbl' }, 'Title', f.title), h('label', { class: 'lbl' }, 'Message', f.text),
      h('div', { class: 'two' }, h('label', { class: 'lbl' }, 'Link', f.link), h('label', { class: 'lbl' }, 'Button text', f.linkLabel)),
      h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
        try {
          await api('/announcement', { method: 'PUT', body: { enabled: f.enabled.checked, title: f.title.value, text: f.text.value, link: f.link.value, linkLabel: f.linkLabel.value } });
          status.className = 'status ok'; status.textContent = 'Saved ✓';
        } catch (e) { status.className = 'status err'; status.textContent = e.message; }
      } }, 'Save')), status));
  }

  // ---------- settings ----------
  async function settings(box) {
    const s = await api('/settings');
    const key = h('input', { class: 'field', type: 'password', placeholder: s.tmdb.tmdbEnabled ? 'Key saved (' + s.tmdb.tmdbKeyMasked + ') — paste to replace' : 'TMDB API key', autocomplete: 'off' });
    const lang = h('select', { class: 'field' }, [['en-US', 'English'], ['bn-BD', 'বাংলা'], ['hi-IN', 'हिन्दी'], ['ko-KR', '한국어'], ['ar-SA', 'العربية'], ['es-ES', 'Español']].map(([c, n]) => h('option', { value: c }, n)));
    lang.value = s.tmdb.language;
    const st = h('div', { class: 'status' });
    const cur = h('input', { class: 'field', type: 'password', placeholder: 'Current password', autocomplete: 'current-password' });
    const nxt = h('input', { class: 'field', type: 'password', placeholder: 'New password (8+ characters)', autocomplete: 'new-password' });
    const st2 = h('div', { class: 'status' });
    box.append(head('Settings'),
      h('div', { class: 'card' }, h('h2', {}, 'TMDB'), h('p', { class: 'sub' }, 'Used by the web app and to fill posters & descriptions for your own titles.' + (s.tmdb.tmdbFromEnv ? ' (Currently set by TMDB_API_KEY.)' : '')),
        key, lang, h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
          st.className = 'status'; st.textContent = 'Saving…';
          try {
            const body = { language: lang.value }; if (key.value.trim()) body.tmdbKey = key.value.trim();
            await api('/settings', { method: 'POST', body }); key.value = ''; st.className = 'status ok'; st.textContent = 'Saved ✓';
          } catch (e) { st.className = 'status err'; st.textContent = e.message; }
        } }, 'Save TMDB')), st),
      h('div', { class: 'card' }, h('h2', {}, 'Admin password'),
        s.passwordFromEnv ? h('p', { class: 'sub' }, 'The password is set by ADMIN_PASSWORD when the server starts.') : [cur, nxt,
          h('div', { class: 'row' }, h('button', { class: 'btn', onclick: async () => {
            try { await api('/password', { method: 'POST', body: { current: cur.value, next: nxt.value } }); cur.value = ''; nxt.value = ''; st2.className = 'status ok'; st2.textContent = 'Password changed ✓'; }
            catch (e) { st2.className = 'status err'; st2.textContent = e.message; }
          } }, 'Change password')), st2]),
      h('div', { class: 'card' }, h('h2', {}, 'Server'),
        h('div', { class: 'file' }, h('span', { class: 'nm' }, 'Media folder'), h('small', {}, s.mediaDir)),
        h('div', { class: 'file' }, h('span', { class: 'nm' }, 'Port'), h('small', {}, s.port)),
        h('div', { class: 'file' }, h('span', { class: 'nm' }, 'App token (APP_TOKEN)'), h('small', {}, s.appToken ? 'on' : 'off — anyone on your network can read the library')),
        h('div', { class: 'file' }, h('span', { class: 'nm' }, 'Public address'), h('small', {}, s.publicBase || 'taken from each request'))));
  }

  // ---------- render ----------
  const PAGES = { dashboard, library, feedback, announce, settings };
  async function render() {
    const app = $('#app');
    if (!state.info) return;
    if (!state.user) { app.replaceChildren(loginView()); return; }
    const box = h('div', {});
    app.replaceChildren(shell(box));
    try { await PAGES[state.tab](box); }
    catch (e) { box.append(h('div', { class: 'status err' }, e.message)); }
    // refresh the unread badge
    const badge = $('.nav button:nth-child(3)');
    if (badge && !badge.querySelector('.n') && state.unread) badge.append(h('span', { class: 'n' }, state.unread));
  }

  async function boot() {
    try { state.info = await api('/state'); state.user = state.info.loggedIn; }
    catch (e) { $('#app').replaceChildren(h('div', { class: 'message empty' }, 'Cannot reach the server: ' + e.message)); return; }
    render();
  }
  boot();
})();
