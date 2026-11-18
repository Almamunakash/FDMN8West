/* Camp- 8 West · FDMN Database — web app (works with the same API as the Android app) */
'use strict';

const API_URL = 'api/index.php';
const APP_VERSION = '1.0';
const AGE_GROUPS = [['u5', '0–5 yrs'], ['c6', '6–12 yrs'], ['c13', '13–17 yrs'], ['e60', '60+ yrs']];
const BLOCK_TONES = ['green', 'blue', 'purple', 'orange', 'pink', 'teal', 'red', 'indigo'];

// ---------------- small helpers ----------------
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (n) => Number(n || 0).toLocaleString('en-US');
const store = {
  get(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } },
  set(k, v) { try { if (v) localStorage.setItem(k, v); else localStorage.removeItem(k); } catch (e) { /* private mode */ } },
};
const ageLabel = (code) => (AGE_GROUPS.find((a) => a[0] === code) || [code, code])[1];
const sexText = (s) => (s === 'M' ? 'Male' : s === 'F' ? 'Female' : s || '');
const sexTone = (s) => (s === 'F' ? 'pink' : s === 'M' ? 'blue' : 'green');
const wave = () => '<svg class="wave" viewBox="0 0 100 100" preserveAspectRatio="none"><path d="M42 100C68 100 78 55 100 42V100Z" fill="currentColor" opacity=".22"/><path d="M58 100C80 100 88 76 100 66V100Z" fill="currentColor" opacity=".55"/></svg>';
const bigWave = (c) => `<svg class="wave" viewBox="0 0 100 100" preserveAspectRatio="none"><defs><linearGradient id="bw${c}" x1="0" x2="1"><stop offset="0" stop-color="#${c}" stop-opacity="0"/><stop offset="1" stop-color="#${c}" stop-opacity=".6"/></linearGradient></defs><path d="M35 100C60 95 75 55 100 35V100Z" fill="url(#bw${c})"/></svg>`;

// ---------------- state ----------------
const S = {
  token: store.get('fdmn_token'),
  user: (() => { try { return JSON.parse(store.get('fdmn_user') || 'null'); } catch (e) { return null; } })(),
  stats: {},
  meta: { blocks: [], relations: [], majhis: [] },
  version: 0,
  openBlocks: new Set(),
  rec: { filters: {}, items: [], total: 0, loading: false, error: null, key: null, gen: 0, scroll: 0 },
  preset: null,
  lastRoute: '',
};
const isAdmin = () => S.user && S.user.role === 'admin';

// ---------------- API ----------------
async function api(action, body = {}) {
  let res;
  const payload = S.token ? Object.assign({}, body, { token: S.token }) : body;
  try {
    res = await fetch(API_URL + '?a=' + encodeURIComponent(action), {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, S.token ? { 'X-Auth-Token': S.token } : {}),
      body: JSON.stringify(payload),
    });
  } catch (e) {
    throw new Error('No internet connection, or the server could not be reached.');
  }
  let j;
  try { j = await res.json(); } catch (e) { throw new Error(`The server sent an unexpected reply (code ${res.status}).`); }
  if (!j.ok) {
    if (j.code === 'auth' && S.token) { signOut(false); toast('Please sign in again.'); }
    const err = new Error(j.error || 'Something went wrong.');
    err.code = j.code;
    throw err;
  }
  return j;
}

async function loadStats() {
  try { S.stats = (await api('stats')).stats || {}; } catch (e) { /* keep old */ }
  updateNav();
}
async function loadMeta() {
  try {
    const [b, o] = await Promise.all([api('blocks'), api('options')]);
    S.meta = { blocks: b.blocks || [], relations: o.relations || [], majhis: o.majhis || [] };
  } catch (e) { /* keep old */ }
}
function changed() { S.version++; loadMeta(); loadStats(); }
const mainNames = () => S.meta.blocks.map((b) => b.name).filter(Boolean);
const subNames = (main) => [...new Set((main ? S.meta.blocks.filter((b) => b.name === main) : S.meta.blocks).flatMap((b) => b.subs.map((s) => s.name)).filter(Boolean))];

function describe(f) {
  const p = [];
  if (f.main) p.push('Main Block ' + f.main);
  if (f.sub) p.push('Sub-Block ' + f.sub);
  if (f.sex) p.push(sexText(f.sex));
  if (f.age) p.push((f.age === 'e60' ? 'Elderly ' : 'Children ') + ageLabel(f.age));
  if (f.relation) p.push('Relation ' + f.relation);
  if (f.majhi) p.push('Majhi ' + f.majhi);
  if (f.q) p.push(`Search "${f.q}"`);
  return p.length ? p.join(' · ') : 'All records';
}
const filtersActive = (f) => Object.values(f).some((v) => v);

// ---------------- UI helpers ----------------
function toast(msg) {
  const old = $('.toast'); if (old) old.remove();
  const t = document.createElement('div'); t.className = 'toast'; t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3200);
}
function modal(html, onMount) {
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `<div class="modal">${html}</div>`;
  document.body.appendChild(bg);
  const close = () => bg.remove();
  bg.addEventListener('click', (e) => { if (e.target === bg && !bg.dataset.busy) close(); });
  if (onMount) onMount(bg.firstElementChild, close, bg);
  return close;
}
function confirmBox(title, text, ok = 'Yes', danger = false) {
  return new Promise((resolve) => {
    modal(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="actions"><button class="btn ghost" data-no>Cancel</button><button class="btn ${danger ? 'danger' : ''}" data-yes>${esc(ok)}</button></div>`, (m, close) => {
      $('[data-no]', m).onclick = () => { close(); resolve(false); };
      $('[data-yes]', m).onclick = () => { close(); resolve(true); };
    });
  });
}
function topbar(title, sub, opts = {}) {
  return `<header class="topbar">
    ${opts.back ? `<button class="iconbtn" data-back title="Back">${ic('ArrowBack')}</button>` : ''}
    <div class="titles"><h1>${esc(title)}</h1>${sub != null ? `<div class="sub" id="topSub">${esc(sub)}</div>` : ''}</div>
    ${opts.actions || ''}
  </header>`;
}
let navCount = 0;
function back(fallback) {
  if (navCount > 1) history.back(); else go(fallback || 'home');
}
function ageYears(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
  if (!m) return null;
  const n = new Date();
  let y = n.getFullYear() - +m[1];
  if (n.getMonth() + 1 < +m[2] || (n.getMonth() + 1 === +m[2] && n.getDate() < +m[3])) y--;
  return y;
}
const go = (path) => { location.hash = '#/' + path; };
const loading = () => '<div class="center"><span class="spinner"></span></div>';
const errorBox = (msg) => `<div class="center"><p class="muted">${esc(msg)}</p><button class="btn" data-retry>Try again</button></div>`;
const emptyBox = (t, s) => `<div class="center empty"><h3>${esc(t)}</h3><p class="muted">${esc(s)}</p></div>`;
function busyBtn(btn, busy, text) {
  if (!btn) return;
  if (busy) { btn.dataset.label = btn.innerHTML; btn.disabled = true; btn.innerHTML = '<span class="spinner" style="width:20px;height:20px;border-width:2px;border-color:rgba(255,255,255,.4);border-top-color:#fff"></span>' + (text ? ' ' + esc(text) : ''); }
  else { btn.disabled = false; if (btn.dataset.label) btn.innerHTML = btn.dataset.label; }
}

// ---------------- routing ----------------
const ROOT = { home: 'home', camp: 'home', records: 'records', record: 'records', family: 'records', edit: 'records', new: 'records', messages: 'messages', more: 'more', users: 'more', import: 'more', password: 'more' };
function route() {
  const h = location.hash.replace(/^#\/?/, '');
  const i = h.indexOf('/');
  const name = (i < 0 ? h : h.slice(0, i)) || 'home';
  const arg = i < 0 ? '' : decodeURIComponent(h.slice(i + 1));
  return { name: ROOT[name] ? name : 'home', arg };
}
window.addEventListener('hashchange', render);

function render() {
  const r = route();
  if (S.lastRoute === 'records') S.rec.scroll = window.scrollY;
  navCount++;
  if (!S.token || !S.user) { renderAuth(); S.lastRoute = ''; return; }
  ensureShell();
  updateNav(ROOT[r.name]);
  const main = $('#main');
  main.innerHTML = '';
  window.scrollTo(0, 0);
  const pages = { home: pageHome, camp: pageCamp, records: pageRecords, record: pageDetail, family: pageFamily, edit: pageEdit, new: pageEdit, messages: pageMessages, more: pageMore, users: pageUsers, import: pageImport, password: pagePassword };
  S.lastRoute = r.name;
  (pages[r.name] || pageHome)(main, r.arg);
}

// ---------------- shell (side menu on PC, bottom menu on phone) ----------------
const NAV = [['home', 'Home', 'Home'], ['records', 'Records', 'List'], ['messages', 'Messages', 'MailOutline'], ['more', 'More', 'Menu']];
function ensureShell() {
  if ($('#main')) return;
  const navBtns = NAV.map(([k, label, icon]) => `<button class="navbtn" data-nav="${k}">${ic(icon)}<span>${label}</span><i class="badge hidden" data-badge="${k}"></i></button>`).join('');
  $('#app').innerHTML = `
    <aside class="sidebar">
      <img class="logo" src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West">
      <div class="appname">Camp- 8 West</div>
      ${navBtns}
      <div class="who" id="who"></div>
    </aside>
    <main class="main" id="main"></main>
    <nav class="bottomnav">${navBtns}</nav>`;
  $$('[data-nav]').forEach((b) => { b.onclick = () => go(b.dataset.nav); });
  $('#main').addEventListener('click', (e) => { if (e.target.closest('[data-back]')) back(ROOT[route().name]); });
}
function updateNav(active) {
  if (active) $$('[data-nav]').forEach((b) => b.classList.toggle('on', b.dataset.nav === active));
  const st = S.stats || {};
  const counts = { messages: isAdmin() ? st.open_messages : st.unread_replies, more: isAdmin() ? st.pending_users : 0 };
  $$('[data-badge]').forEach((b) => {
    const n = counts[b.dataset.badge] || 0;
    b.textContent = n > 99 ? '99+' : n;
    b.classList.toggle('hidden', !n);
  });
  const who = $('#who');
  if (who && S.user) who.innerHTML = `<b>${esc(S.user.name)}</b>${isAdmin() ? '👑 Admin' : 'Viewer'} · ${esc(S.user.email)}`;
}

// ---------------- sign in / register ----------------
function renderAuth(mode = 'login', notice = '') {
  $('#app').innerHTML = `
  <div class="auth"><div class="box">
    <img class="logo" src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West">
    <h1>Camp- 8 West</h1>
    <p class="tagline">About the Camp, FDMN and Activities.</p>
    <form class="form" id="authForm" autocomplete="on">
      ${mode === 'login' ? `
        <h2>Sign in</h2>
        ${notice ? `<div class="note">${esc(notice)}</div>` : ''}
        <label class="field"><span>Email</span><input class="input" name="email" type="email" autocomplete="username" value="${esc(store.get('fdmn_email'))}" required></label>
        <label class="field"><span>Password</span><div class="pw"><input class="input" name="password" type="password" autocomplete="current-password" required><button type="button" data-show>Show</button></div></label>
        <div class="err" id="authErr"></div>
        <button class="btn block" type="submit">Sign in</button>
        <p style="text-align:center;margin:14px 0 0"><button type="button" class="btn ghost" data-mode="register">New user?&nbsp;<b>Create an account</b></button></p>
      ` : `
        <h2>Create account</h2>
        <p class="muted" style="margin:-6px 0 12px;font-size:13px">An Admin must approve your account before you can sign in.</p>
        <label class="field"><span>Full name</span><input class="input" name="name" autocomplete="name" required></label>
        <label class="field"><span>Email</span><input class="input" name="email" type="email" autocomplete="email" required></label>
        <label class="field"><span>Password (min 6)</span><input class="input" name="password" type="password" autocomplete="new-password" required></label>
        <label class="field"><span>Confirm password</span><input class="input" name="password2" type="password" autocomplete="new-password" required></label>
        <div class="err" id="authErr"></div>
        <button class="btn block" type="submit">Create account</button>
        <p style="text-align:center;margin:14px 0 0"><button type="button" class="btn ghost" data-mode="login">Already have an account?&nbsp;<b>Sign in</b></button></p>
      `}
    </form>
    <p class="foot">Forgot your password? Ask an Admin to reset it.</p>
  </div></div>`;
  const f = $('#authForm');
  $$('[data-mode]').forEach((b) => { b.onclick = () => renderAuth(b.dataset.mode); });
  const sh = $('[data-show]');
  if (sh) sh.onclick = () => { const p = f.password; p.type = p.type === 'password' ? 'text' : 'password'; sh.textContent = p.type === 'password' ? 'Show' : 'Hide'; };
  f.onsubmit = async (e) => {
    e.preventDefault();
    const btn = $('button[type=submit]', f);
    const err = $('#authErr');
    err.textContent = '';
    const v = Object.fromEntries(new FormData(f));
    if (mode === 'register') {
      if ((v.name || '').trim().length < 2) { err.textContent = 'Please enter your full name.'; return; }
      if ((v.password || '').length < 6) { err.textContent = 'Password must be at least 6 characters.'; return; }
      if (v.password !== v.password2) { err.textContent = 'The two passwords do not match.'; return; }
    }
    busyBtn(btn, true);
    try {
      if (mode === 'login') {
        const r = await api('login', { email: v.email.trim(), password: v.password });
        signedIn(r.token, r.user);
      } else {
        const r = await api('register', { name: v.name.trim(), email: v.email.trim(), password: v.password });
        store.set('fdmn_email', v.email.trim());
        renderAuth('login', r.message || 'Account created. Wait for Admin approval.');
      }
    } catch (ex) {
      err.textContent = ex.message;
      busyBtn(btn, false);
    }
  };
}

function signedIn(token, user) {
  S.token = token; S.user = user;
  store.set('fdmn_token', token); store.set('fdmn_user', JSON.stringify(user)); store.set('fdmn_email', user.email);
  S.rec = { filters: {}, items: [], total: 0, loading: false, error: null, key: null, gen: 0, scroll: 0 };
  $('#app').innerHTML = '';
  if (location.hash && location.hash !== '#/' && location.hash !== '#/home') go('home'); else render();
  loadMeta().then(() => { if (route().name === 'home') render(); });
  loadStats().then(() => { if (route().name === 'home') render(); });
  showWelcome();
}
function signOut(callServer = true) {
  if (callServer && S.token) api('logout').catch(() => {});
  S.token = ''; S.user = null; S.stats = {};
  store.set('fdmn_token', ''); store.set('fdmn_user', '');
  $('#app').innerHTML = '';
  renderAuth();
}

// ---------------- welcome popup (5 seconds, X to close) ----------------
function welcomeCard() {
  const u = S.user;
  return `<div class="bigcard welcomecard">${bigWave('49D17D')}
    <div class="avatar"><div class="circle">${ic('Person')}</div><div class="role">${isAdmin() ? '👑 Admin' : '👤 Viewer'}</div></div>
    <div class="vline"></div>
    <div style="flex:1;min-width:0"><h3>Welcome, ${esc(u.name)}</h3><p>${isAdmin() ? 'Admin · You can add, edit and delete records.' : 'Viewer · You can search and view records.'}</p></div>
  </div>`;
}
function showWelcome() {
  if (!S.user || $('.welcome-pop')) return;
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `<div class="welcome-pop"><button class="x" aria-label="Close">${ic('Close')}</button>${welcomeCard()}</div>`;
  document.body.appendChild(bg);
  const close = () => bg.remove();
  $('.x', bg).onclick = close;
  bg.onclick = (e) => { if (e.target === bg) close(); };
  setTimeout(close, 5000);
}

// ---------------- HOME ----------------
function pageHome(main) {
  const st = S.stats || {};
  const stat = (tone, icon, val, label, link) => `<div class="wcard stat t-${tone} ${link ? 'click' : ''}" ${link ? `data-link='${esc(JSON.stringify(link))}'` : ''}>${wave()}<div class="in"><div class="tile">${ic(icon)}</div><div><div class="val">${fmt(val)}</div><div class="lbl">${label}</div></div></div></div>`;
  const age = (tone, icon, val, label, code) => `<div class="wcard agecard click t-${tone}" data-link='${esc(JSON.stringify({ age: code }))}'>${wave()}<div class="in"><div class="tile sm">${ic(icon)}</div><div class="val">${fmt(val)}</div><div class="lbl">${label}</div></div></div>`;
  const alerts = [];
  if (isAdmin() && st.pending_users > 0) alerts.push([`${st.pending_users} new user(s) waiting for your approval`, 'users']);
  if (isAdmin() && st.open_messages > 0) alerts.push([`${st.open_messages} open message request(s)`, 'messages']);
  if (!isAdmin() && st.unread_replies > 0) alerts.push([`The Admin replied to ${st.unread_replies} of your request(s)`, 'messages']);
  const blocks = S.meta.blocks.map((mb, i) => {
    const tone = BLOCK_TONES[i % BLOCK_TONES.length];
    const open = S.openBlocks.has(mb.name);
    const subs = open ? `<div class="subs">
        <div class="subrow" data-link='${esc(JSON.stringify({ main: mb.name }))}'><i class="dot"></i><div class="grow"><b>All of Main Block ${esc(mb.name)}</b><small>${fmt(mb.count)} people · ${fmt(mb.families)} families</small></div>${ic('KeyboardArrowRight')}</div>
        ${mb.subs.map((sb) => `<div class="subrow" data-link='${esc(JSON.stringify({ main: mb.name, sub: sb.name }))}'><i class="dot"></i><div class="grow"><b>Sub-Block ${esc(sb.name || '(none)')}</b><small>${fmt(sb.count)} people · ${fmt(sb.families)} families</small></div>${ic('KeyboardArrowRight')}</div>`).join('')}
      </div>` : '';
    return `<div class="wcard block t-${tone}">${wave()}<div class="in">
      <div class="head" data-toggle="${esc(mb.name)}">
        <div class="tile">${esc((mb.name || '?').slice(0, 2))}</div><div class="vline"></div>
        <div class="info"><b>Main Block ${esc(mb.name || '(none)')}</b>
          <div class="mini"><span>${ic('Groups')} ${fmt(mb.count)} people</span><span>·</span><span>${ic('FamilyRestroom')} ${fmt(mb.families)} families</span></div>
          <div class="mini" style="margin-top:3px"><span>${ic('Layers')} ${mb.subs.length} sub-blocks</span></div></div>
        <div class="chev">${ic(open ? 'KeyboardArrowUp' : 'KeyboardArrowDown')}</div>
      </div>${subs}</div></div>`;
  }).join('');

  main.innerHTML = `
    <div class="hero"><button class="iconbtn refresh" data-refresh title="Refresh">${ic('Refresh')}</button>
      <div class="logocard"><img src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West"></div></div>
    <div class="page pad" style="padding-top:2px">
      <div class="bigcard campcard" data-camp>${bigWave('8FB0FF')}
        <div class="em"><img src="assets/emblem.png" alt=""></div><div class="vline"></div>
        <div style="flex:1;min-width:0"><h3>Camp Profile</h3><p>About the Camp, FDMN and Activities</p></div>
        ${ic('KeyboardArrowRight')}
      </div>
      <div class="stats">
        ${stat('green', 'Groups', st.total, 'People', {})}
        ${stat('orange', 'FamilyRestroom', st.families, 'Families', null)}
        ${stat('blue', 'Man', st.male, 'Male', { sex: 'M' })}
        ${stat('pink', 'Woman', st.female, 'Female', { sex: 'F' })}
      </div>
      <div class="section-title">${ic('Groups')}<h2>Age groups</h2><i class="line"></i></div>
      <div class="hscroll">
        ${age('purple', 'ChildFriendly', st.under5, '0–5 yrs', 'u5')}
        ${age('teal', 'ChildCare', st.age6to12, '6–12 yrs', 'c6')}
        ${age('indigo', 'EmojiPeople', st.age13to17, '13–17 yrs', 'c13')}
        ${age('red', 'Elderly', st.elderly, 'Elderly 60+', 'e60')}
      </div>
      ${alerts.map(([t, l]) => `<div class="alert" data-go="${l}">${ic('Warning')}<span class="grow">${esc(t)}</span>${ic('KeyboardArrowRight')}</div>`).join('')}
      <div class="section-title">${ic('Layers')}<h2>Blocks</h2><i class="line"></i></div>
      <div class="blocks">${blocks || emptyBox('No records yet', isAdmin() ? 'Go to More → Import from Excel to upload your beneficiary list.' : 'The Admin has not added any records yet.')}</div>
    </div>`;

  $('[data-camp]', main).onclick = () => go('camp');
  $('[data-refresh]', main).onclick = async () => { toast('Refreshing…'); await Promise.all([loadStats(), loadMeta()]); if (route().name === 'home') render(); };
  $$('[data-link]', main).forEach((el) => { el.onclick = () => openRecords(JSON.parse(el.dataset.link)); });
  $$('[data-go]', main).forEach((el) => { el.onclick = () => go(el.dataset.go); });
  $$('[data-toggle]', main).forEach((el) => {
    el.onclick = () => { const k = el.dataset.toggle; if (S.openBlocks.has(k)) S.openBlocks.delete(k); else S.openBlocks.add(k); const y = window.scrollY; render(); window.scrollTo(0, y); };
  });
}
function openRecords(f) {
  S.rec.filters = Object.assign({}, f || {});
  S.rec.key = null;
  go('records');
}

// ---------------- CAMP PROFILE ----------------
function pageCamp(main) {
  main.innerHTML = topbar('Camp Profile', 'About the Camp, FDMN and Activities', { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto">
      <img src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West" style="width:100%;max-width:640px;display:block;margin:4px auto 16px">
      <div class="wcard t-camp">${wave()}<div class="in" style="padding:24px;text-align:center">
        <div class="tile" style="margin:0 auto 12px">${ic('Info')}</div>
        <h2 style="margin:0 0 6px;color:var(--camp)">Camp- 8 West</h2>
        <p class="muted" style="margin:0;font-size:16px">Camp information will be added soon.</p>
      </div></div>
    </div>`;
}

// ---------------- RECORDS ----------------
function personCard(p, sl) {
  const tone = sexTone(p.sex);
  const block = [p.main_block, p.sub_block].filter(Boolean).join(' / ');
  return `<div class="wcard pcard click t-${tone}" data-id="${p.id}">${wave()}<div class="in">
    <div class="slbox"><small>SL</small><b>${fmt(sl)}</b></div>
    <div class="body">
      <div class="nm"><span>${esc(p.name)}</span>${p.relation ? `<span class="tag" style="--c:#C27410">${esc(p.relation)}</span>` : ''}</div>
      <div class="l2">${[sexText(p.sex), XL.ageText(p.dob), XL.displayDate(p.dob)].filter(Boolean).map(esc).join(' · ')}</div>
      <div class="l3">FCN ${esc(p.fcn || '—')} · ${esc(block || 'No block')}${p.majhi ? ' · ' + esc(p.majhi) : ''}</div>
    </div></div></div>`;
}
function recordsTable(items) {
  return `<table class="grid"><thead><tr><th>SL</th><th>Name</th><th>Sex</th><th class="c-opt">Age</th><th>FCN</th><th>Date of birth</th><th>Relation</th><th>Main Block</th><th>Sub-Block</th><th>Majhi</th><th class="c-opt">Remarks</th></tr></thead><tbody>
    ${items.map((p, i) => `<tr data-id="${p.id}"><td>${fmt(i + 1)}</td><td class="name">${esc(p.name)}</td><td class="sx-${esc(p.sex)}">${esc(p.sex)}</td><td class="c-opt">${esc(XL.ageText(p.dob))}</td><td>${esc(p.fcn)}</td><td>${esc(XL.displayDate(p.dob))}</td><td>${esc(p.relation)}</td><td>${esc(p.main_block)}</td><td>${esc(p.sub_block)}</td><td>${esc(p.majhi)}</td><td class="rem c-opt">${esc(p.remarks)}</td></tr>`).join('')}
  </tbody></table>`;
}
function recKey() { return JSON.stringify(S.rec.filters) + '|' + S.version; }

async function loadRecords(reset) {
  const R = S.rec;
  if (!reset && (R.loading || R.items.length >= R.total)) return;
  const gen = reset ? ++R.gen : R.gen;
  R.loading = true; R.error = null;
  if (reset) { R.items = []; R.total = 0; }
  renderRecList();
  try {
    const r = await api('list', Object.assign({}, R.filters, { per: 100, offset: reset ? 0 : R.items.length }));
    if (gen !== R.gen) return;
    const have = new Set(R.items.map((p) => p.id));
    const more = (r.rows || []).filter((p) => !have.has(p.id));
    R.items = R.items.concat(more);
    R.total = more.length ? r.total : R.items.length;
    R.key = recKey();
  } catch (e) {
    if (gen === R.gen) R.error = e.message;
  } finally {
    if (gen === R.gen) { R.loading = false; renderRecList(); }
  }
}

let recObserver = null;
function renderRecList() {
  const box = $('#recList');
  if (!box) return;
  const R = S.rec;
  const sub = $('#topSub');
  if (sub) sub.textContent = `${fmt(R.total)} found · ${describe(R.filters)}`;
  const cnt = $('#recCount');
  if (cnt) cnt.innerHTML = R.items.length || !R.loading ? `<b>${fmt(R.total)}</b> records · ${esc(describe(R.filters))}${R.items.length < R.total ? ` · showing ${fmt(R.items.length)}` : ''}` : 'Loading…';
  if (!R.items.length) {
    box.innerHTML = R.loading ? loading() : R.error ? errorBox(R.error)
      : emptyBox('No records found', filtersActive(R.filters) ? 'Try a different search or clear the filters.' : 'No records have been added yet.');
    const rt = $('[data-retry]', box); if (rt) rt.onclick = () => loadRecords(true);
    return;
  }
  const more = R.items.length < R.total;
  box.innerHTML = `
    <div class="tablewrap">${recordsTable(R.items)}</div>
    <div class="plist records">${R.items.map((p, i) => personCard(p, i + 1)).join('')}</div>
    ${more || R.loading ? `<div class="loadmore" id="sentinel">${R.error ? `<button class="btn outline small" data-more>Load more</button>` : '<span class="spinner"></span>'}</div>` : ''}`;
  $$('[data-id]', box).forEach((el) => { el.onclick = () => go('record/' + el.dataset.id); });
  const lm = $('[data-more]', box); if (lm) lm.onclick = () => { R.error = null; loadRecords(false); };
  if (recObserver) recObserver.disconnect();
  const sen = $('#sentinel');
  if (sen && !R.error && 'IntersectionObserver' in window) {
    recObserver = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) loadRecords(false); }, { rootMargin: '600px' });
    recObserver.observe(sen);
  }
}

function pageRecords(main) {
  const R = S.rec, f = R.filters;
  const opt = (v, label, cur) => `<option value="${esc(v)}" ${v === cur ? 'selected' : ''}>${esc(label)}</option>`;
  const sel = (key, allLabel, options) => `<select class="input ${f[key] ? 'set' : ''}" data-f="${key}">${opt('', allLabel, f[key] || '')}${options.map(([v, l]) => opt(v, l, f[key] || '')).join('')}</select>`;
  main.innerHTML = topbar('Records', `${fmt(R.total)} found · ${describe(f)}`, {
    actions: `<button class="iconbtn" data-xlsx title="Export to Excel">${ic('Download')}</button><button class="iconbtn" data-print title="Print / Save as PDF">${ic('Print')}</button>`,
  }) + `
    <div class="page pad">
      <div class="toolbar">
        <label class="search">${ic('Search')}<input class="input" id="q" type="search" placeholder="Search name, FCN or Majhi" value="${esc(f.q || '')}"></label>
        <button class="btn outline small" data-xlsx>${ic('Download')} Excel</button>
        <button class="btn outline small" data-print>${ic('Print')} Print / PDF</button>
      </div>
      <div class="filters">
        ${sel('main', 'Main Block: All', mainNames().map((n) => [n, 'Main ' + n]))}
        ${sel('sub', 'Sub-Block: All', subNames(f.main).map((n) => [n, 'Sub ' + n]))}
        ${sel('sex', 'Sex: All', [['M', 'Male'], ['F', 'Female']])}
        ${sel('age', 'Age group: All', AGE_GROUPS)}
        ${sel('relation', 'Relation: All', S.meta.relations.map((r) => [r, r]))}
        ${sel('majhi', 'Majhi: All', S.meta.majhis.map((m) => [m, m]))}
        ${filtersActive(f) ? `<button class="btn ghost small" data-clear>${ic('Close')} Clear</button>` : ''}
      </div>
      <div class="count" id="recCount"></div>
      <div id="recList"></div>
    </div>
    ${isAdmin() ? `<button class="fab" data-add>${ic('Add')} Add</button>` : ''}`;

  let t = null;
  $('#q', main).oninput = (e) => {
    clearTimeout(t);
    t = setTimeout(() => { R.filters = Object.assign({}, R.filters, { q: e.target.value.trim() }); if (!R.filters.q) delete R.filters.q; loadRecords(true); }, 450);
  };
  $$('[data-f]', main).forEach((s) => {
    s.onchange = () => {
      const k = s.dataset.f;
      const nf = Object.assign({}, R.filters, { [k]: s.value });
      if (k === 'main' && nf.sub && !subNames(nf.main).includes(nf.sub)) nf.sub = '';
      Object.keys(nf).forEach((x) => { if (!nf[x]) delete nf[x]; });
      R.filters = nf;
      R.key = null;
      render();
    };
  });
  const cl = $('[data-clear]', main); if (cl) cl.onclick = () => { R.filters = {}; R.key = null; render(); };
  $$('[data-xlsx]', main).forEach((b) => { b.onclick = () => exportExcel(); });
  $$('[data-print]', main).forEach((b) => { b.onclick = () => printList(); });
  const add = $('[data-add]', main); if (add) add.onclick = () => { S.preset = { main_block: R.filters.main || '', sub_block: R.filters.sub || '' }; go('new'); };

  if (R.key !== recKey()) loadRecords(true);
  else { renderRecList(); requestAnimationFrame(() => window.scrollTo(0, R.scroll || 0)); }
}

async function fetchAll(filters, onProgress) {
  const all = [];
  for (;;) {
    const r = await api('list', Object.assign({}, filters, { per: 2000, offset: all.length }));
    all.push(...(r.rows || []));
    onProgress(all.length, r.total);
    if (!(r.rows || []).length || all.length >= r.total) break;
  }
  return all;
}
function progressModal(title) {
  let closeFn;
  modal(`<h3>${esc(title)}</h3><p id="pmText">Starting…</p><div class="progress" id="pmBar"><div></div></div>`, (m, close, bg) => { closeFn = close; bg.dataset.busy = '1'; });
  return {
    set(text, frac) { const t = $('#pmText'); if (t) t.textContent = text; const b = $('#pmBar'); if (b) { b.classList.toggle('indet', frac == null); b.firstElementChild.style.width = frac == null ? '' : Math.round(frac * 100) + '%'; } },
    close() { closeFn(); },
  };
}
function stamp() { const d = new Date(); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}`; }

async function exportExcel() {
  const f = Object.assign({}, S.rec.filters);
  if (!S.rec.total) { toast('Nothing to export.'); return; }
  const pm = progressModal('Export to Excel');
  try {
    const rows = await fetchAll(f, (n, t) => pm.set(`Downloading records… ${fmt(n)} / ${fmt(t)}`, t ? n / t : null));
    pm.set('Creating Excel file…', null);
    await new Promise((r) => setTimeout(r, 30));
    const blob = XL.writeXlsx(
      ['SL', 'Name', 'Sex', 'FCN', 'Date of birth', 'Relation', 'Main Block', 'Sub-Block', 'Majhi', 'Remarks'],
      rows.map((p, i) => [i + 1, p.name, p.sex, p.fcn, XL.displayDate(p.dob), p.relation, p.main_block, p.sub_block, p.majhi, p.remarks]),
      [7, 28, 6, 11, 14, 10, 11, 11, 22, 30],
    );
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `FDMN_8W_${stamp()}.xlsx`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 60000);
    pm.close();
    toast(`Excel file with ${fmt(rows.length)} records downloaded.`);
  } catch (e) { pm.close(); toast(e.message); }
}

async function printList() {
  const f = Object.assign({}, S.rec.filters);
  const total = S.rec.total;
  if (!total) { toast('Nothing to print.'); return; }
  if (total > 5000 && !(await confirmBox('Large list', `This list has ${fmt(total)} records (about ${fmt(Math.ceil(total / 38))} pages). Printing may be slow. Continue?`, 'Continue'))) return;
  const pm = progressModal('Print / Save as PDF');
  try {
    const rows = await fetchAll(f, (n, t) => pm.set(`Downloading records… ${fmt(n)} / ${fmt(t)}`, t ? n / t : null));
    pm.set('Preparing pages…', null);
    const now = new Date().toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
    let pa = $('#printArea');
    if (!pa) { pa = document.createElement('div'); pa.id = 'printArea'; document.body.appendChild(pa); }
    pa.innerHTML = `<div class="phead"><img src="assets/logo_full.png" alt=""><div><h2>Camp- 8 West · Beneficiary list</h2><div class="meta">${esc(describe(f))} · ${fmt(rows.length)} records · Printed ${esc(now)}</div></div></div>
      <table><thead><tr><th>SL</th><th>Name</th><th>Sex</th><th>FCN</th><th>Date of birth</th><th>Relation</th><th>Main Block</th><th>Sub-Block</th><th>Majhi</th><th>Remarks</th></tr></thead><tbody>
      ${rows.map((p, i) => `<tr><td>${i + 1}</td><td>${esc(p.name)}</td><td>${esc(p.sex)}</td><td>${esc(p.fcn)}</td><td>${esc(XL.displayDate(p.dob))}</td><td>${esc(p.relation)}</td><td>${esc(p.main_block)}</td><td>${esc(p.sub_block)}</td><td>${esc(p.majhi)}</td><td>${esc(p.remarks)}</td></tr>`).join('')}
      </tbody></table><div class="pfoot">Confidential — for official use only.</div>`;
    pm.close();
    const img = $('img', pa);
    await new Promise((r) => { if (img.complete) r(); else { img.onload = r; img.onerror = r; } });
    document.body.classList.add('printing');
    const done = () => { document.body.classList.remove('printing'); pa.innerHTML = ''; window.removeEventListener('afterprint', done); };
    window.addEventListener('afterprint', done);
    setTimeout(() => { window.print(); setTimeout(() => { if (document.body.classList.contains('printing') && !window.matchMedia('print').matches) done(); }, 1500); }, 50);
  } catch (e) { pm.close(); toast(e.message); }
}

// ---------------- DETAIL ----------------
async function pageDetail(main, id) {
  main.innerHTML = topbar('Record', '', { back: true }) + loading();
  let p;
  try { p = (await api('get', { id: +id })).row; } catch (e) {
    main.innerHTML = topbar('Record', '', { back: true }) + errorBox(e.message);
    $('[data-retry]', main).onclick = () => render();
    return;
  }
  if (route().name !== 'record') return;
  const tone = sexTone(p.sex);
  const row = (k, v) => `<div class="inforow"><span>${k}</span><b>${esc(v || '—')}</b></div>`;
  main.innerHTML = topbar(p.name, 'FCN ' + p.fcn, {
    back: true,
    actions: isAdmin() ? `<button class="iconbtn" data-edit title="Edit">${ic('Edit')}</button><button class="iconbtn" data-del title="Delete">${ic('Delete')}</button>` : '',
  }) + `
    <div class="page pad"><div class="twocol">
      <div class="wcard t-${tone}">${wave()}<div class="in" style="padding:16px">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
          <div class="tile">${ic('Person')}</div>
          <div><div style="font-size:20px;font-weight:800">${esc(p.name)}</div>
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:4px">
              ${p.sex ? `<span class="tag" style="--c:var(--d)">${esc(sexText(p.sex))}</span>` : ''}
              ${XL.ageText(p.dob) ? `<span class="tag">${esc(XL.ageText(p.dob))}</span>` : ''}
              ${p.relation ? `<span class="tag" style="--c:#C27410">${esc(p.relation)}</span>` : ''}
            </div></div>
        </div>
        ${row('Name', p.name)}${row('Sex', sexText(p.sex))}${row('FCN', p.fcn)}${row('Date of birth', XL.displayDate(p.dob))}${row('Age', XL.ageText(p.dob))}
        ${row('Relation', p.relation)}${row('Main Block', p.main_block)}${row('Sub-Block', p.sub_block)}${row('Majhi', p.majhi)}${row('Remarks', p.remarks)}
        ${p.updated_at ? `<p class="muted" style="font-size:12px;margin:10px 0 0">Last updated ${esc(p.updated_at)}${p.updated_by_name ? ' by ' + esc(p.updated_by_name) : ''}</p>` : ''}
      </div></div>
      <div>
        ${p.fcn ? `<button class="btn block" data-family style="margin-bottom:10px">${ic('FamilyRestroom')} View whole family (FCN ${esc(p.fcn)})</button>` : ''}
        ${isAdmin() ? `<div class="btnrow" style="margin-top:0"><button class="btn outline" data-edit>${ic('Edit')} Edit</button><button class="btn outline" data-del style="color:var(--danger);border-color:var(--danger)">${ic('Delete')} Delete</button></div>`
          : `<button class="btn outline block" data-req>${ic('MailOutline')} Request a correction</button>`}
      </div>
    </div></div>`;
  const fam = $('[data-family]', main); if (fam) fam.onclick = () => go('family/' + encodeURIComponent(p.fcn));
  $$('[data-edit]', main).forEach((b) => { b.onclick = () => go('edit/' + p.id); });
  $$('[data-del]', main).forEach((b) => {
    b.onclick = async () => {
      if (!(await confirmBox('Delete record?', `${p.name} (FCN ${p.fcn}) will be permanently deleted. This cannot be undone.`, 'Delete', true))) return;
      try { await api('delete', { id: p.id }); toast('Record deleted.'); changed(); back('records'); } catch (e) { toast(e.message); }
    };
  });
  const rq = $('[data-req]', main); if (rq) rq.onclick = () => composeMessage(`Correction: ${p.name} (FCN ${p.fcn})`, p.id);
}

// ---------------- FAMILY ----------------
async function pageFamily(main, fcn) {
  main.innerHTML = topbar('Family · FCN ' + fcn, '', { back: true }) + loading();
  let rows;
  try { rows = (await api('family', { fcn })).rows || []; } catch (e) {
    main.innerHTML = topbar('Family · FCN ' + fcn, '', { back: true }) + errorBox(e.message);
    $('[data-retry]', main).onclick = () => render();
    return;
  }
  if (route().name !== 'family') return;
  const head = rows[0] || {};
  const m = rows.filter((r) => r.sex === 'M').length, fe = rows.filter((r) => r.sex === 'F').length;
  const kids = rows.filter((r) => { const a = ageYears(r.dob); return a != null && a < 18; }).length;
  main.innerHTML = topbar('Family · FCN ' + fcn, rows.length + ' members', { back: true }) + `
    <div class="page pad">
      ${rows.length ? `<div class="wcard t-orange" style="margin-bottom:12px">${wave()}<div class="in" style="padding:16px">
        <div style="display:flex;align-items:center;gap:12px"><div class="tile">${ic('FamilyRestroom')}</div><div style="font-size:22px;font-weight:800;color:var(--d)">FCN ${esc(fcn)}</div></div>
        <p class="muted" style="margin:8px 0 10px">${esc([[head.main_block, head.sub_block].filter(Boolean).join(' / '), head.majhi ? 'Majhi ' + head.majhi : ''].filter(Boolean).join(' · '))}</p>
        <div style="display:flex;gap:6px;flex-wrap:wrap"><span class="tag">${rows.length} members</span><span class="tag" style="--c:var(--male)">${m} male</span><span class="tag" style="--c:var(--female)">${fe} female</span>${kids ? `<span class="tag" style="--c:#C27410">${kids} under 18</span>` : ''}</div>
      </div></div>
      <div class="plist">${rows.map((p, i) => personCard(p, i + 1)).join('')}</div>` : emptyBox('No members', 'No records found with FCN ' + fcn + '.')}
    </div>
    ${isAdmin() && rows.length ? `<button class="fab" data-add>${ic('Add')} Add member</button>` : ''}`;
  $$('[data-id]', main).forEach((el) => { el.onclick = () => go('record/' + el.dataset.id); });
  const add = $('[data-add]', main);
  if (add) add.onclick = () => { S.preset = { fcn, main_block: head.main_block, sub_block: head.sub_block, majhi: head.majhi }; go('new'); };
}

// ---------------- ADD / EDIT ----------------
async function pageEdit(main, id) {
  const isNew = route().name === 'new';
  if (!isAdmin()) { main.innerHTML = topbar('Not allowed', '', { back: true }) + emptyBox('Only an Admin can do this.', ''); return; }
  let p = Object.assign({ id: 0, name: '', sex: '', fcn: '', dob: '', relation: '', main_block: '', sub_block: '', majhi: '', remarks: '' }, isNew ? (S.preset || {}) : {});
  if (!isNew) {
    main.innerHTML = topbar('Edit record', '', { back: true }) + loading();
    try { p = (await api('get', { id: +id })).row; } catch (e) { main.innerHTML = topbar('Edit record', '', { back: true }) + errorBox(e.message); return; }
    if (route().name !== 'edit') return;
  }
  const dl = (id2, list) => `<datalist id="${id2}">${list.map((x) => `<option value="${esc(x)}">`).join('')}</datalist>`;
  const today = new Date().toISOString().slice(0, 10);
  main.innerHTML = topbar(isNew ? 'Add record' : 'Edit record', isNew ? null : p.name, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto"><form class="card" id="editForm">
      <label class="field"><span>Name *</span><input class="input" name="name" value="${esc(p.name)}" required></label>
      <div class="field"><span>Sex</span><div class="seg">
        <label><input type="radio" name="sex" value="M" ${p.sex === 'M' ? 'checked' : ''}><span>Male</span></label>
        <label><input type="radio" name="sex" value="F" ${p.sex === 'F' ? 'checked' : ''}><span>Female</span></label></div></div>
      <div class="row2">
        <label class="field"><span>FCN</span><input class="input" name="fcn" value="${esc(p.fcn)}" inputmode="numeric"></label>
        <label class="field"><span>Date of birth</span><input class="input" name="dob" type="date" max="${today}" value="${esc(p.dob)}"></label>
      </div>
      <label class="field"><span>Relation</span><input class="input" name="relation" list="dlRel" value="${esc(p.relation)}" style="text-transform:uppercase"></label>
      <div class="row2">
        <label class="field"><span>Main Block</span><input class="input" name="main_block" list="dlMain" value="${esc(p.main_block)}"></label>
        <label class="field"><span>Sub-Block</span><input class="input" name="sub_block" list="dlSub" value="${esc(p.sub_block)}"></label>
      </div>
      <label class="field"><span>Majhi</span><input class="input" name="majhi" list="dlMajhi" value="${esc(p.majhi)}"></label>
      <label class="field"><span>Remarks</span><textarea class="input" name="remarks">${esc(p.remarks)}</textarea></label>
      ${dl('dlRel', S.meta.relations)}${dl('dlMain', mainNames())}${dl('dlSub', subNames(p.main_block))}${dl('dlMajhi', S.meta.majhis)}
      <div class="err" id="editErr"></div>
      <button class="btn block" type="submit">${isNew ? 'Add record' : 'Save changes'}</button>
    </form></div>`;
  const f = $('#editForm', main);
  f.main_block.oninput = () => { $('#dlSub', main).innerHTML = subNames(f.main_block.value.trim()).map((x) => `<option value="${esc(x)}">`).join(''); };
  // tap a selected radio again to clear it
  $$('input[name=sex]', f).forEach((r) => { r.onclick = () => { if (r.dataset.was === '1') { r.checked = false; r.dataset.was = ''; } else { $$('input[name=sex]', f).forEach((x) => { x.dataset.was = ''; }); r.dataset.was = '1'; } }; if (r.checked) r.dataset.was = '1'; });
  f.onsubmit = async (e) => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(f));
    if (!v.name.trim()) { $('#editErr').textContent = 'Name is required.'; return; }
    const btn = $('button[type=submit]', f);
    busyBtn(btn, true);
    try {
      await api('save', { id: p.id || 0, name: v.name.trim(), sex: v.sex || '', fcn: v.fcn.trim(), dob: v.dob || '', relation: v.relation.trim().toUpperCase(), main_block: v.main_block.trim(), sub_block: v.sub_block.trim(), majhi: v.majhi.trim(), remarks: v.remarks.trim() });
      toast(isNew ? 'Record added.' : 'Changes saved.');
      S.preset = null;
      changed();
      back('records');
    } catch (ex) { $('#editErr').textContent = ex.message; busyBtn(btn, false); }
  };
}

// ---------------- MESSAGES ----------------
let msgOpenOnly = true;
async function pageMessages(main) {
  const admin = isAdmin();
  main.innerHTML = topbar('Messages', admin ? 'Requests from users' : 'Your requests to the Admin', { actions: `<button class="iconbtn" data-reload title="Refresh">${ic('Refresh')}</button>` }) + `
    <div class="page pad">
      ${admin ? `<div class="chips"><button class="chip ${msgOpenOnly ? 'on' : ''}" data-open="1">Open</button><button class="chip ${!msgOpenOnly ? 'on' : ''}" data-open="0">All</button></div>` : ''}
      <div id="msgList">${loading()}</div>
    </div>
    ${!admin ? `<button class="fab" data-new>${ic('Add')} New request</button>` : ''}`;
  $('[data-reload]', main).onclick = () => render();
  $$('[data-open]', main).forEach((c) => { c.onclick = () => { msgOpenOnly = c.dataset.open === '1'; render(); }; });
  const nb = $('[data-new]', main); if (nb) nb.onclick = () => composeMessage('', 0);
  let list;
  try {
    list = (admin ? await api('msg_all', { status: msgOpenOnly ? 'open' : 'all' }) : await api('msg_mine')).messages || [];
    loadStats();
  } catch (e) { $('#msgList').innerHTML = errorBox(e.message); $('[data-retry]').onclick = () => render(); return; }
  if (route().name !== 'messages') return;
  const box = $('#msgList');
  if (!list.length) {
    box.innerHTML = emptyBox('No messages', admin ? (msgOpenOnly ? 'There are no open requests.' : 'No requests yet.') : 'Tap "New request" to ask the Admin to add, correct or remove a record.');
    return;
  }
  const tag = (s) => s === 'open' ? '<span class="tag" style="--c:#C27410">Open</span>' : s === 'replied' ? '<span class="tag">Replied</span>' : '<span class="tag" style="--c:#777">Closed</span>';
  box.innerHTML = `<div class="stack cols">${list.map((m) => `
    <div class="card">
      <div style="display:flex;gap:8px;align-items:center"><b style="flex:1">${esc(m.subject || 'Request')}</b>${tag(m.status)}</div>
      <div class="muted" style="font-size:12px;margin:2px 0 8px">${admin ? esc(m.user_name) + ' · ' : ''}${esc(m.created_at)}</div>
      <div style="white-space:pre-line">${esc(m.body)}</div>
      ${m.beneficiary ? `<div style="margin-top:6px;color:var(--brand);font-weight:600;font-size:14px">Record: ${esc(m.beneficiary)}</div>` : ''}
      ${m.reply ? `<div class="reply"><small>Reply from ${esc(m.replied_by || 'Admin')} · ${esc(m.replied_at)}</small>${esc(m.reply)}</div>` : ''}
      ${(admin || m.beneficiary_id) ? `<div class="btnrow" style="margin-top:10px">
        ${m.beneficiary_id ? `<button class="btn outline small" data-rec="${m.beneficiary_id}">Open record</button>` : ''}
        ${admin ? `<button class="btn outline small" data-reply="${m.id}">${m.reply ? 'Edit reply' : 'Reply'}</button>${m.status !== 'closed' ? `<button class="btn ghost small" data-close="${m.id}">Close</button>` : ''}` : ''}
      </div>` : ''}
    </div>`).join('')}</div>`;
  $$('[data-rec]', box).forEach((b) => { b.onclick = () => go('record/' + b.dataset.rec); });
  $$('[data-reply]', box).forEach((b) => { b.onclick = () => replyMessage(list.find((m) => m.id === +b.dataset.reply)); });
  $$('[data-close]', box).forEach((b) => { b.onclick = async () => { try { await api('msg_close', { id: +b.dataset.close }); render(); } catch (e) { toast(e.message); } }; });
}
function composeMessage(subject, beneficiaryId) {
  modal(`<h3>Message to Admin</h3>
    <label class="field"><span>Subject</span><input class="input" id="mSub" value="${esc(subject)}"></label>
    <label class="field"><span>Message</span><textarea class="input" id="mBody" placeholder="What should the Admin add, correct or remove?"></textarea></label>
    <div class="err" id="mErr"></div>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-send>${ic('Send')} Send</button></div>`, (m, close) => {
    $('[data-cancel]', m).onclick = close;
    $('[data-send]', m).onclick = async (e) => {
      const body = $('#mBody', m).value.trim();
      if (!body) { $('#mErr', m).textContent = 'Please write your message.'; return; }
      busyBtn(e.currentTarget, true);
      try {
        const r = await api('msg_send', { subject: $('#mSub', m).value.trim(), body, beneficiary_id: beneficiaryId || 0 });
        close(); toast(r.message || 'Sent.');
        if (route().name === 'messages') render();
      } catch (ex) { $('#mErr', m).textContent = ex.message; busyBtn(e.currentTarget, false); }
    };
    setTimeout(() => $('#mBody', m).focus(), 50);
  });
}
function replyMessage(msg) {
  if (!msg) return;
  modal(`<h3>Reply to ${esc(msg.user_name)}</h3><p class="muted" style="white-space:pre-line">${esc(msg.body)}</p>
    <label class="field"><span>Your reply</span><textarea class="input" id="rBody">${esc(msg.reply)}</textarea></label>
    <div class="err" id="rErr"></div>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-send>${ic('Send')} Send reply</button></div>`, (m, close) => {
    $('[data-cancel]', m).onclick = close;
    $('[data-send]', m).onclick = async (e) => {
      const reply = $('#rBody', m).value.trim();
      if (!reply) { $('#rErr', m).textContent = 'Please write a reply.'; return; }
      busyBtn(e.currentTarget, true);
      try { await api('msg_reply', { id: msg.id, reply }); close(); toast('Reply sent.'); render(); }
      catch (ex) { $('#rErr', m).textContent = ex.message; busyBtn(e.currentTarget, false); }
    };
  });
}

// ---------------- MORE ----------------
function pageMore(main) {
  const u = S.user;
  const item = (key, icon, tone, label, badge) => `<div class="menuitem t-${tone}" data-item="${key}"><div class="tile">${ic(icon)}</div><span class="grow">${label}</span>${badge ? `<span class="tag" style="--c:#D32F2F">${badge} new</span>` : ''}${ic('KeyboardArrowRight')}</div>`;
  main.innerHTML = topbar('More', null) + `
    <div class="page pad narrow" style="margin:0 auto">
      <div class="wcard t-green">${wave()}<div class="in" style="display:flex;align-items:center;gap:14px;padding:16px">
        <div class="tile" style="border-radius:50%;width:56px;height:56px">${esc((u.name || '?').slice(0, 1).toUpperCase())}</div>
        <div style="flex:1;min-width:0"><div style="font-size:18px;font-weight:800">${esc(u.name)}</div><div class="muted" style="font-size:13px">${esc(u.email)}</div>
          <span class="tag" style="margin-top:6px;${isAdmin() ? '--c:#C27410' : ''}">${isAdmin() ? '👑 Admin' : 'Viewer'}</span></div>
      </div></div>
      <div class="card menu" style="margin-top:12px">
        ${isAdmin() ? item('users', 'AccountCircle', 'green', 'Manage users', (S.stats || {}).pending_users) + item('import', 'UploadFile', 'green', 'Import from Excel') : ''}
        ${item('password', 'Lock', 'green', 'Change password')}
        ${item('refresh', 'Refresh', 'green', 'Refresh data')}
        ${item('about', 'Info', 'green', 'About this app')}
      </div>
      <div class="card menu" style="margin-top:12px">${item('signout', 'Logout', 'red', '<span style="color:var(--danger)">Sign out</span>')}</div>
    </div>`;
  $$('[data-item]', main).forEach((el) => {
    el.onclick = async () => {
      const k = el.dataset.item;
      if (k === 'users' || k === 'import' || k === 'password') go(k);
      else if (k === 'refresh') { changed(); toast('Refreshed.'); }
      else if (k === 'about') modal(`<h3>Camp- 8 West</h3><p>FDMN Database · Web version ${APP_VERSION}</p><p class="muted">Beneficiary records for Camp-8W, organised by Main Block and Sub-Block. Data is stored securely on app.informationplant.com and is only visible to approved users. The same data is used by the Android app.</p><div class="actions"><button class="btn" data-ok>OK</button></div>`, (m, close) => { $('[data-ok]', m).onclick = close; });
      else if (k === 'signout') { if (await confirmBox('Sign out?', 'You will need your email and password to sign in again.', 'Sign out')) signOut(); }
    };
  });
}

// ---------------- USERS (admin) ----------------
let userFilter = '';
async function pageUsers(main) {
  if (!isAdmin()) { go('more'); return; }
  main.innerHTML = topbar('Manage users', '', { back: true }) + `<div class="page pad"><div id="uChips" class="chips"></div><div id="uList">${loading()}</div></div>`;
  let list;
  try { list = (await api('users')).users || []; loadStats(); } catch (e) { $('#uList').innerHTML = errorBox(e.message); $('[data-retry]').onclick = () => render(); return; }
  if (route().name !== 'users') return;
  if (!userFilter) userFilter = list.some((u) => u.status === 'pending') ? 'pending' : 'active';
  $('#topSub').textContent = list.length + ' users';
  const draw = () => {
    const cnt = (k) => (k === 'all' ? list.length : list.filter((u) => u.status === k).length);
    $('#uChips').innerHTML = [['pending', 'Waiting'], ['active', 'Active'], ['blocked', 'Blocked'], ['all', 'All']]
      .map(([k, l]) => `<button class="chip ${userFilter === k ? 'on' : ''}" data-uf="${k}">${l} (${cnt(k)})</button>`).join('');
    $$('[data-uf]').forEach((c) => { c.onclick = () => { userFilter = c.dataset.uf; draw(); }; });
    const shown = userFilter === 'all' ? list : list.filter((u) => u.status === userFilter);
    const box = $('#uList');
    if (!shown.length) { box.innerHTML = emptyBox('Nobody here', userFilter === 'pending' ? 'No new sign-ups are waiting for approval.' : 'No users in this list.'); return; }
    const me = S.user.id;
    const stTag = (s) => s === 'pending' ? '<span class="tag" style="--c:#D32F2F">Waiting</span>' : s === 'blocked' ? '<span class="tag" style="--c:#777">Blocked</span>' : '<span class="tag" style="--c:#2E7D32">Active</span>';
    box.innerHTML = `<div class="stack cols">${shown.map((u) => `
      <div class="card"><div style="display:flex;gap:10px;align-items:flex-start">
        <div style="flex:1;min-width:0"><b>${esc(u.name)}${u.id === me ? ' (You)' : ''}</b><div class="muted" style="font-size:13px;word-break:break-all">${esc(u.email)}</div>
          <div style="display:flex;gap:6px;margin-top:6px">${u.role === 'admin' ? '<span class="tag" style="--c:#C27410">Admin</span>' : '<span class="tag">Viewer</span>'}${stTag(u.status)}</div>
          <div class="muted" style="font-size:11.5px;margin-top:5px">Joined ${esc((u.created_at || '').slice(0, 10))}${u.last_login ? ' · Last sign-in ' + esc(u.last_login.slice(0, 16)) : ''}</div></div>
        ${u.id !== me ? `<div class="dropdown"><button class="iconbtn" style="color:var(--muted)" data-menu="${u.id}">${ic('MoreVert')}</button></div>` : ''}
      </div>
      ${u.status === 'pending' ? `<div class="btnrow" style="margin-top:10px"><button class="btn small" data-act="approve" data-u="${u.id}">Approve</button><button class="btn ghost small" style="color:var(--danger)" data-act="delete" data-u="${u.id}">Reject</button></div>` : ''}
      </div>`).join('')}</div>`;
    $$('[data-act]', box).forEach((b) => { b.onclick = () => userAction(b.dataset.act, list.find((u) => u.id === +b.dataset.u)); });
    $$('[data-menu]', box).forEach((b) => {
      b.onclick = (e) => {
        e.stopPropagation();
        $$('.dropdown .list').forEach((l) => l.remove());
        const u = list.find((x) => x.id === +b.dataset.menu);
        const opts = [];
        if (u.status === 'blocked') opts.push(['unblock', 'Unblock']); else if (u.status === 'active') opts.push(['block', 'Block']);
        opts.push(u.role === 'admin' ? ['viewer', 'Make Viewer'] : ['admin', 'Make Admin']);
        opts.push(['reset', 'Reset password'], ['delete', 'Delete user']);
        const l = document.createElement('div'); l.className = 'list';
        l.innerHTML = opts.map(([k, t]) => `<button class="${k === 'delete' ? 'danger' : ''}" data-o="${k}">${t}</button>`).join('');
        b.parentElement.appendChild(l);
        $$('[data-o]', l).forEach((o) => { o.onclick = () => { l.remove(); userAction(o.dataset.o, u); }; });
        setTimeout(() => document.addEventListener('click', () => l.remove(), { once: true }), 0);
      };
    });
  };
  draw();
}
async function userAction(act, u) {
  if (!u) return;
  const set = async (body, msg) => { try { await api('user_set', Object.assign({ id: u.id }, body)); toast(msg); render(); } catch (e) { toast(e.message); } };
  if (act === 'approve') set({ status: 'active' }, `${u.name} approved.`);
  else if (act === 'unblock') set({ status: 'active' }, `${u.name} unblocked.`);
  else if (act === 'block') { if (await confirmBox('Please confirm', `Block ${u.name}? They will not be able to sign in.`, 'Block')) set({ status: 'blocked' }, `${u.name} blocked.`); }
  else if (act === 'admin') { if (await confirmBox('Please confirm', `Make ${u.name} an Admin? Admins can add, edit and delete all records and manage users.`, 'Make Admin')) set({ role: 'admin' }, `${u.name} is now an Admin.`); }
  else if (act === 'viewer') { if (await confirmBox('Please confirm', `Change ${u.name} to Viewer? They will only be able to view records.`, 'Make Viewer')) set({ role: 'viewer' }, `${u.name} is now a Viewer.`); }
  else if (act === 'delete') {
    if (await confirmBox('Please confirm', `Delete ${u.name}'s account permanently?`, 'Delete', true)) {
      try { await api('user_delete', { id: u.id }); toast('User deleted.'); render(); } catch (e) { toast(e.message); }
    }
  } else if (act === 'reset') {
    modal(`<h3>Reset password</h3><p>Set a new password for ${esc(u.name)}. Tell them the new password so they can sign in.</p>
      <label class="field"><span>New password (min 6)</span><input class="input" id="npw" type="text" autocomplete="off"></label><div class="err" id="npwErr"></div>
      <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-save>Save</button></div>`, (m, close) => {
      $('[data-cancel]', m).onclick = close;
      $('[data-save]', m).onclick = () => {
        const pw = $('#npw', m).value;
        if (pw.length < 6) { $('#npwErr', m).textContent = 'Password must be at least 6 characters.'; return; }
        close(); set({ password: pw }, `Password changed for ${u.name}.`);
      };
    });
  }
}

// ---------------- IMPORT (admin) ----------------
function pageImport(main) {
  if (!isAdmin()) { go('more'); return; }
  const st = { parsed: null, sent: 0, busy: false, done: false, replace: (S.stats || {}).total > 0 };
  main.innerHTML = topbar('Import from Excel', null, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto">
      <div class="card"><b>How it works</b><p style="margin:6px 0 0;font-size:14px;line-height:1.6">
        1. Choose your Excel file (.xlsx) or a .csv file.<br>
        2. The app finds the heading row (SL, Name, Sex, FCN, Date of birth, Relation, Main Block, Sub-Block, Majhi, Remarks). All sheets with this heading row are read.<br>
        3. Check the summary, then tap Upload.<br><br>
        Main Block and Sub-Block lists are created automatically. SL numbers are given automatically.</p></div>
      <label class="btn outline block" style="margin-top:12px">${ic('UploadFile')} <span id="pickLabel">Choose Excel file</span>
        <input type="file" id="file" accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv" hidden></label>
      <div id="imp"></div>
    </div>`;
  const box = $('#imp', main);
  const draw = () => {
    const p = st.parsed;
    if (!p) return;
    const fam = new Set(p.rows.map((r) => r.fcn).filter(Boolean)).size;
    const kv = (k, v, warn) => `<span>${k}</span><b class="${warn ? 'warn' : ''}">${esc(v)}</b>`;
    box.innerHTML = `
      <div class="card" style="margin-top:12px"><b>File summary</b><div class="kv" style="margin-top:8px">
        ${kv('Rows found', fmt(p.rows.length))}${kv('Families (FCN)', fmt(fam))}${kv('Sheets read', p.sheetsUsed.join(', '))}
        ${p.sheetsSkipped.length ? kv('Sheets skipped', p.sheetsSkipped.join(', ')) : ''}${kv('Columns matched', p.columnsFound.join(', '))}
        ${p.columnsMissing.length ? kv('Columns not found', p.columnsMissing.join(', '), true) : ''}
        ${p.emptyRowsSkipped ? kv('Empty rows skipped', fmt(p.emptyRowsSkipped)) : ''}
        ${p.badDates ? kv('Unclear dates', `${fmt(p.badDates)} (left empty; the original text was put in Remarks)`, true) : ''}
      </div></div>
      ${p.rows.length ? `
      <div class="card" style="margin-top:12px"><b>First rows (preview)</b>
        ${p.rows.slice(0, 6).map((r, i) => `<div style="font-size:13px;padding:6px 0;border-bottom:1px solid #EEF3F1">${i + 1}. ${esc(r.name)} · ${esc(r.sex)} · FCN ${esc(r.fcn)} · ${esc(XL.displayDate(r.dob) || 'no DOB')} · ${esc(r.relation)} · ${esc(r.main_block)}/${esc(r.sub_block)} · ${esc(r.majhi)}</div>`).join('')}
      </div>
      <label class="check" style="margin:14px 2px"><input type="checkbox" id="rep" ${st.replace ? 'checked' : ''} ${st.sent || st.busy ? 'disabled' : ''}>
        <span><b>Delete all existing records first</b><br><small class="muted">${(S.stats || {}).total > 0 ? `There are ${fmt(S.stats.total)} records now. Tick this to replace them, or untick to add these rows to them.` : 'The database is empty.'}</small></span></label>
      ${st.busy || st.sent ? `<div>Uploaded ${fmt(st.sent)} of ${fmt(p.rows.length)}</div><div class="progress"><div style="width:${Math.round(st.sent / p.rows.length * 100)}%"></div></div>` : ''}
      ${st.done ? `<div class="note" style="background:#CDEBE1;padding:12px;border-radius:12px;font-weight:600;margin:10px 0">Import finished: ${fmt(st.sent)} records uploaded.</div><button class="btn block" data-view>View records</button>`
        : `<button class="btn block" data-up ${st.busy ? 'disabled' : ''}>${st.busy ? 'Uploading…' : st.sent ? 'Continue upload' : `Upload ${fmt(p.rows.length)} records`}</button>`}` : ''}
      <div class="err" id="impErr">${esc(st.error || '')}</div>`;
    const rep = $('#rep', box); if (rep) rep.onchange = () => { st.replace = rep.checked; };
    const vw = $('[data-view]', box); if (vw) vw.onclick = () => openRecords({});
    const up = $('[data-up]', box);
    if (up) up.onclick = async () => {
      if (st.replace && !st.sent && (S.stats || {}).total > 0 && !(await confirmBox('Replace all records?', `All ${fmt(S.stats.total)} existing records will be deleted and replaced with the ${fmt(p.rows.length)} rows from this file. This cannot be undone.`, 'Replace', true))) return;
      st.busy = true; st.error = ''; draw();
      try {
        while (st.sent < p.rows.length) {
          const part = p.rows.slice(st.sent, st.sent + 1000);
          await api('import', { rows: part, replace: st.replace && st.sent === 0 });
          st.sent += part.length;
          draw();
        }
        st.done = true;
        changed();
      } catch (e) {
        st.error = `${e.message}\n${fmt(st.sent)} of ${fmt(p.rows.length)} rows were uploaded. Tap Continue to upload the rest.`;
      } finally { st.busy = false; draw(); }
    };
  };
  $('#file', main).onchange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    Object.assign(st, { parsed: null, sent: 0, done: false, error: '' });
    box.innerHTML = `<div class="card" style="margin-top:12px">Reading ${esc(file.name)}…<div class="progress indet"><div></div></div></div>`;
    try {
      st.parsed = await XL.readFile(await file.arrayBuffer());
      $('#pickLabel').textContent = 'Choose a different file';
      draw();
    } catch (ex) { box.innerHTML = `<p class="err">${esc(ex.message || 'Could not read this file.')}</p>`; }
    e.target.value = '';
  };
}

// ---------------- CHANGE PASSWORD ----------------
function pagePassword(main) {
  main.innerHTML = topbar('Change password', null, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto"><form class="card" id="pwForm">
      <label class="field"><span>Current password</span><input class="input" type="password" name="old" autocomplete="current-password" required></label>
      <label class="field"><span>New password (min 6)</span><input class="input" type="password" name="n1" autocomplete="new-password" required></label>
      <label class="field"><span>Confirm new password</span><input class="input" type="password" name="n2" autocomplete="new-password" required></label>
      <div class="err" id="pwErr"></div>
      <button class="btn block" type="submit">Change password</button>
    </form></div>`;
  const f = $('#pwForm', main);
  f.onsubmit = async (e) => {
    e.preventDefault();
    const err = $('#pwErr');
    if (f.n1.value.length < 6) { err.textContent = 'New password must be at least 6 characters.'; return; }
    if (f.n1.value !== f.n2.value) { err.textContent = 'The two new passwords do not match.'; return; }
    const btn = $('button[type=submit]', f);
    busyBtn(btn, true);
    try { await api('change_password', { old: f.old.value, new: f.n1.value }); toast('Password changed.'); back('more'); }
    catch (ex) { err.textContent = ex.message; busyBtn(btn, false); }
  };
}

// ---------------- start ----------------
(function start() {
  const splash = document.createElement('div');
  splash.className = 'splash';
  splash.innerHTML = '<img src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West"><b>Camp- 8 West</b><span class="spinner"></span>';
  document.body.appendChild(splash);
  const ready = S.token && S.user ? Promise.all([loadMeta(), loadStats()]) : Promise.resolve();
  Promise.all([ready, new Promise((r) => setTimeout(r, 1200))]).then(() => {
    splash.remove();
    render();
    if (S.token && S.user) {
      showWelcome();
      api('me').then((r) => { S.user = r.user; store.set('fdmn_user', JSON.stringify(r.user)); updateNav(); }).catch(() => {});
    }
  });
})();
