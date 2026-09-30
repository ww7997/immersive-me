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
    'span.imt-trans.imt-block{display:block;border-inline-start:3px solid rgba(110,150,255,.6);',
    '  padding-inline-start:.5em;margin:.2em 0 .3em;}',
    'html.imt-mode-blur .imt-trans{filter:blur(4.5px);transition:filter .15s ease;cursor:pointer;}',
    'html.imt-mode-blur .imt-trans:hover{filter:none;}',
    'html.imt-mode-replace .imt-orig{display:none;}',
    '.imt-loading{opacity:.55;}',
    '.imt-badge{position:fixed;z-index:2147483647;left:10px;bottom:10px;background:rgba(27,29,34,.92);',
    '  color:#cfd6e4;font:11px/1.4 system-ui,sans-serif;padding:5px 9px;border-radius:8px;',
    '  pointer-events:none;opacity:0;transition:opacity .25s;}',
    '.imt-badge.on{opacity:1;}',
    /* ---- ترجمة التحديد ---- */
    '.imt-sel{position:absolute;z-index:2147483646;max-width:min(340px,88vw);background:#272D38;',
    '  border:1px solid #3A4252;border-radius:14px;padding:10px 12px;',
    '  box-shadow:0 10px 30px rgba(0,0,0,.62);color:#E7EAF2;',
    '  font:13.5px/1.65 system-ui,sans-serif;direction:rtl;text-align:right;}',
    '.imt-sel .s{font-size:11px;color:#8C94A6;direction:ltr;text-align:left;margin-bottom:7px;',
    '  max-height:3.4em;overflow:hidden;}',
    '.imt-sel .b{white-space:pre-wrap;}',
    '.imt-sel .a{margin-top:9px;display:flex;gap:14px;font-size:11.5px;color:#8B94A7;}',
    '.imt-sel .a span{cursor:pointer;}',
    /* ---- ترجمة صناديق الإدخال ---- */
    '.imt-inp{display:block;margin:5px 0 10px;font:inherit;}',
    '.imt-inp .t{font-size:.94em;line-height:1.6;direction:rtl;text-align:right;color:inherit;',
    '  opacity:.95;border-inline-start:3px solid rgba(110,150,255,.6);padding-inline-start:9px;}'
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
      }, 240000);
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

  /** هل العنصر قريب من الشاشة؟ — نترجم اللي قدامك أول، والباقي مع التمرير */
  function nearViewport(el) {
    if (!CFG.lazy) return true;
    try {
      var r = el.getBoundingClientRect();
      var vh = window.innerHeight || 800;
      return r.bottom > -1400 && r.top < vh + 1400;
    } catch (e) { return true; }
  }

  function collect() {
    var out = [];
    var seen = new Set();
    var list;
    try { list = document.body.querySelectorAll(CAND_SEL); } catch (e) { return out; }
    for (var i = 0; i < list.length && out.length < 2500; i++) {
      var el = list[i];
      if (el.__imtDone || seen.has(el)) continue;
      if (eligible(el)) { seen.add(el); out.push(el); }
    }
    // طبقة ثانية: عُدّ عُقد النص مباشرة — تلتقط المواقع اللي بتستعمل <span> (يوتيوب، ريديت…)
    var tn = collectTextNodes();
    for (var j = 0; j < tn.length && out.length < 4000; j++) {
      if (!seen.has(tn[j])) { seen.add(tn[j]); out.push(tn[j]); }
    }
    // نرجّع بس اللي قريب من الشاشة — الباقي بينضاف مع التمرير
    if (CFG.lazy) {
      var vis = [];
      for (var k = 0; k < out.length; k++) if (nearViewport(out[k])) vis.push(out[k]);
      return vis;
    }
    return out;
  }

  /** كل عنصر بيحتوي نصاً مباشراً — بلا شرط block و بلا شرط "ما تحته عناصر" */
  function collectTextNodes() {
    var out = [];
    if (!document.body) return out;
    var seen = new Set();
    var walker;
    try {
      walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
        acceptNode: function (n) {
          var v = n.nodeValue;
          if (!v || v.trim().length < MIN_LEN) return NodeFilter.FILTER_REJECT;
          var p = n.parentElement;
          if (!p) return NodeFilter.FILTER_REJECT;
          if (SKIP_TAGS[p.tagName]) return NodeFilter.FILTER_REJECT;
          if (p.closest && p.closest(SKIP_SEL)) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        }
      });
    } catch (e) { return out; }

    var node;
    while ((node = walker.nextNode())) {
      var p = node.parentElement;
      if (!p || seen.has(p) || p.__imtDone || p.__imtQueued) continue;
      seen.add(p);
      out.push(p);
      if (out.length > 1500) break;
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
    var blocky = isBlocky(el);

    // عنصر inline (متل span بعنوان يوتيوب) → الترجمة سطر جديد تحته
    if (!blocky) {
      node.className = 'imt-trans imt-block';
      try { el.insertAdjacentElement('afterend', node); return; }
      catch (e) { /* نكمل بالطريقة العادية */ }
    }

    var inside = (tag === 'TD' || tag === 'TH' || tag === 'CAPTION' ||
                  tag === 'SUMMARY' || tag === 'LI');

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
  var CONC = 2;
  var BATCH = 5;

  function tuneFromConfig() {
    CONC = CFG.conc || 2;
    BATCH = CFG.batch || 5;
  }

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
    while (batch.length < BATCH && queue.length) {
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

  /* -------------------- ترجمة النص المظلَّل -------------------- */
  var selBubble = null;
  var selTimer = null;

  function hideSel() {
    if (selBubble) { selBubble.remove(); selBubble = null; }
  }

  function onSelectionChange() {
    clearTimeout(selTimer);
    selTimer = setTimeout(handleSelection, 320);
  }

  function handleSelection() {
    if (!CFG.selection) { hideSel(); return; }
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) { hideSel(); return; }
    var text = (sel.toString() || '').trim();
    if (text.length < 2 || text.length > 900) { hideSel(); return; }

    var node = sel.anchorNode;
    var pe = node && (node.nodeType === 1 ? node : node.parentElement);
    if (pe && pe.closest && pe.closest('.imt-sel,.imt-trans,.imt-inp,.imt-ui')) return;

    var rect = null;
    try { rect = sel.getRangeAt(0).getBoundingClientRect(); } catch (e) {}
    if (!rect || (rect.width === 0 && rect.height === 0)) return;

    showSel(rect, text);
  }

  function showSel(rect, text) {
    hideSel();
    selBubble = document.createElement('div');
    selBubble.className = 'imt-sel';
    selBubble.setAttribute('data-imt-skip', '1');

    var w = Math.min(340, window.innerWidth * 0.88);
    var left = rect.left + window.pageXOffset + rect.width / 2 - w / 2;
    left = Math.max(8, Math.min(left, window.innerWidth - w - 8));
    selBubble.style.left = left + 'px';
    selBubble.style.top = (rect.bottom + window.pageYOffset + 8) + 'px';
    selBubble.style.width = w + 'px';

    var src = document.createElement('div');
    src.className = 's';
    src.textContent = text;

    var body = document.createElement('div');
    body.className = 'b';
    body.textContent = '…';

    var acts = document.createElement('div');
    acts.className = 'a';
    var aCopy = document.createElement('span'); aCopy.textContent = '📋 نسخ';
    var aClose = document.createElement('span'); aClose.textContent = '✕ إغلاق';
    aCopy.addEventListener('click', function (e) {
      e.stopPropagation();
      try {
        if (navigator.clipboard) navigator.clipboard.writeText(body.textContent);
        else if (ImtNative && ImtNative.notify) ImtNative.notify('النسخ غير مدعوم هون');
      } catch (err) {}
    });
    aClose.addEventListener('click', function (e) { e.stopPropagation(); hideSel(); });
    acts.appendChild(aCopy); acts.appendChild(aClose);

    selBubble.appendChild(src);
    selBubble.appendChild(body);
    selBubble.appendChild(acts);
    (document.body || document.documentElement).appendChild(selBubble);

    bridgeTranslate([text]).then(function (r) {
      if (!selBubble) return;
      var tr = ((r && r[0]) || '').trim();
      body.textContent = tr || 'تعذّرت الترجمة — جرّب مرة تانية';
    });
  }

  document.addEventListener('selectionchange', onSelectionChange);
  document.addEventListener('mouseup', onSelectionChange, true);
  document.addEventListener('touchend', function () { setTimeout(onSelectionChange, 60); }, true);
  document.addEventListener('mousedown', function (e) {
    if (selBubble && !selBubble.contains(e.target)) hideSel();
  }, true);

  /* -------------------- ترجمة صناديق الإدخال -------------------- */
  var BAD_INPUT = ['password', 'email', 'number', 'tel', 'date', 'file', 'checkbox',
                   'radio', 'range', 'color', 'hidden', 'submit', 'button'];

  function removeInpTr(el) {
    try {
      var host = el.parentElement && el.parentElement.parentElement;
      if (!host) return;
      var n = host.querySelector('.imt-inp');
      if (n) n.remove();
    } catch (e) {}
  }

  function doInputTr(el, text) {
    bridgeTranslate([text]).then(function (r) {
      var tr = ((r && r[0]) || '').trim();
      if (!tr || !el.isConnected) return;
      var anchor = el.parentElement;
      if (!anchor || !anchor.parentElement) return;
      removeInpTr(el);

      var box = document.createElement('div');
      box.className = 'imt-inp';
      box.setAttribute('data-imt-skip', '1');
      var t = document.createElement('div');
      t.className = 't';
      t.textContent = tr;
      box.appendChild(t);
      try { anchor.parentElement.insertBefore(box, anchor.nextSibling); } catch (e) {}
    });
  }

  document.addEventListener('input', function (e) {
    if (!CFG.input) return;
    var el = e.target;
    if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return;
    if (el.type && BAD_INPUT.indexOf(String(el.type).toLowerCase()) >= 0) return;
    if (el.closest && el.closest('.imt-sel')) return;

    clearTimeout(el.__imtT);
    var v = (el.value || '').trim();
    if (v.length < 3) { removeInpTr(el); return; }
    el.__imtT = setTimeout(function () { doInputTr(el, v); }, 650);
  }, true);

  /* -------------------- التحميل التدريجي مع التمرير -------------------- */
  var loadTimer = null;

  function startAutoLoad() {
    if (window.__imtScrollHooked) return;
    window.__imtScrollHooked = true;
    var handler = function () {
      if (!running) return;
      clearTimeout(loadTimer);
      loadTimer = setTimeout(function () {
        if (!running) return;
        var more = collect();
        if (more.length) {
          stats.total += more.length;
          report();
          more.forEach(enqueue);
        }
      }, 320);
    };
    window.addEventListener('scroll', handler, { passive: true });
    document.addEventListener('scroll', handler, { passive: true, capture: true });
  }

  /* -------------------- الواجهة البرمجية -------------------- */
  window.__imtStart = function () {
    readConfig();
    if (running) return;
    running = true;
    injectCSS();
    startObserver();
    tuneFromConfig();
    var found = collect();
    stats.total = found.length;
    stats.done = 0;
    report();
    found.forEach(enqueue);
    startAutoLoad();
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
    injectCSS();
    if (CFG.enabled) window.__imtStart();
  };

  // إقلاع مبكر
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { window.__imtBoot(); });
  } else {
    window.__imtBoot();
  }
})();
