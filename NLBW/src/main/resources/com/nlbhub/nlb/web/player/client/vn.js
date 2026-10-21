// Экран визуальной новеллы веб-плеера NLB — порт GameScreen::renderVisualNovel (NLBNavigator):
// экран 1920×1080, вписанный в окно с полями; картинка страницы во весь экран, графические
// объекты на своих местах (vn.lua) с эффектами появления и подписями, полоса текста внизу
// (vn:geom экспорта) — текст порциями, как paginator, затем экран вариантов; строка инвентаря;
// кнопка меню темы. Оформление — тема INSTEAD (api/books/<книга>/theme).
window.NLBVisualNovel = function (ctx) {
  'use strict';
  const W = 1920, H = 1080;
  const ANCHORS = ['LeftTop', 'MiddleTop', 'RightTop', 'LeftMiddle', 'MiddleMiddle', 'RightMiddle',
    'LeftBottom', 'MiddleBottom', 'RightBottom'];
  const TEXT_FAMILY = 'nlb-vn-text', INV_FAMILY = 'nlb-vn-inv', FALLBACK = 'Georgia, "Times New Roman", serif';
  // Тема VN экспорта NLB по умолчанию (ScreenTheme::defaults), если тему не удалось получить
  const DEFAULT_THEME = {
    background: '#011322', scale: 1, align: 'justify', inventoryHorizontal: true, inventoryAlign: 'center',
    menuButton: '', menuButtonRu: '',
    text: { x: 248, y: 838, w: 1424, h: 204, fg: '#ffffff', link: '#00ff00', alink: '#ffd700', font: '', fontSize: 32, lineHeight: 1.3 },
    inventory: { x: 0, y: 1050, w: 1920, h: 30, fg: '#ffffff', link: '#00ff00', alink: '#ffd700', font: '', fontSize: 24, lineHeight: 1.3 }
  };

  const $ = (id) => document.getElementById(id);
  const el = ctx.el;
  let theme = DEFAULT_THEME;
  let state = null;
  const vn = {
    portion: 0,            // текущая порция текста
    voiced: -1,            // порция, на которой уже запущена фраза озвучки
    voicePtr: 0, voicesKey: '',
    showChoices: false,    // экран вариантов
    scroll: 0,             // листание строк внутри порции (или текста в режиме «варианты в тексте»)
    pageH: 0, more: false, morePortions: false,
    lastPage: null, lastReaction: '',
    shown: new Map(),      // id -> был виден (эффекты появления и исчезновения)
    rects: new Map(),      // id -> прямоугольник на последнем кадре
    nodes: new Map()       // id -> элемент объекта
  };
  let choicesScreen = ctx.store.get('nlb.choicesScreen', true);
  let renderToken = 0;

  // ------------------------------------------------------------------ шрифты и размеры
  const canvas = document.createElement('canvas').getContext('2d');
  const fontCss = (size, family) => size + 'px ' + family;
  function textW(size, family, text) {
    canvas.font = fontCss(size, family);
    return canvas.measureText(text).width;
  }
  // Высота строки шрифта (как TTF_FontHeight): восхождение + нисхождение
  function fontHeight(size, family) {
    canvas.font = fontCss(size, family);
    const m = canvas.measureText('Ag');
    const h = (m.fontBoundingBoxAscent || 0) + (m.fontBoundingBoxDescent || 0);
    return h > 0 ? h : size * 1.2;
  }
  const family = (w, own) => (w && w.font ? '"' + own + '", ' : '') + FALLBACK;
  const textFamily = () => family(theme.text, TEXT_FAMILY);
  const invFamily = () => family(theme.inventory, INV_FAMILY);
  const themeUrl = (path) => 'theme/' + encodeURIComponent(ctx.bookId()) + '/' + path.split('/').map(encodeURIComponent).join('/');

  async function loadTheme() {
    try {
      const data = await ctx.api('books/' + encodeURIComponent(ctx.bookId()) + '/theme');
      theme = data.vn || DEFAULT_THEME;
    } catch (e) {
      theme = DEFAULT_THEME;
    }
    const faces = [];
    for (const [w, name] of [[theme.text, TEXT_FAMILY], [theme.inventory, INV_FAMILY]]) {
      if (!w.font) continue;
      try {
        const face = new FontFace(name, 'url("' + themeUrl(w.font) + '")');
        document.fonts.add(face);
        faces.push(face.load());
      } catch (e) { /* шрифт не загрузился — шрифт браузера */ }
    }
    await Promise.all(faces.map((p) => p.catch(() => null)));
    $('vn-stage').style.background = theme.background;
  }

  // Размеры картинок (раскладка объектов по vn.lua считает от них)
  const sizes = new Map();
  function imageSize(url) {
    if (!url) return Promise.resolve(null);
    if (sizes.has(url)) return sizes.get(url);
    const p = new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve({ w: img.naturalWidth, h: img.naturalHeight });
      img.onerror = () => resolve(null);
      img.src = url;
    });
    sizes.set(url, p);
    return p;
  }

  // ------------------------------------------------------------------ экран 1920×1080 в окне
  function fit() {
    const k = Math.min(window.innerWidth / W, window.innerHeight / H);
    const stage = $('vn-stage');
    stage.style.transform = 'translate(' + ((window.innerWidth - W * k) / 2) + 'px,' + ((window.innerHeight - H * k) / 2) + 'px) scale(' + k + ')';
    document.documentElement.style.setProperty('--vn-scale', String(k));
  }
  window.addEventListener('resize', fit);

  // ------------------------------------------------------------------ текст
  // «--» -> «—» и кавычки, как модули dash и quotes STEAD (экспорт NLB подключает их всегда)
  function dashes(s) {
    return s.replace(/(^|[^-])--(?!-)/g, '$1—');
  }
  function quotes(s) {
    if (!/["',_]/.test(s)) return s;
    s = s.replace(/_"/g, '«').replace(/"_/g, '»').replace(/"([^"]*)"/g, '«$1»');
    return s.replace(/,,/g, '„').replace(/''/g, '”');
  }
  const typo = (s) => quotes(dashes(s));

  function runNode(run) {
    const text = typo(run.text);
    if (run.kind === 'ObjectLink') return ctx.link(text, () => ctx.command('object', { id: run.target }), 'vn-hit');
    if (run.kind === 'ChoiceLink') {
      return run.target === '-1' ? ctx.link(text, () => ctx.command('look'), 'vn-hit')
        : ctx.link(text, () => ctx.command('choose', { index: Number(run.target) }), 'vn-hit');
    }
    return document.createTextNode(text);
  }
  const isBlank = (p) => !p.runs.length || p.runs.every((r) => !r.text.trim());

  const choicesMode = () => choicesScreen;
  const enabledChoices = () => state.screen.choices.filter((c) => c.enabled);
  function singleTrivialChoice() {
    const enabled = enabledChoices();
    return enabled.length === 1 && enabled[0].trivial ? enabled[0].index : -1;
  }
  const anyText = (s) => s.pageText.some((p) => p.runs.length) || s.reactionText.some((p) => p.runs.length);

  // ------------------------------------------------------------------ ход
  function nextVoice() {
    const voices = state.voices || [];
    if (vn.voicePtr < voices.length) ctx.playVoice(voices[vn.voicePtr++]);
  }

  // Новый снимок: command — команда, после которой он пришёл ('advance' — ход времени)
  function update(newState, command) {
    const before = state;
    state = newState;
    const s = state.screen;
    const reaction = JSON.stringify(s.reactionText);
    const voicesKey = JSON.stringify(state.voices || []);
    const fresh = command === 'restart' || command === 'tryAgain' || command === 'load';
    if (!before || fresh || s.pageId !== vn.lastPage) {
      // новая страница (или та же, но игра начата заново или загружена) — текст с первой
      // порции, озвучка заново
      vn.portion = 0;
      vn.voiced = -1;
      vn.scroll = 0;
      vn.showChoices = false;
      vn.voicePtr = 0;
    } else if (s.reactionText.length && reaction !== vn.lastReaction) {
      // ответ на действие на той же странице — с его первой порции
      vn.portion = state.pagePortions || 0;
      vn.scroll = 0;
    }
    if (voicesKey !== vn.voicesKey) vn.voicePtr = 0;
    vn.voicesKey = voicesKey;
    vn.lastPage = s.pageId;
    vn.lastReaction = reaction;
    if (command !== 'advance') afterCommand();
  }

  function afterCommand() {
    const s = state.screen;
    vn.showChoices = false;
    // Страница без текста: показывать нечего — сразу экран вариантов (вариант один тривиальный —
    // ждём «дальше», чтобы игрок увидел сцену)
    if (choicesMode() && !anyText(s) && singleTrivialChoice() < 0) advanceToChoices();
    // Конец игры на странице без текста — сразу сообщение
    if (s.finished && choicesMode() && !anyText(s) && !s.externalGame) gameOver();
  }

  function advanceToChoices() {
    if (!choicesMode() || vn.more || vn.showChoices) return false;
    if (!enabledChoices().length) return false;
    vn.showChoices = true;
    return true;
  }

  function gameOver() {
    ctx.dialog(ctx.ui().theEnd, [{ label: ctx.ui().tryAgain, action: () => ctx.command('tryAgain') }]);
  }

  // «Дальше» (пробел, щелчок мимо ссылок): строки порции, следующая порция, а после последней —
  // экран вариантов или переход по единственной тривиальной ссылке
  function advanceText() {
    if (vn.showChoices) return;
    if (vn.more) { vn.scroll += vn.pageH; render(); return; }
    if (vn.morePortions) { vn.portion++; vn.scroll = 0; render(); return; }
    if (state.screen.finished && !enabledChoices().length) { gameOver(); return; }
    const trivial = singleTrivialChoice();
    if (trivial >= 0) { ctx.command('choose', { index: trivial }); return; }
    if (advanceToChoices()) render();
  }

  // ------------------------------------------------------------------ рисование
  async function render() {
    const token = ++renderToken;
    const s = state.screen;
    // размеры картинок объектов — до раскладки
    const urls = [];
    for (const o of s.sceneObjects) if (o.graphical) urls.push(ctx.imageUrl(o.image));
    const loaded = await Promise.all(urls.map(imageSize));
    if (token !== renderToken) return;  // пришёл новый снимок
    const size = new Map(urls.map((u, i) => [u, loaded[i]]));
    fit();
    renderBackground(s);
    renderObjects(s, size);
    renderText(s);
    renderInventory(s);
    renderMenuButton();
    const hint = $('vn-hint');
    hint.hidden = !state.selected;
    hint.textContent = ctx.ui().itemSelected;
  }

  function renderBackground(s) {
    const bg = $('vn-bg');
    const url = ctx.imageUrl(s.pageImage);
    if (!url) { bg.hidden = true; bg.removeAttribute('src'); bg.dataset.key = ''; return; }
    bg.hidden = false;
    const key = JSON.stringify(s.pageImage);
    if (bg.dataset.key !== key) {
      bg.dataset.key = key;
      bg.src = url;
      ctx.animate(bg, s.pageImage);
    }
  }

  // Подпись, которую стоит рисовать: не внутреннее имя объекта (abilitiesTrigger)
  const isHumanLabel = (s) => !!s && /[ \u0080-\uffff]/.test(s);
  // Строки подписи (txtfn gobj.lua) — шрифтом подписей vn.lua: hudFont (STEINEMU 29), а строка
  // «== текст ==» — hudFont_H1 (45); пустая строка — отступ hud_indent_spacing (25)
  function labelLines(label) {
    return label.replace(/\r/g, '').split('\n').map((line) => {
      const h1 = /^== (.*) ==$/.exec(line);
      const size = h1 ? 45 : 29;
      return { text: h1 ? h1[1] : line, size: size, height: line ? fontHeight(size, textFamily()) : 25 };
    });
  }
  function labelNodes(node, lines, x, y) {
    for (const line of lines) {
      if (line.text) {
        const t = el('div', 'vn-label', line.text);
        t.style.left = x + 'px';
        t.style.top = y + 'px';
        t.style.font = fontCss(line.size, textFamily());
        t.style.lineHeight = line.height + 'px';
        node.appendChild(t);
      }
      y += line.height;
    }
  }

  // Прямоугольник объекта (GameScreen::objectRect): корневой — по углу привязки и arm (vn.lua
  // postoxy), вложенный — точка родителя + iarm, иначе столбиком под предыдущими детьми
  function objectRect(s, index, rects, size) {
    const o = s.sceneObjects[index];
    const img = size.get(ctx.imageUrl(o.image));
    let w = img ? img.w : 0, h = img ? img.h : 0;
    const textOnly = !img && isHumanLabel(o.label);
    let labelW = 0, labelH = 0, labelCoef = 0;
    if (textOnly) {
      for (const line of labelLines(o.label)) {
        if (line.text) labelW = Math.max(labelW, textW(line.size, textFamily(), line.text) + 9);
        labelH += line.height;
      }
      const row = Math.floor(ANCHORS.indexOf(o.placement.anchor) / 3);
      labelCoef = o.parentId ? 0.5 : row === 0 ? 0 : row === 1 ? 0.5 : 1;
      w = h = 0;
    }
    const finish = (x, y) => textOnly ? { x: x, y: y - labelH * labelCoef, w: labelW, h: labelH, textOnly: true }
      : { x: x, y: y, w: w, h: h };
    const p = o.placement;
    const parent = o.parentId ? rects.get(o.parentId) : null;
    if (parent) {
      let ax = 0, ay = 0;
      if (p.hasInnerArm) {
        ax = p.innerArmX; ay = p.innerArmY;
      } else {
        for (let i = 0; i < index; i++) {
          const prev = s.sceneObjects[i];
          if (prev.parentId !== o.parentId || prev.placement.hasInnerArm) continue;
          const r = rects.get(prev.id);
          if (r) ay += r.h;
        }
      }
      return finish(parent.x + ax, parent.y + ay);
    }
    const a = Math.max(0, ANCHORS.indexOf(p.anchor));
    const col = a % 3, row = Math.floor(a / 3);
    let x = col === 0 ? p.armX : col === 2 ? W - w : Math.floor((W - w) / 2);
    let y = row === 0 ? p.armY : row === 2 ? H - h : Math.floor((H - h) / 2);
    if (p.hasOffset) { x += p.offsetX; y += p.offsetY; }
    return finish(x, y);
  }

  // Эффект появления (исчезновения — в обратную сторону): кадров maxStep − curStep (иначе 25),
  // по 20 мс, как GameScreen::effectState
  function effectKeyframes(o, r) {
    const e = o.appearance.effect;
    if (e === 'FadeIn' || e === 'FadeOut') return [{ opacity: 0 }, { opacity: 1 }];
    if (e === 'ZoomIn' || e === 'ZoomOut') return [{ transform: 'scale(0.01)' }, { transform: 'scale(1)' }];
    if (e === 'MoveIn' || e === 'MoveOut') {
      let from;
      switch (o.appearance.direction) {
        case 'Right': from = 'translate(' + (W - r.x) + 'px, 0)'; break;
        case 'Top': from = 'translate(0, ' + (-(r.y + r.h)) + 'px)'; break;
        case 'Bottom': from = 'translate(0, ' + (H - r.y) + 'px)'; break;
        default: from = 'translate(' + (-(r.x + r.w)) + 'px, 0)'; break;
      }
      return [{ transform: from }, { transform: 'translate(0, 0)' }];
    }
    return null;
  }
  const effectSteps = (o) => Math.max(1, o.appearance.maxStep > o.appearance.curStep ? o.appearance.maxStep - o.appearance.curStep : 25);
  function playEffect(node, o, r, hiding, onDone) {
    const frames = effectKeyframes(o, r);
    if (!frames || !node.animate) { if (onDone) onDone(); return; }
    const animation = node.animate(hiding ? frames.slice().reverse() : frames,
      { duration: effectSteps(o) * 20, easing: 'linear', fill: 'both' });
    animation.onfinish = () => { if (onDone) onDone(); else animation.cancel(); };
  }
  // Эффект родителя действует и на детей (в vn.lua дети получают шаги родителя)
  function withChildren(s, id) {
    const ids = [id];
    for (const o of s.sceneObjects) if (ids.includes(o.parentId)) ids.push(o.id);
    return ids;
  }

  function objectNode(o) {
    let node = vn.nodes.get(o.id);
    if (!node) {
      node = el('div', 'vn-obj');
      node.dataset.id = o.id;
      vn.nodes.set(o.id, node);
    }
    return node;
  }

  function fillObject(node, o, r) {
    const key = JSON.stringify([o.image, o.hoverImage, o.label, o.active, r.textOnly]);
    node.style.left = r.x + 'px';
    node.style.top = r.y + 'px';
    node.style.width = r.w + 'px';
    node.style.height = r.h + 'px';
    if (node.dataset.key === key) return;
    node.dataset.key = key;
    node.textContent = '';
    node.classList.toggle('vn-hit', !!o.active);
    node.onclick = o.active ? (ev) => { ev.stopPropagation(); ctx.command('object', { id: o.id }); } : null;
    if (r.textOnly) {
      // объект без картинки — только подпись, строками (draw_hud в vn.lua)
      labelNodes(node, labelLines(o.label), 9, 0);
      return;
    }
    const img = el('img');
    img.src = ctx.imageUrl(o.image);
    img.alt = '';
    img.draggable = false;
    ctx.animate(img, o.image);
    node.appendChild(img);
    const hover = ctx.imageUrl(o.hoverImage);
    if (hover) {
      // morphover: под курсором — другая картинка
      node.onmouseenter = () => { img.src = hover; };
      node.onmouseleave = () => { img.src = ctx.imageUrl(o.image); };
    } else {
      node.onmouseenter = node.onmouseleave = null;
    }
    if (isHumanLabel(o.label)) {
      // подпись спрайта — справа от картинки (+4 +5 px), по вертикали — по углу привязки
      const lines = labelLines(o.label);
      const total = lines.reduce((sum, line) => sum + line.height, 0);
      const row = Math.floor(ANCHORS.indexOf(o.placement.anchor) / 3);
      const coef = o.parentId ? 0.5 : row === 0 ? 0 : row === 1 ? 0.5 : 1;
      labelNodes(node, lines, r.w + 9, r.h * coef - total * coef);
    }
  }

  function renderObjects(s, size) {
    const layer = $('vn-objects');
    const rects = new Map();
    const present = new Set();
    const shown = new Map();
    const samePage = vn.renderedPage === s.pageId;
    const appear = [], hide = [];
    s.sceneObjects.forEach((o, index) => {
      if (!o.graphical) return;
      present.add(o.id);
      const r = objectRect(s, index, rects, size);
      rects.set(o.id, r);
      const visible = o.visible && !o.collapsed;
      shown.set(o.id, visible);
      const was = !!vn.shown.get(o.id);
      const node = objectNode(o);
      fillObject(node, o, r);
      node.style.zIndex = String(index);
      if (node.parentNode !== layer) layer.appendChild(node);
      const e = o.appearance.effect;
      if (visible && !was && (e === 'FadeIn' || e === 'MoveIn' || e === 'ZoomIn')) appear.push([o, r]);
      if (!visible && was && e !== 'None' && e !== 'Overlap') hide.push([o, r, false]);
      if (!node.dataset.hiding) node.hidden = !visible;
    });
    // Объект исчез со страницы (взят, убран): эффект в обратную сторону на прежнем месте.
    // При смене страницы объекты уходят вместе со сценой — без эффекта
    for (const [id, node] of vn.nodes) {
      if (present.has(id)) continue;
      const old = before.get(id);
      const e = old ? old.appearance.effect : 'None';
      if (samePage && old && vn.shown.get(id) && e !== 'None' && e !== 'Overlap' && vn.rects.get(id)) {
        hide.push([old, vn.rects.get(id), true]);
      } else {
        node.remove();
        vn.nodes.delete(id);
      }
    }
    for (const [o, r] of appear) {
      for (const id of withChildren(s, o.id)) {
        const node = vn.nodes.get(id);
        if (node) playEffect(node, o, r, false, null);
      }
    }
    for (const [o, r, gone] of hide) {
      const node = vn.nodes.get(o.id);
      if (!node || node.dataset.hiding) continue;
      node.dataset.hiding = '1';
      node.hidden = false;
      playEffect(node, o, r, true, () => {
        delete node.dataset.hiding;
        if (gone) { node.remove(); vn.nodes.delete(o.id); } else { node.hidden = true; node.getAnimations().forEach((a) => a.cancel()); }
      });
    }
    // на экране вариантов объектов нет — как в INSTEAD, где на время выбора остаются фон и кнопка меню
    layer.hidden = vn.showChoices;
    vn.shown = shown;
    vn.rects = rects;
    vn.renderedPage = s.pageId;
    before.clear();
    for (const o of s.sceneObjects) before.set(o.id, o);
  }
  const before = new Map();  // объекты прошлого снимка (для эффекта исчезновения)

  function paragraphNode(p, cls) {
    const node = el('p', cls || '');
    for (const run of p.runs) node.appendChild(runNode(run));
    return node;
  }

  function renderText(s) {
    const band = $('vn-band'), box = $('vn-text'), inner = $('vn-text-inner'), choices = $('vn-choices');
    const t = theme.text;
    const fs = t.fontSize;
    const lh = Math.floor(fontHeight(fs, textFamily()) * t.lineHeight);
    inner.textContent = '';
    choices.hidden = !vn.showChoices;
    if (vn.showChoices) {
      band.hidden = true;
      renderChoicesScreen(s);
      return;
    }
    // Текст: порция (режим экрана вариантов) или текст страницы, ответ и варианты
    const paragraphs = [];
    if (choicesMode()) {
      const parts = state.portions;
      if (vn.portion >= parts.length) vn.portion = Math.max(0, parts.length - 1);
      if (parts.length) paragraphs.push(...parts[vn.portion]);
      vn.morePortions = vn.portion + 1 < parts.length;
      // озвучка: каждая новая порция — следующая фраза (paginator text_page -> nextsnd)
      if (parts.length && vn.portion !== vn.voiced) {
        vn.voiced = vn.portion;
        nextVoice();
      }
    } else {
      vn.morePortions = false;
      paragraphs.push(...s.pageText, ...s.reactionText);
    }
    const hasText = anyText(s) || (!choicesMode() && s.choices.length > 0);
    band.hidden = !hasText;
    if (!hasText) { vn.more = false; return; }
    // Полоса текста — vn:geom(8, 864, 1904, 184) экспорта: полупрозрачная, во всю ширину, текст
    // внутри с полями по 240 px (vn.lua: textpad = 8)
    const bandRect = { x: 8, y: 864, w: 1904, h: 184 };
    band.style.top = bandRect.y + 'px';
    band.style.height = (bandRect.h + 16) + 'px';
    const win = { x: bandRect.x + 240, y: bandRect.y + 8, w: bandRect.w - 480, h: bandRect.h - 16 };
    const pageH = Math.max(lh, Math.floor(win.h / lh) * lh);
    box.style.left = win.x + 'px';
    box.style.top = win.y + 'px';
    box.style.width = win.w + 'px';
    box.style.height = pageH + 'px';
    box.style.color = t.fg;
    box.style.setProperty('--vn-link', t.link);
    box.style.setProperty('--vn-alink', t.alink);
    box.style.font = fontCss(fs, textFamily());
    box.style.lineHeight = lh + 'px';
    box.style.textAlign = theme.align === 'justify' || theme.align === 'center' || theme.align === 'right' ? theme.align : 'left';
    for (const p of paragraphs) {
      if (isBlank(p)) {
        const gap = el('div', 'vn-gap');
        gap.style.height = Math.floor(lh / 2) + 'px';
        inner.appendChild(gap);
      } else {
        inner.appendChild(paragraphNode(p));
      }
    }
    if (!choicesMode()) {
      // варианты в тексте — нумерованным списком под ним
      let n = 0;
      for (const c of s.choices) {
        const p = el('p', 'vn-choice-line');
        if (c.enabled) {
          p.appendChild(ctx.link((++n) + '. ' + c.text, () => ctx.command('choose', { index: c.index }), 'vn-hit'));
        } else if (c.altText) {
          p.textContent = c.altText;
        } else {
          continue;
        }
        inner.appendChild(p);
      }
    }
    // Не уместившиеся строки листаются страницами окна; последняя страница не возвращается назад
    const content = inner.scrollHeight;
    vn.pageH = pageH;
    vn.scroll = Math.max(0, Math.min(vn.scroll, Math.max(0, content - lh)));
    inner.style.transform = 'translateY(' + (-vn.scroll) + 'px)';
    vn.more = vn.scroll + pageH < content - 1;
  }

  function renderChoicesScreen(s) {
    // Варианты посередине экрана на полупрозрачной панели (GameScreen::renderChoicesScreen)
    const panel = $('vn-choices');
    panel.textContent = '';
    const t = theme.text;
    const fs = t.fontSize * 1.25;
    const lh = fontHeight(fs, textFamily());
    const panelW = W * 0.675;
    panel.style.left = ((W - panelW) / 2) + 'px';
    panel.style.width = panelW + 'px';
    panel.style.font = fontCss(fs, textFamily());
    panel.style.lineHeight = lh + 'px';
    panel.style.color = t.fg;
    panel.style.setProperty('--vn-link', t.link);
    panel.style.setProperty('--vn-alink', t.alink);
    let n = 0;
    for (const c of s.choices) {
      if (!c.enabled && !c.altText) continue;
      const row = el('div', 'vn-choice' + (c.enabled ? ' vn-hit' : ' disabled'));
      row.style.marginBottom = (lh * 0.9) + 'px';
      if (c.enabled) {
        row.appendChild(el('span', 'num', (++n) + '. '));
        row.appendChild(el('span', 'body', c.text));
        row.addEventListener('click', (ev) => { ev.stopPropagation(); ctx.command('choose', { index: c.index }); });
      } else {
        row.appendChild(el('span', 'body', c.altText));
      }
      panel.appendChild(row);
    }
    panel.hidden = false;
    const total = panel.scrollHeight - lh * 0.5;
    const panelH = Math.max(H * 0.46, total + lh);
    panel.style.height = panelH + 'px';
    panel.style.top = Math.max(20, (H - panelH) / 2 - 80) + 'px';
  }

  function renderInventory(s) {
    const inv = $('vn-inventory');
    inv.textContent = '';
    if (!s.inventory.length) { inv.hidden = true; return; }
    inv.hidden = false;
    const w = theme.inventory;
    const fs = w.fontSize;
    const lh = Math.floor(fontHeight(fs, invFamily()) * w.lineHeight);
    // под инвентарём — сплошная чёрная полоса до низа экрана
    let y = w.y;
    const line = el('div', 'vn-inv-items');
    line.style.font = fontCss(fs, invFamily());
    line.style.lineHeight = lh + 'px';
    line.style.color = w.fg;
    line.style.setProperty('--vn-link', w.link);
    line.style.setProperty('--vn-alink', w.alink);
    line.style.width = w.w + 'px';
    line.style.marginLeft = w.x + 'px';
    line.style.textAlign = theme.inventoryHorizontal ? theme.inventoryAlign : 'left';
    s.inventory.forEach((item, i) => {
      if (i > 0 && theme.inventoryHorizontal) line.appendChild(document.createTextNode(' | '));
      const label = item.label.replace(/\n/g, ' ');
      const node = item.active ? ctx.link(label, () => ctx.command('item', { id: item.id }), 'vn-hit') : el('span', 'inactive', label);
      node.classList.add('vn-item');
      if (item.id === state.selected) node.classList.add('selected');
      const icon = ctx.imageUrl(item.icon);
      if (icon) {
        const img = el('img');
        img.src = icon;
        img.style.height = lh + 'px';
        img.style.width = lh + 'px';
        node.insertBefore(img, node.firstChild);
      }
      if (!theme.inventoryHorizontal) {
        const row = el('div');
        row.appendChild(node);
        line.appendChild(row);
      } else {
        line.appendChild(node);
      }
    });
    inv.appendChild(line);
    // не уходить за низ экрана: строки поднимаются вместе с полосой
    const need = line.scrollHeight;
    if (y + need > H) y = Math.max(0, H - need);
    inv.style.top = y + 'px';
    inv.style.height = (H - y) + 'px';
  }

  function renderMenuButton() {
    // кнопка, которую ставит игра NLB (nlb.lua): gfx/menubtn_ru.png для русской книги или
    // gfx/menubtn.png темы — в правый верхний угол
    const button = $('vn-menu-button');
    const path = state.language === 'ru' && theme.menuButtonRu ? theme.menuButtonRu : theme.menuButton;
    if (!path) {
      button.classList.add('text');
      button.textContent = ctx.ui().menu;
      return;
    }
    const url = themeUrl(path);
    if (button.dataset.url === url) return;
    button.dataset.url = url;
    button.classList.remove('text');
    button.textContent = '';
    const img = el('img');
    img.alt = ctx.ui().menu;
    img.draggable = false;
    img.onload = () => {
      img.style.width = (img.naturalWidth * theme.scale) + 'px';
      img.style.height = (img.naturalHeight * theme.scale) + 'px';
    };
    img.src = url;
    button.appendChild(img);
  }

  // ------------------------------------------------------------------ ввод
  $('vn-stage').addEventListener('click', (ev) => {
    if (!state || ev.target.closest('.vn-hit') || ev.target.closest('#vn-menu-button')) return;
    if (vn.showChoices) {
      // экран вариантов: щелчок мимо вариантов — назад к тексту
      vn.showChoices = false;
      render();
    } else if (choicesMode()) {
      advanceText();  // как paginator: щелчок мимо спрайтов — дальше по тексту
    } else if (vn.more) {
      vn.scroll += vn.pageH;
      render();
    }
  });
  $('vn-menu-button').addEventListener('click', (ev) => { ev.stopPropagation(); ctx.mainMenu(); });
  $('vn-stage').addEventListener('wheel', (ev) => {
    if (!state || vn.showChoices) return;
    const lh = Math.floor(fontHeight(theme.text.fontSize, textFamily()) * theme.text.lineHeight);
    vn.scroll = Math.max(0, vn.scroll + Math.sign(ev.deltaY) * 3 * lh);
    render();
  }, { passive: true });

  // Клавиши: пробел и Enter — дальше, Esc — назад от экрана вариантов. true — клавиша обработана
  function key(ev) {
    if (ev.key === ' ' || ev.key === 'Enter') {
      if (choicesMode() && !vn.showChoices) advanceText();
      else if (vn.more) { vn.scroll += vn.pageH; render(); }
      else if (vn.showChoices) { vn.showChoices = false; render(); }
      else if (advanceToChoices()) render();
      return true;
    }
    if (ev.key === 'Escape' && vn.showChoices) {
      vn.showChoices = false;
      render();
      return true;
    }
    if (ev.key === 'PageDown' && vn.more) { vn.scroll += vn.pageH; render(); return true; }
    if (ev.key === 'PageUp') { vn.scroll = Math.max(0, vn.scroll - vn.pageH); render(); return true; }
    return false;
  }

  // Пункт меню «Варианты: отдельным экраном / в тексте» (как в NLBNavigator)
  function menuItem() {
    return {
      label: choicesScreen ? ctx.ui().choicesScreen : ctx.ui().choicesInText,
      action: () => {
        choicesScreen = !choicesScreen;
        ctx.store.set('nlb.choicesScreen', choicesScreen);
        vn.showChoices = false;
        vn.scroll = 0;
        render();
      }
    };
  }

  return {
    loadTheme: loadTheme,
    update: update,
    render: render,
    key: key,
    menuItem: menuItem,
    voicesByPortion: () => choicesMode()
  };
};
