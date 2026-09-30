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
    '  opacity:.95;border-inline-start:3px solid rgba(110,150,255,.6);padding-inline-start:9px;}',
    /* ---- ترجمات الفيديو (يوتيوب) ---- */
    '.imt-subs{position:absolute;left:5%;right:5%;bottom:7%;z-index:2147483645;text-align:center;',
    '  pointer-events:none;display:none;}',
    '.imt-subs .s1{font-size:11px;line-height:1.3;color:#c9d2e0;direction:ltr;opacity:.85;',
    '  text-shadow:0 1px 3px #000,0 0 2px #000;margin-bottom:2px;}',
    '.imt-subs .s2{font-size:16px;line-height:1.4;font-weight:700;color:#fff;',
    '  display:inline-block;padding:2px 10px;background:rgba(0,0,0,.45);border-radius:8px;',
    '  text-shadow:0 1px 4px #000;}',
    '.imt-subs.only .s1{display:none;}',
    /* ---- لوحة تشخيص يوتيوب ---- */
    '.imt-ytdbg{position:fixed;left:8px;top:8px;z-index:2147483647;background:rgba(0,0,0,.82);',
    '  color:#8CF;font:10px/1.5 monospace;padding:7px 9px;border-radius:8px;max-width:62vw;',
    '  white-space:pre-wrap;direction:ltr;text-align:left;pointer-events:none;border:1px solid #345;}'
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
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    var arr = [];
    try { arr = JSON.parse(json) || []; } catch (e) {}
    p.res(arr);
  };

  /** نتيجة مقطع واحد وصلت من البثّ — نعرضها لحظياً */
  window.__imtPartial = function (id, idx, text) {
    var p = pending[id];
    if (!p || !p.cb) return;
    try { p.cb(idx, text); } catch (e) {}
  };

  function bridgeTranslate(texts, onPartial) {
    return new Promise(function (resolve) {
      if (!available) { resolve([]); return; }
      var id = 'r' + (++seq);
      pending[id] = { res: resolve, cb: onPartial || null };
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
      var pending = queue.length + inflight * (BATCH || 5);
      try {
        if (ImtNative && ImtNative.status) ImtNative.status(stats.done, stats.done + pending);
      } catch (e) {}
    }, 250);
  }

  /* -------------------- جمع العناصر -------------------- */
  var SKIP_TAGS = {
    SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, CODE: 1, PRE: 1, KBD: 1, SAMP: 1, VAR: 1,
    TEXTAREA: 1, INPUT: 1, SELECT: 1, OPTION: 1, SVG: 1, CANVAS: 1, MATH: 1,
    IFRAME: 1, VIDEO: 1, AUDIO: 1, IMG: 1, BUTTON: 1, CITE: 1
  };
  var SKIP_SEL = '.notranslate,[translate="no"],.imt-trans,.imt-orig,[contenteditable="true"],[data-imt-skip]';
  /* ما نترجم أي شي جوّا هدول — حتى لو الأبناء عناصر عادية */
  var SKIP_ANC = 'pre,code,kbd,samp,var,textarea,select,option,script,style,noscript,' +
                 'svg,canvas,math,.imt-trans,.imt-orig,.imt-sel,.imt-inp,.imt-inp *,' +
                 '.notranslate,[translate="no"],[contenteditable="true"],[data-imt-skip],.mw-highlight,.highlight';
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

  /** هل العنصر ظاهر فعلاً؟ — نتجاهل المخفي (قوائم مطويّة، نوافذ مسكّرة…) */
  function isRendered(el) {
    try { return el.getClientRects().length > 0; } catch (e) { return true; }
  }

  function eligible(el) {
    if (SKIP_TAGS[el.tagName]) return false;
    if (el.isContentEditable) return false;
    if (el.closest(SKIP_SEL)) return false;
    if (el === document.body || el === document.documentElement) return false;
    if (el.closest(SKIP_ANC)) return false;
    if (!isBlocky(el)) return false;
    if (!isRendered(el)) return false;
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
      var vw = window.innerWidth || 400;
      // عمودياً: هامش ٨٠٠ بكسل · أفقياً: نتجاهل اللي برّا الشاشة (كاروسيلات)
      if (r.bottom < -800 || r.top > vh + 800) return false;
      if (r.right < -250 || r.left > vw + 250) return false;
      return true;
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
    // ملاحظة: collectTextNodes بتضيف للـ seen بنفسها، فما منعيد الفحص هون
    var tn = collectTextNodes(seen);
    for (var j = 0; j < tn.length && out.length < 4000; j++) out.push(tn[j]);
    // نرجّع بس اللي قريب من الشاشة — الباقي بينضاف مع التمرير
    if (CFG.lazy) {
      var vis = [];
      for (var k = 0; k < out.length; k++) if (nearViewport(out[k])) vis.push(out[k]);
      return vis;
    }
    return out;
  }

  /** كل عنصر بيحتوي نصاً مباشراً — بلا شرط block و بلا شرط "ما تحته عناصر" */
  function collectTextNodes(seenOuter) {
    var out = [];
    if (!document.body) return out;
    var seen = seenOuter || new Set();
    var walker;
    try {
      walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
        acceptNode: function (n) {
          var v = n.nodeValue;
          if (!v || v.trim().length < MIN_LEN) return NodeFilter.FILTER_REJECT;
          var p = n.parentElement;
          if (!p) return NodeFilter.FILTER_REJECT;
          if (SKIP_TAGS[p.tagName]) return NodeFilter.FILTER_REJECT;
          if (p.closest && p.closest(SKIP_ANC)) return NodeFilter.FILTER_REJECT;
          if (!isRendered(p)) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        }
      });
    } catch (e) { return out; }

    var node;
    while ((node = walker.nextNode())) {
      var p = node.parentElement;
      if (!p || seen.has(p) || p.__imtDone || p.__imtQueued) continue;

      // ما نضيف عنصر إذا أحد **أجداده** رح يتنترجم — منشان ما نكرّر نفس النص
      var anc = p.parentElement, skip = false;
      while (anc && anc !== document.body && anc !== document.documentElement) {
        if (seen.has(anc) || anc.__imtDone || anc.__imtQueued) { skip = true; break; }
        anc = anc.parentElement;
      }
      if (skip) continue;

      seen.add(p);
      out.push(p);
      if (out.length > 1500) break;
    }
    return out;
  }

  /* -------------------- العرض -------------------- */
  function render(el, text) {
    if (!text || !el.isConnected) return;

    // تحديث النص الموجود بدل إضافة ترجمة تانية
    if (el.__imtNode && el.__imtNode.isConnected) { el.__imtNode.textContent = text; return; }
    for (var i = 0; i < el.children.length; i++) {
      if (el.children[i].classList && el.children[i].classList.contains('imt-trans')) {
        el.children[i].textContent = text;
        el.__imtNode = el.children[i];
        return;
      }
    }

    el.__imtDone = true;
    el.__imtQueued = false;
    el.classList.add('imt-done');          // ← علامة حقيقية تُمسح لاحقاً
    el.classList.remove('imt-loading');
    stats.done++;
    report();

    var node = document.createElement('span');
    node.className = 'imt-trans';
    node.setAttribute('data-imt-skip', '1');
    node.textContent = text;

    var tag = el.tagName;
    var blocky = isBlocky(el);
    var inside = (tag === 'TD' || tag === 'TH' || tag === 'CAPTION' ||
                  tag === 'SUMMARY' || tag === 'LI');

    /* ---- وضع الاستبدال: يشتغل مع المضمّن والكتلي ---- */
    if (CFG.mode === 'replace') {
      var orig = document.createElement('span');
      orig.className = 'imt-orig';
      orig.setAttribute('data-imt-skip', '1');
      while (el.firstChild) orig.appendChild(el.firstChild);
      el.appendChild(orig);
      if (!blocky) node.className = 'imt-trans imt-block';
      el.appendChild(node);
      el.__imtNode = node;
      return;
    }

    // عنصر مضمّن (متل span بعنوان يوتيوب) → الترجمة سطر جديد تحته
    if (!blocky) {
      node.className = 'imt-trans imt-block';
      try { el.insertAdjacentElement('afterend', node); el.__imtNode = node; return; }
      catch (e) { /* نكمل بالطريقة العادية */ }
    }

    if (inside) { el.appendChild(node); el.__imtNode = node; }
    else {
      try { el.insertAdjacentElement('afterend', node); el.__imtNode = node; }
      catch (e) { el.appendChild(node); el.__imtNode = node; }
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
    var d = document.querySelectorAll('.imt-done, .imt-loading');
    for (var m = 0; m < d.length; m++) {
      d[m].classList.remove('imt-done');
      d[m].classList.remove('imt-loading');
      d[m].__imtDone = false;
      d[m].__imtQueued = false;
      d[m].__imtNode = null;
    }
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

  var firstBatch = true;

  function pump() {
    if (!running || inflight >= CONC || !queue.length) return;

    var batch = [];
    // الدفعة الأولى صغيرة → أول ترجمة تظهر بسرعة، وبعدها دفعات أكبر
    var limit = firstBatch ? Math.min(3, BATCH) : BATCH;
    while (batch.length < limit && queue.length) {
      var el = queue.shift();
      if (!el.isConnected || el.__imtDone) continue;   // ← ما نمسح علامة الانتظار هون
      var t = getText(el);
      if (!t) { el.classList.remove('imt-loading'); continue; }
      batch.push([el, t]);
    }
    if (!batch.length) { if (queue.length) setTimeout(pump, 30); return; }
    firstBatch = false;

    inflight++;
    var texts = batch.map(function (b) { return b[1]; });

    bridgeTranslate(texts, function (idx, txt) {
      var b = batch[idx];
      if (b && txt) { try { render(b[0], txt); } catch (e) {} }
    }).then(function (list) {
      if (!running) return;
      batch.forEach(function (b, i) {
        var r = ((list && list[i]) || '').trim();
        if (r) render(b[0], r);
        else { b[0].classList.remove('imt-loading'); b[0].__imtQueued = false; }
      });
    })['catch'](function () {
      batch.forEach(function (b) { b[0].classList.remove('imt-loading'); b[0].__imtQueued = false; });
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

  /* ===== الطريقة الذكية: نستبدل نص يوتيوب نفسه بدل ما نبني تراكب ===== */
  var YR = { cache: {}, miss: {}, last: '' };

  function ytReplaceTick() {
    var win = document.querySelector('.caption-window');
    if (!win) return;

    var txt = (win.textContent || '').replace(/\s+/g, ' ').trim();
    if (!txt) return;
    if (txt === win.__imtOut) return;        // نحنا اللي كتبناها
    if (txt === win.__imtSrc) return;        // ما تغيّرت من يوتيوب

    win.__imtSrc = txt;
    var k = ytNorm(txt);
    if (!k) return;

    var t = YR.cache[k];
    if (!t && YT.loaded) {
      var hit = ytLookup(txt);
      if (hit && hit.tr) t = hit.tr;
    }
    if (t) {
      ytWriteCaption(win, t, txt);
      return;
    }

    // ترجمة لحظية (ما لقيناها جاهزة)
    ytWriteCaption(win, '…', txt);
    if (YR.miss[k]) return;
    YR.miss[k] = 1;
    bridgeTranslate([txt]).then(function (r) {
      var tr = ((r && r[0]) || '').trim();
      if (!tr) { delete YR.miss[k]; return; }
      YR.cache[k] = tr;
      var w = document.querySelector('.caption-window');
      if (w && w.textContent && ytNorm(w.textContent) === k) ytWriteCaption(w, tr, txt);
    })['catch'](function () { delete YR.miss[k]; });
  }

  function ytWriteCaption(win, tr, orig) {
    win.textContent = '';
    if (CFG.subs === 'both') {
      var a = document.createElement('span');
      a.textContent = orig;
      a.style.cssText = 'display:block;font-size:.72em;opacity:.8;direction:ltr;';
      win.appendChild(a);
    }
    var b = document.createElement('span');
    b.textContent = tr;
    b.style.cssText = 'display:block;font-weight:600;';
    win.appendChild(b);
    win.__imtOut = tr;
  }

  /* ===== لوحة تشخيص — تخلّي المشكلة تبان بصورة وحدة ===== */
  var DBG = { el: null, msg: '' };

  function ytDbg(line) { DBG.msg = line; }

  function ytDbgTick() {
    var on = isYouTube() && (/\/watch|\/shorts/.test(location.pathname));
    if (!on) { if (DBG.el) { DBG.el.remove(); DBG.el = null; } return; }
    if (!DBG.el) {
      DBG.el = document.createElement('div');
      DBG.el.className = 'imt-ytdbg';
      DBG.el.setAttribute('data-imt-skip', '1');
      (document.documentElement || document.body).appendChild(DBG.el);
    }
    var win = document.querySelector('.caption-window');
    var segs = document.querySelectorAll('.ytp-caption-segment');
    var segText = segs.length ? (segs[0].textContent || '').slice(0, 30) : '';
    var v = document.querySelector('video');
    var trCount = 0;
    for (var i = 0; i < YT.cues.length; i++) if (YT.cues[i].tr) trCount++;
    var line =
      'runner: ' + (YT.loaded ? 'CUES' : (YT.live ? 'LIVE' : 'SEARCH')) + '\n' +
      'cues: ' + YT.cues.length + '  tr: ' + trCount + '\n' +
      'captionWin: ' + (win ? 'YES' : 'no') + '  segs: ' + segs.length + '\n' +
      'ccBtn: ' + (document.querySelector('.ytp-subtitles-button') ? 'yes' : 'NO') + '\n' +
      'video: ' + (v ? (Math.round(v.currentTime) + 's ' + (v.paused ? 'PAUSED' : 'play')) : 'none') + '\n' +
      'subs:' + (CFG.subs || '?') + '  hits: ' + YT.hitIdx + '\n' +
      (DBG.msg ? 'msg: ' + DBG.msg : '');
    DBG.el.textContent = line;

    // نسخة مختصرة لشريط الحالة (تظهر بالتقاط الشاشة عن بُعد)
    var short = 'YT:' + (YT.loaded ? 'CUES' : (YT.live ? 'LIVE' : 'SRCH')) +
      ' cues=' + YT.cues.length + ' tr=' + trCount +
      ' win=' + (win ? 1 : 0) + ' seg=' + segs.length +
      ' cc=' + (document.querySelector('.ytp-subtitles-button') ? 1 : 0) +
      ' v=' + (v ? Math.round(v.currentTime) + 's' + (v.paused ? 'P' : '>') : '-');
    if (short !== DBG.lastShort) {
      DBG.lastShort = short;
      try { if (ImtNative && ImtNative.ytStatus) ImtNative.ytStatus(short); } catch (e) {}
    }
  }

  /* ==================== ترجمات يوتيوب الثنائية ==================== */
  /**
   * ثلاث طرق بالترتيب:
   *  ١) مسار الترجمات من بيانات المشغّل (Kotlin) — الأسرع إذا سمح يوتيوب
   *  ٢) نقرأ cues من عنصر الفيديو نفسه (video.textTracks) — الأضمن من جوّا المتصفح
   *  ٣) وضع مباشر: نقرأ نص الترجمة المعروض ونترجمو لحظياً — خطة أخيرة
   */
  var YT = {
    cues: [], ov: null, el1: null, el2: null, lastIdx: -2, timer: null,
    loaded: false, loading: false, waiting: {}, seq: 0,
    live: false, tries: 0, ccClicked: false, liveLast: '', liveCache: {},
    map: {}, pending: {}, livePending: {}, hitIdx: -1, lastShown: '', lastHref: ''
  };

  function isYouTube() {
    var h = location.hostname;
    return /(^|\.)youtube\.com$/.test(h) || h === 'youtu.be';
  }
  function isWatchPage() {
    return /^\/(watch|shorts|embed|live)/.test(location.pathname) || /[?&]v=/.test(location.search);
  }

  /** يوتيوب أحياناً بيرجّع رابط نسبي — لازم نكمّلو */
  function ytAbs(u) {
    if (!u) return null;
    u = u.replace(/\\u0026/g, '&').replace(/\\\//g, '/');
    if (u.charAt(0) === '/') return location.origin + u;
    if (!/^https?:/.test(u)) return null;
    return u;
  }

  function ytCaptionUrl() {
    // ١) من كائن المشغّل
    try {
      var pr = window.ytInitialPlayerResponse;
      var tracks = pr && pr.captions && pr.captions.playerCaptionsTracklistRenderer
                   && pr.captions.playerCaptionsTracklistRenderer.captionTracks;
      if (tracks && tracks.length) {
        var pick = null;
        for (var i = 0; i < tracks.length; i++) if (tracks[i].kind === 'asr') { pick = tracks[i]; break; }
        if (!pick) pick = tracks[0];
        var a = ytAbs(pick && pick.baseUrl);
        if (a) return a;
      }
    } catch (e) {}

    // ٢) من سكربتات الصفحة (مضمّن بالـ HTML، وهاد بيشتغل حتى قبل تشغيل الفيديو)
    try {
      var scripts = document.querySelectorAll('script');
      for (var k = 0; k < scripts.length; k++) {
        var s = scripts[k].textContent || '';
        if (s.indexOf('captionTracks') < 0) continue;
        var m = /"captionTracks"\s*:\s*(\[[\s\S]*?\}\s*\])/.exec(s);
        if (!m) continue;
        var arr = JSON.parse(m[1]);
        for (var j = 0; j < arr.length; j++) {
          var b = ytAbs(arr[j] && arr[j].baseUrl);
          if (b) return b;
        }
      }
    } catch (e) {}
    return null;
  }

  /** هل ترجمات يوتيوب مفعّلة هلّق؟ */
  function ytCCIsOn() {
    try {
      var p = document.getElementById('movie_player');
      if (p && p.isSubtitlesOn) return !!p.isSubtitlesOn();
    } catch (e) {}
    try {
      var b = document.querySelector('.ytp-subtitles-button');
      if (b) return b.getAttribute('aria-pressed') === 'true';
    } catch (e) {}
    return false;
  }

  /** نحاول نفعّل ترجمات يوتيوب — بعدة طرق */
  function ytEnableCC() {
    try {
      var p = document.getElementById('movie_player');
      if (p) {
        try { if (p.isSubtitlesOn && p.isSubtitlesOn()) return true; } catch (e) {}
        try { if (p.toggleSubtitlesOn) { p.toggleSubtitlesOn(); return true; } } catch (e) {}
        try { if (p.setOption) { p.setOption('captions', 'track', {}); return true; } } catch (e) {}
        try { if (p.loadModule) p.loadModule('captions'); } catch (e) {}
      }
    } catch (e) {}
    try {
      var btn = document.querySelector(
        '.ytp-subtitles-button,button[aria-label*="ntertitel"],button[aria-label*="ubtitle"],' +
        'button[aria-label*="aption"],button[data-tooltip-target-id*="caption"]');
      if (btn) {
        if (btn.getAttribute('aria-pressed') === 'true') return true;
        btn.click();
        return true;
      }
    } catch (e) {}
    return false;
  }

  /** نقرأ كل مقاطع الترجمة من عنصر الفيديو */
  function ytCuesFromTrack() {
    var v = document.querySelector('video');
    if (!v || !v.textTracks || !v.textTracks.length) return null;
    for (var i = 0; i < v.textTracks.length; i++) {
      var tt = v.textTracks[i];
      if (!tt.cues || tt.cues.length < 2) continue;
      var out = [];
      for (var k = 0; k < tt.cues.length; k++) {
        var c = tt.cues[k];
        var t = String(c.text || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
        if (!t) continue;
        out.push({ s: Math.round(c.startTime * 1000), e: Math.round(c.endTime * 1000), t: t, tr: '' });
      }
      if (out.length > 1) return out;
    }
    return null;
  }

  function ytLiveText() {
    var els = document.querySelectorAll('.ytp-caption-segment');
    var s = '';
    for (var i = 0; i < els.length; i++) s += els[i].textContent;
    return s.replace(/\s+/g, ' ').trim();
  }

  function ytReset() {
    YT.cues = []; YT.loaded = false; YT.loading = false; YT.live = false;
    YT.lastIdx = -2; YT.waiting = {}; YT.tries = 0; YT.ccClicked = false;
    YT.liveLast = ''; YT.liveCache = {}; YT.map = {}; YT.pending = {};
    YT.livePending = {}; YT.hitIdx = -1; YT.lastShown = '';
    document.documentElement.classList.remove('imt-yt');
    if (YT.ov) { YT.ov.remove(); YT.ov = null; }
  }

  function ytRequest() {
    if (YT.loaded || YT.loading) return;
    var url = ytCaptionUrl();
    if (!url) return;                       // الطريقة ٢/٣ بتتولّى
    YT.loading = true;
    var id = 'c' + (++YT.seq);
    YT.waiting[id] = true;
    try { ImtNative.fetchCaptions(id, url); }
    catch (e) { YT.loading = false; return; }
    setTimeout(function () {
      if (YT.waiting[id]) { delete YT.waiting[id]; YT.loading = false; }   // بنكمّل للطريقة ٢
    }, 20000);
  }

  window.__imtCaptions = function (id, payload) {
    if (!YT.waiting[id]) return;
    delete YT.waiting[id];
    YT.loading = false;
    var data;
    try { data = JSON.parse(payload); } catch (e) { return; }
    if (!data || data.error || !data.length) return;    // نفشل بصمت ونكمّل للطريقة ٢
    ytLoadCues(data.slice(0));
  };

  function ytLoadCues(list) {
    YT.cues = list;
    for (var i = 0; i < YT.cues.length; i++) YT.cues[i].tr = YT.cues[i].tr || '';
    YT.loaded = true;
    YT.live = false;
    document.documentElement.classList.add('imt-yt');
    showBadge('✓ ' + YT.cues.length + ' سطر مترجم');
    ytBuildMap();
    ytTranslateCues();
  }

  function ytTranslateCues() {
    var CH = 10;
    for (var i = 0; i < YT.cues.length; i += CH) {
      (function (start) {
        var slice = YT.cues.slice(start, start + CH);
        bridgeTranslate(slice.map(function (c) { return c.t; }), function (idx, txt) {
          if (slice[idx] && txt) slice[idx].tr = txt;
        }).then(function (list) {
          if (!list) return;
          for (var k = 0; k < slice.length && k < list.length; k++) {
            var r = (list[k] || '').trim();
            if (r) slice[k].tr = r;
          }
        })['catch'](function () {});
      })(i);
    }
  }

  function ytEnsureOverlay() {
    var v = document.querySelector('video');
    if (!v) return null;
    // نحطه داخل حاوية المشغّل — مو بصفحة كاملة
    var host = document.querySelector('.html5-video-player') || v.parentElement;
    if (!host) return null;
    if (YT.ov && YT.ov.isConnected && YT.ov.parentElement === host) return YT.ov;
    if (YT.ov) { try { YT.ov.remove(); } catch (e) {} }
    YT.ov = document.createElement('div');
    YT.ov.className = 'imt-subs' + (CFG.subs === 'both' ? '' : ' only');
    YT.ov.setAttribute('data-imt-skip', '1');
    var a = document.createElement('div'); a.className = 's1';
    var b = document.createElement('div'); b.className = 's2';
    YT.ov.appendChild(a); YT.ov.appendChild(b);
    YT.el1 = a; YT.el2 = b;
    try {
      if (getComputedStyle(host).position === 'static') host.style.position = 'relative';
      host.appendChild(YT.ov);
    } catch (e) { return null; }
    return YT.ov;
  }

  function ytHide(show) {
    if (YT.ov) YT.ov.style.display = show ? 'block' : 'none';
  }

  function ytNorm(s) {
    return String(s || '').toLowerCase()
      .replace(/[\u2018\u2019\u201C\u201D]/g, '')
      .replace(/[^\p{L}\p{N}\s]/gu, ' ')
      .replace(/\s+/g, ' ').trim();
  }

  /** يبني خريطة نص → ترجمة بعد ما تخلص الترجمة المسبقة */
  function ytBuildMap() {
    YT.map = {};
    for (var i = 0; i < YT.cues.length; i++) {
      var n = YT.cues[i].n || (YT.cues[i].n = ytNorm(YT.cues[i].t));
      if (n && YT.map[n] == null) YT.map[n] = i;
    }
  }

  /**
   * يدوّر ترجمة النص اللي يوتيوب عم يعرضو.
   * يبحث أول بمحيط آخر موضع (سريع)، وإذا فشل → بحث كامل.
   */
  function ytLookup(shown) {
    var k = ytNorm(shown);
    if (!k || k.length < 2) return null;
    var n = YT.cues.length;
    var around = YT.hitIdx >= 0 ? YT.hitIdx : 0;

    for (var pass = 0; pass < 3; pass++) {
      var a, b;
      if (pass === 0) { a = Math.max(0, around - 15); b = Math.min(n, around + 45); }
      else if (pass === 1) { a = 0; b = Math.min(n, 400); }
      else { a = 0; b = n; }
      for (var i = a; i < b; i++) {
        var c = YT.cues[i];
        if (!c) continue;
        var t = c.n || (c.n = ytNorm(c.t));
        if (!t) continue;
        if (t === k) return { i: i, tr: c.tr };
        // تطابق جزئي: واحد بيحتوي التاني
        if (t.length > 6 && k.length > 6 && (t.indexOf(k) >= 0 || k.indexOf(t) >= 0)) {
          return { i: i, tr: c.tr };
        }
      }
      if (pass === 0 && n <= 400) break;
    }
    return null;
  }

  function ytHideNative() {
    try {
      var els = document.querySelectorAll(
        '.ytp-caption-window-container,.caption-window,.ytp-caption-segment,.captions-text');
      for (var i = 0; i < els.length; i++) {
        var e = els[i];
        if (e.style.visibility !== 'hidden') {
          e.style.opacity = '0';
          e.style.visibility = 'hidden';
        }
      }
    } catch (e) {}
  }

  function ytSetText(tr, orig) {
    if (!YT.ov) return;
    YT.ov.style.display = 'block';
    if (CFG.subs === 'both' && orig != null) { YT.el1.textContent = orig; }
    YT.el2.textContent = tr || '…';
  }

  function ytTick() {
    if (!running) return;
    ytDbgTick();

    /* ---- التحميل والبدائل ---- */
    if (!YT.loaded && !YT.live) {
      if (YT.tries % 10 === 0 && !ytCCIsOn()) ytEnableCC();   // نعيد المحاولة دايماً
      if (YT.tries % 6 === 0) {
        var c = ytCuesFromTrack();
        if (c) ytLoadCues(c);
      }
      YT.tries++;
      if (YT.tries > 250 && !YT.live) {
        YT.live = true;
        document.documentElement.classList.add('imt-yt');
        showBadge('وضع مباشر — ترجمة لحظية');
      }
      return;
    }

    if (!document.querySelector('video')) return;

    /* ---- الأفضل: نستبدل نص ترجمات يوتيوب نفسه (تنسيق ومزامنة مثاليين) ---- */
    ytReplaceTick();
    if (document.querySelector('.caption-window')) {
      if (YT.ov) ytHide(false);          // ما منحتاج تراكب
      return;
    }

    ytEnsureOverlay();
    ytHideNative();

    /* ---- الطريقة الأقوى: نتبع اللي يوتيوب عم يعرضو (مزامنة مثالية مع الصوت) ---- */
    var shown = ytLiveText();
    if (shown && shown !== YT.lastShown) {
      YT.lastShown = shown;
      var hit = YT.loaded ? ytLookup(shown) : null;
      if (hit) {
        YT.hitIdx = hit.i;
        if (hit.tr) { ytSetText(hit.tr, shown); return; }      // ✓ ترجمة جاهزة — فورية
        // مقطع موجود بس الترجمة لسا ما وصلت
        ytSetText("\u2026", shown);
        if (!YT.pending[hit.i]) {
          YT.pending[hit.i] = 1;
          (function (idx) {
            bridgeTranslate([YT.cues[idx].t]).then(function (r) {
              var t = ((r && r[0]) || '').trim();
              delete YT.pending[idx];
              if (t) { YT.cues[idx].tr = t; if (YT.lastShown && ytNorm(YT.lastShown) === YT.cues[idx].n) ytSetText(t); }
            })['catch'](function () { delete YT.pending[idx]; });
          })(hit.i);
        }
        return;
      }
      // ما لقيناه بالمقاطع → نترجم لحظياً
      var cached = YT.liveCache[shown];
      if (cached) { ytSetText(cached, shown); return; }
      ytSetText("\u2026", shown);
      if (!YT.livePending[shown]) {
        YT.livePending[shown] = 1;
        (function (key) {
          bridgeTranslate([key]).then(function (r) {
            var t = ((r && r[0]) || '').trim();
            delete YT.livePending[key];
            if (t) {
              YT.liveCache[key] = t;
              if (YT.lastShown === key) ytSetText(t, key);
            }
          })['catch'](function () { delete YT.livePending[key]; });
        })(shown);
      }
      return;
    }

    /* ---- خطة بديلة: لو يوتيوب ما عم يعرض شي، نستعمل توقيت الملف ---- */
    if (!YT.loaded || shown) return;
    var v = document.querySelector('video');
    var ms = v.currentTime * 1000;
    var idx = -1;
    for (var i = 0; i < YT.cues.length; i++) {
      var cu = YT.cues[i];
      if (ms >= cu.s && ms < cu.e) { idx = i; break; }
    }
    if (idx === YT.lastIdx) return;
    YT.lastIdx = idx;
    if (idx < 0) { ytHide(false); return; }
    ytSetText(YT.cues[idx].tr, YT.cues[idx].t);
  }

  function ytStart() {
    if (!isYouTube() || !isWatchPage()) return;
    if (!YT.timer) YT.timer = setInterval(ytTick, 130);
    ytRequest();
  }

  function ytStop() {
    if (YT.timer) { clearInterval(YT.timer); YT.timer = null; }
    ytHide(false);
  }

  window.__imtYtReset = function () { ytReset(); if (running) setTimeout(ytStart, 1500); };

  window.addEventListener('yt-navigate-finish', function () { window.__imtYtReset(); });
  document.addEventListener('yt-page-data-updated', function () {
    if (running && !YT.loaded && !YT.live && !YT.loading) setTimeout(ytStart, 800);
  });
  setInterval(function () {
    if (!isYouTube()) return;
    if (location.href !== YT.lastHref) { YT.lastHref = location.href; window.__imtYtReset(); }
  }, 1500);
  YT.lastHref = location.href;

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
    if (found.length === 0) {
      showBadge('ما لقيت نص جديد بهالصفحة — جرّب تمرّر أو أعد تحميل الصفحة');
    }
    found.forEach(enqueue);
    startAutoLoad();
    setTimeout(ytStart, 1500);          // ترجمات الفيديو (يوتيوب)
    showBadge('Immersive-Me · ' + CFG.target + ' · ' + (CFG.provider || '') + ' · ' + found.length + ' مقطع');
  };

  window.__imtStop = function () {
    running = false;
    queue = [];
    firstBatch = true;
    ytStop();
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
