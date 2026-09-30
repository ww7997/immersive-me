/* ============================================================
 *  Immersive-Me — محرّك الترجمة الثنائية (يُحقن في كل صفحة)
 *  المحرّك الفعلي في Kotlin. هنا: جمع النصوص، العرض، والمراقبة.
 * ============================================================ */
(function () {
  'use strict';
  if (window.__IMT_LOADED__) { window.__imtConfigChanged && window.__imtConfigChanged(); return; }
  window.__IMT_LOADED__ = true;

  /* -------------------- الإعدادات من التطبيق -------------------- */
  var CFG = { target: 'ar', mode: 'below', enabled: false, provider: '' };

  function readConfig() {
    try {
      var raw = (typeof ImtNative !== 'undefined') ? ImtNative.getConfig() : null;
      if (raw) CFG = JSON.parse(raw);
    } catch (e) {}
    document.documentElement.classList.remove('imt-mode-below', 'imt-mode-blur', 'imt-mode-replace');
    document.documentElement.classList.add('imt-mode-' + (CFG.mode || 'below'));
  }

  /* -------------------- الأنماط -------------------- */
  var CSS = [
    '.imt-trans{display:block;margin:.2em 0 .45em;font:inherit;line-height:1.55;color:inherit;',
    '  opacity:.95;border-inline-start:3px solid rgba(110,150,255,.6);padding-inline-start:.55em;}',
    'span.imt-trans{display:inline;border:0;padding:0;margin:0;}',
    'html.imt-mode-blur .imt-trans{filter:blur(4.5px);transition:filter .15s ease;cursor:pointer;}',
    'html.imt-mode-blur .imt-trans:hover{filter:none;}',
    'html.imt-mode-replace .imt-orig{display:none;}',
    '.imt-loading{opacity:.55;}',
    '.imt-badge{position:fixed;z-index:2147483647;left:10px;bottom:10px;background:rgba(27,29,34,.92);',
    '  color:#cfd6e4;font:11px/1.4 system-ui,sans-serif;padding:5px 9px;border-radius:8px;',
    '  pointer-events:none;opacity:0;transition:opacity .25s;}',
    '.imt-badge.on{opacity:1;}'
  ].join('\n');

  function injectCSS() {
    if (document.getElementById('imt-style')) return;
    var s = document.createElement('style');
    s.id = 'imt-style';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  /* -------------------- شارة الحالة -------------------- */
  var badge = null, badgeTimer = null;
  function showBadge(text) {
    if (!badge) {
      badge = document.createElement('div');
      badge.className = 'imt-badge';
      document.documentElement.appendChild(badge);
    }
    badge.textContent = text;
    badge.classList.add('on');
    clearTimeout(badgeTimer);
    badgeTimer = setTimeout(function () { badge.classList.remove('on'); }, 2600);
  }

  /* -------------------- الجسر -------------------- */
  var seq = 0;
  var pending = {};
  var available = (typeof ImtNative !== 'undefined') && ImtNative && ImtNative.translate;

  window.__imtCallback = function (id, json) {
    var cb = pending[id];
    if (!cb) return;
    delete pending[id];
    var arr = [];
    try { arr = JSON.parse(json) || []; } catch (e) {}
    cb(arr);
  };

  function bridgeTranslate(texts) {
    return new Promise(function (resolve) {
      if (!available) { resolve([]); return; }
      var id = 'r' + (++seq);
      pending[id] = resolve;
      try {
        ImtNative.translate(id, JSON.stringify(texts));
      } catch (e) { delete pending[id]; resolve([]); }
      setTimeout(function () {
        if (pending[id]) { delete pending[id]; resolve([]); }
      }, 180000);
    });
  }

  /* -------------------- الإحصائيات (تظهر بشريط الحالة) -------------------- */
  var stats = { done: 0, total: 0 };
  var repTimer = null;

  function report() {
    if (repTimer) return;
    repTimer = setTimeout(function () {
      repTimer = null;
      try { if (ImtNative && ImtNative.status) ImtNative.status(stats.done, stats.total); } catch (e) {}
    }, 250);
  }

  /* -------------------- جمع العناصر -------------------- */
  var SKIP_TAGS = {
    SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, CODE: 1, PRE: 1, KBD: 1, SAMP: 1, VAR: 1,
    TEXTAREA: 1, INPUT: 1, SELECT: 1, OPTION: 1, SVG: 1, CANVAS: 1, MATH: 1,
    IFRAME: 1, VIDEO: 1, AUDIO: 1, IMG: 1, BUTTON: 1, CITE: 1
  };
  var SKIP_SEL = '.notranslate,[translate="no"],.imt-trans,.imt-orig,[contenteditable="true"],[data-imt-skip]';
  var CAND_SEL = 'p,li,h1,h2,h3,h4,h5,h6,td,th,dd,dt,blockquote,figcaption,summary,caption,' +
                 'div,span,article,section,main,aside';
  var BLOCKY = /^(block|list-item|table-cell|table-caption|flex|grid)$/;
  var visCache = new WeakMap();

  function isBlocky(el) {
    var v = visCache.get(el);
    if (v === undefined) {
      try { v = BLOCKY.test(getComputedStyle(el).display); } catch (e) { v = false; }
      visCache.set(el, v);
    }
    return v;
  }

  var MIN_LEN = 8;

  function eligible(el) {
    if (SKIP_TAGS[el.tagName]) return false;
    if (el.isContentEditable) return false;
    if (el.closest(SKIP_SEL)) return false;
    if (el === document.body || el === document.documentElement) return false;
    if (!isBlocky(el)) return false;
    if (el.querySelector(CAND_SEL)) return false;
    var t = el.innerText || '';
    var min = (el.tagName === 'DIV' || el.tagName === 'SPAN') ? 14 : MIN_LEN;
    if (t.trim().length < min) return false;
    if (!/[A-Za-z\u00C0-\u024F\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]/.test(t)) return false;
    return true;
  }

  function getText(el) {
    var t = el.innerText || el.textContent || '';
    return t.replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
  }

  function collect() {
    var out = [];
    var list;
    try { list = document.body.querySelectorAll(CAND_SEL); } catch (e) { return out; }
    for (var i = 0; i < list.length && out.length < 3000; i++) {
      var el = list[i];
      if (el.__imtDone) continue;
      if (eligible(el)) out.push(el);
    }
    return out;
  }

  /* -------------------- العرض -------------------- */
  function render(el, text) {
    if (!text || !el.isConnected) return;
    el.__imtDone = true;
    el.classList.remove('imt-loading');
    stats.done++;
    report();

    for (var i = 0; i < el.children.length; i++) {
      if (el.children[i].classList && el.children[i].classList.contains('imt-trans')) {
        el.children[i].textContent = text;
        return;
      }
    }

    var node = document.createElement('span');
    node.className = 'imt-trans';
    node.setAttribute('data-imt-skip', '1');
    node.textContent = text;

    var tag = el.tagName;
    var inside = (tag === 'TD' || tag === 'TH' || tag === 'CAPTION' ||
                  tag === 'SUMMARY' || tag === 'LI' || !isBlocky(el));

    if (CFG.mode === 'replace') {
      var orig = document.createElement('span');
      orig.className = 'imt-orig';
      orig.setAttribute('data-imt-skip', '1');
      while (el.firstChild) orig.appendChild(el.firstChild);
      el.appendChild(orig);
      el.appendChild(node);
      return;
    }

    if (inside) el.appendChild(node);
    else {
      try { el.insertAdjacentElement('afterend', node); }
      catch (e) { el.appendChild(node); }
    }
  }

  function resetAll() {
    var t = document.querySelectorAll('.imt-trans');
    for (var i = 0; i < t.length; i++) t[i].remove();
    var o = document.querySelectorAll('.imt-orig');
    for (var j = 0; j < o.length; j++) {
      var n = o[j], p = n.parentNode;
      while (n.firstChild) p.insertBefore(n.firstChild, n);
      n.remove();
    }
    var l = document.querySelectorAll('.imt-loading');
    for (var k = 0; k < l.length; k++) l[k].classList.remove('imt-loading');
    var d = document.querySelectorAll('.__imtDone');
    for (var m = 0; m < d.length; m++) d[m].__imtDone = false;
    stats.done = 0;
    stats.total = 0;
    report();
  }

  window.__imtSetMode = function (m) {
    CFG.mode = m;
    try { if (ImtNative && ImtNative.saveMode) ImtNative.saveMode(m); } catch (e) {}
    document.documentElement.classList.remove('imt-mode-below', 'imt-mode-blur', 'imt-mode-replace');
    document.documentElement.classList.add('imt-mode-' + m);
    showBadge('وضع العرض: ' + ({ below: 'ثنائي', blur: 'ضبابي', replace: 'استبدال' }[m] || m));
  };

  /* -------------------- الطابور -------------------- */
  var queue = [];
  var inflight = 0;
  var running = false;
  var CONC = 3;

  function enqueue(el) {
    if (!running || el.__imtDone || el.__imtQueued) return;
    el.__imtQueued = true;
    el.classList.add('imt-loading');
    queue.push(el);
    pump();
  }

  function pump() {
    if (!running || inflight >= CONC || !queue.length) return;

    var batch = [];
    while (batch.length < 8 && queue.length) {
      var el = queue.shift();
      el.__imtQueued = false;
      if (!el.isConnected || el.__imtDone) continue;
      var t = getText(el);
      if (!t) { el.classList.remove('imt-loading'); continue; }
      batch.push([el, t]);
    }
    if (!batch.length) { if (queue.length) setTimeout(pump, 30); return; }

    inflight++;
    var texts = batch.map(function (b) { return b[1]; });

    bridgeTranslate(texts).then(function (list) {
      if (!running) return;
      batch.forEach(function (b, i) {
        var r = ((list && list[i]) || '').trim();
        if (r) render(b[0], r);
        else b[0].classList.remove('imt-loading');
      });
    })['catch'](function () {
      batch.forEach(function (b) { b[0].classList.remove('imt-loading'); });
    }).then(function () {
      inflight--;
      setTimeout(pump, 10);
    });

    setTimeout(pump, 0);
  }

  /* -------------------- المراقبة -------------------- */
  var obs = null, obsTimer = null;

  function startObserver() {
    if (obs || !document.body) return;
    obs = new MutationObserver(function (muts) {
      if (!running) return;
      var hits = 0;
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var n = added[j];
          if (n.nodeType !== 1) continue;
          if (n.classList && n.classList.contains('imt-trans')) continue;
          hits++;
        }
      }
      if (!hits) return;
      clearTimeout(obsTimer);
      obsTimer = setTimeout(function () {
        if (!running) return;
        collect().forEach(enqueue);
      }, 450);
    });
    obs.observe(document.body, { childList: true, subtree: true });
  }

  /* -------------------- الواجهة البرمجية -------------------- */
  window.__imtStart = function () {
    readConfig();
    if (running) return;
    running = true;
    injectCSS();
    startObserver();
    var found = collect();
    stats.total = found.length;
    stats.done = 0;
    report();
    found.forEach(enqueue);
    showBadge('Immersive-Me · ' + CFG.target + ' · ' + (CFG.provider || '') + ' · ' + found.length + ' مقطع');
  };

  window.__imtStop = function () {
    running = false;
    queue = [];
    resetAll();
    showBadge('أُوقفت الترجمة');
  };

  window.__imtToggle = function () {
    running ? window.__imtStop() : window.__imtStart();
  };

  window.__imtConfigChanged = function () {
    var was = running;
    if (was) window.__imtStop();
    readConfig();
    if (was || CFG.enabled) setTimeout(window.__imtStart, 30);
  };

  window.__imtBoot = function () {
    readConfig();
    if (CFG.enabled) window.__imtStart();
  };

  // إقلاع مبكر
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { window.__imtBoot(); });
  } else {
    window.__imtBoot();
  }
})();
