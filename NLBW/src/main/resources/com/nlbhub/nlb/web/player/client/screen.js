// Экран игры веб-плеера NLB — порт GameScreen NLBNavigator: экран 1920×1080, вписанный в окно
// с полями, в двух раскладках, как в INSTEAD.
// - Визуальная новелла (renderVisualNovel): картинка страницы во весь экран, графические объекты
//   на своих местах (vn.lua) с эффектами и подписями, полоса текста внизу (vn:geom экспорта) —
//   текст порциями, как paginator, затем экран вариантов; строка инвентаря.
// - Стандартная тема (renderStandard): фон темы, картинка страницы в своей области (scr.gfx),
//   окно текста (win.*) с заголовком-«осмотреться» и путями, листание стрелками, инвентарь (inv.*).
// Оформление — тема INSTEAD: базовая (стандартная тема INSTEAD) и поверх неё тема игры NLB
// (api/books/<книга>/theme): шрифты, цвета, окна, кнопка меню, звук щелчка.
window.NLBScreen = function (ctx) {
  'use strict';
  const W = 1920, H = 1080;
  const ANCHORS = ['LeftTop', 'MiddleTop', 'RightTop', 'LeftMiddle', 'MiddleMiddle', 'RightMiddle',
    'LeftBottom', 'MiddleBottom', 'RightBottom'];
  const FALLBACK = 'Georgia, "Times New Roman", serif';
  // Темы экспорта NLB по умолчанию (ScreenTheme::defaults), если тему не удалось получить
  const window_ = (x, y, w, h, fg, link, alink, size) => ({
    x: x, y: y, w: w, h: h, fg: fg, link: link, alink: alink, font: '', fontBold: '', fontItalic: '',
    fontSize: size, lineHeight: 1.3, upImage: '', downImage: ''
  });
  const common = {
    background: '#011322', scale: 1, align: 'justify', inventoryHorizontal: true, inventoryAlign: 'center',
    menuButton: '', menuButtonRu: '', themeMenuButton: '', menuButtonX: 0, menuButtonY: 0, backgroundImage: '',
    pageBackground: '', screenArea: { x: 0, y: 0, w: W, h: H }, pictureArea: { x: 40, y: 30, w: 1787, h: 640 },
    waysBottom: false, clickSound: '', menuFont: ''
  };
  const DEFAULT_THEMES = {
    vn: Object.assign({}, common, {
      text: window_(248, 838, 1424, 204, '#ffffff', '#00ff00', '#ffd700', 32),
      inventory: window_(0, 1050, 1920, 30, '#ffffff', '#00ff00', '#ffd700', 24)
    }),
    standard: Object.assign({}, common, {
      text: window_(68, 670, 1704, 301, '#ffffff', '#00ff00', '#ffd700', 24),
      inventory: window_(68, 982, 1704, 90, '#ffffff', '#00ff00', '#ffd700', 24)
    })
  };

  const $ = (id) => document.getElementById(id);
  const el = ctx.el;
  let themes = DEFAULT_THEMES;
  let defaultMode = 'vn';  // режим книги по умолчанию (по её теме) — для адреса без ?mode=
  let state = null;
  const scr = {
    portion: 0,            // текущая порция текста (VN)
    voiced: -1,            // порция, на которой уже запущена фраза озвучки
    voicePtr: 0, voicesKey: '',
    showChoices: false,    // экран вариантов (VN)
    scroll: 0,             // листание строк окна текста
    pageH: 0, more: false, morePortions: false,
    lastPage: null, lastReaction: '',
    reactionThenRoom: true,
    shown: new Map(),      // id -> был виден (эффекты появления и исчезновения)
    rects: new Map(),      // id -> прямоугольник на последнем кадре
    nodes: new Map()       // id -> элемент объекта
  };
  let choicesScreen = ctx.store.get('nlb.choicesScreen', true);
  // Размер текста (GameScreen: textScale, пункт меню «Размер текста»): множитель всех шрифтов
  // экрана — текста, вариантов, инвентаря, подписей, сообщений, меню. Выбор игрока запоминается;
  // без него на маленьком экране (телефон: сцена 1920×1080 ужата больше чем вдвое) — 150 %
  const TEXT_SCALES = [1, 1.25, 1.5, 1.75, 2];
  const smallScreen = () => Math.min(window.innerWidth / 1920, window.innerHeight / 1080) < 0.5;
  let textScale = Number(ctx.store.get('nlb.textScale', smallScreen() ? 1.5 : 1));
  if (!(textScale >= 0.5 && textScale <= 3)) textScale = 1;
  const fsize = (w) => w.fontSize * textScale;
  let renderToken = 0;

  const isVN = () => state && state.screen.layout === 'VisualNovel';
  const theme = () => isVN() ? themes.vn : themes.standard;

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
  // Файл шрифта темы -> семейство @font-face (один и тот же файл — одно семейство)
  const fontFaces = new Map();
  const family = (path) => path && fontFaces.has(path) ? '"' + fontFaces.get(path) + '", ' + FALLBACK : FALLBACK;
  const textFamily = () => family(theme().text.font);
  // Подписи графических объектов — шрифтом hudFont темы, цвет hud_color = black (draw_hud vn.lua);
  // без него — шрифт и цвет окна текста
  const labelFamily = () => theme().hudFont ? family(theme().hudFont) : textFamily();
  const labelColor = () => theme().hudFont ? 'black' : theme().text.fg;
  const themeUrl = (path) => 'theme/' + encodeURIComponent(ctx.bookId()) + '/' + path.split('/').map(encodeURIComponent).join('/');

  async function loadTheme() {
    try {
      const data = await ctx.api('books/' + encodeURIComponent(ctx.bookId()) + '/theme');
      themes = { vn: data.vn || DEFAULT_THEMES.vn, standard: data.standard || DEFAULT_THEMES.standard };
      defaultMode = data.mode === 'standard' ? 'standard' : 'vn';
    } catch (e) {
      themes = DEFAULT_THEMES;
    }
    const loads = [];
    for (const t of [themes.vn, themes.standard]) {
      for (const w of [t.text, t.inventory, { font: t.menuFont }, { font: t.hudFont }]) {
        for (const path of [w.font, w.fontBold, w.fontItalic]) {
          if (!path || fontFaces.has(path)) continue;
          const name = 'nlb-font-' + fontFaces.size;
          fontFaces.set(path, name);
          try {
            const face = new FontFace(name, 'url("' + themeUrl(path) + '")');
            document.fonts.add(face);
            loads.push(face.load().catch(() => null));
          } catch (e) { /* шрифт не загрузился — шрифт браузера */ }
        }
      }
    }
    await Promise.all(loads);
  }

  // Звук щелчка по ссылке, предмету, пункту меню — snd.click темы, как в INSTEAD
  function click() {
    const sound = theme().clickSound;
    if (sound) ctx.playSoundUrl(themeUrl(sound));
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
    const stage = $('stage');
    stage.style.transform = 'translate(' + ((window.innerWidth - W * k) / 2) + 'px,' + ((window.innerHeight - H * k) / 2) + 'px) scale(' + k + ')';
    document.documentElement.style.setProperty('--stage-scale', String(k));
  }
  window.addEventListener('resize', fit);
  const place = (node, r) => {
    node.style.left = r.x + 'px';
    node.style.top = r.y + 'px';
    node.style.width = r.w + 'px';
    node.style.height = r.h + 'px';
  };
  function setColors(node, w) {
    node.style.color = w.fg;
    node.style.setProperty('--link', w.link);
    node.style.setProperty('--alink', w.alink);
  }

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

  // Картинки объектов в тексте STANDARD (img() в {..} экспорта STEAD): в своём размере с
  // масштабом темы, посередине строки, как в INSTEAD; размеры известны до раскладки (render)
  const textImageSizes = new Map();
  function textImageUrls(s) {
    const urls = [];
    for (const list of [s.roomText, s.reactionText, s.objectsText, s.modText]) {
      for (const p of list || []) for (const r of p.runs) if (r.image) urls.push(sizeUrl(r.image));
    }
    return urls.filter((u) => u);
  }
  function textImage(image) {
    const img = el('img', 'text-image');
    img.alt = '';
    img.draggable = false;
    img.src = ctx.imageUrl(image);
    const size = textImageSizes.get(sizeUrl(image));
    if (size) {
      img.style.width = (size.w * theme().scale) + 'px';
      img.style.height = (size.h * theme().scale) + 'px';
    }
    ctx.animate(img, image);
    return img;
  }

  function runNode(run) {
    const text = typo(run.text);
    if (run.kind === 'ObjectLink') {
      const a = ctx.link(text, () => ctx.command('object', { id: run.target }), 'hit');
      if (run.image) a.insertBefore(textImage(run.image), a.firstChild);
      return a;
    }
    if (run.kind === 'ChoiceLink') {
      return run.target === '-1' ? ctx.link(text, () => ctx.command('look'), 'hit')
        : ctx.link(text, () => ctx.command('choose', { index: Number(run.target) }), 'hit');
    }
    return document.createTextNode(text);
  }
  function paragraphNode(p, cls) {
    const node = el('p', cls || p.cls || '');
    for (const run of p.runs) node.appendChild(runNode(run));
    return node;
  }
  const isBlank = (p) => !p.runs.length || p.runs.every((r) => !r.text.trim() && !r.image);
  // Перестраивать часть экрана, только если она изменилась: на страницах с таймером снимок
  // приходит несколько раз в секунду, и перестройка между нажатием и отпусканием кнопки мыши
  // съедала бы щелчок по ссылке. Подпись — содержимое и всё, от чего зависит его вид
  function unchanged(node, ...parts) {
    const sig = JSON.stringify(parts, (k, v) => ((k === 'picture' || k === 'image') && v ? ctx.imageKey(v) : v));
    if (node.dataset.sig === sig) return true;
    node.dataset.sig = sig;
    return false;
  }

  // Варианты отдельным экраном — только на страницах VN (там его вёл paginator INSTEAD)
  const choicesMode = () => choicesScreen && isVN();
  const enabledChoices = () => state.screen.choices.filter((c) => c.enabled);
  function singleTrivialChoice() {
    const enabled = enabledChoices();
    return enabled.length === 1 && enabled[0].trivial ? enabled[0].index : -1;
  }
  const anyText = (s) => s.pageText.some((p) => p.runs.length) || s.reactionText.some((p) => p.runs.length);

  // ------------------------------------------------------------------ ход
  function nextVoice() {
    const voices = state.voices || [];
    if (scr.voicePtr < voices.length) ctx.playVoice(voices[scr.voicePtr++]);
  }

  // Новый снимок: command — команда, после которой он пришёл ('advance' — ход времени)
  function update(newState, command) {
    const before = state;
    state = newState;
    const s = state.screen;
    const reaction = JSON.stringify(s.reactionText);
    const voicesKey = JSON.stringify(state.voices || []);
    const fresh = command === 'restart' || command === 'tryAgain' || command === 'load';
    if (!before || fresh || s.pageId !== scr.lastPage) {
      // новая страница (или та же, но игра начата заново или загружена) — текст с начала,
      // озвучка заново; ответ (если есть) — перед описанием новой комнаты
      scr.portion = 0;
      scr.voiced = -1;
      scr.scroll = 0;
      scr.showChoices = false;
      scr.voicePtr = 0;
      scr.reactionThenRoom = true;
    } else if (s.reactionText.length && reaction !== scr.lastReaction) {
      // новое действие на той же странице — показать ответ (VN: с его первой порции)
      scr.portion = state.pagePortions || 0;
      scr.scroll = 0;
      scr.reactionThenRoom = false;
    }
    if (voicesKey !== scr.voicesKey) scr.voicePtr = 0;
    scr.voicesKey = voicesKey;
    scr.lastPage = s.pageId;
    scr.lastReaction = reaction;
    if (command !== 'advance') afterCommand();
  }

  function afterCommand() {
    const s = state.screen;
    scr.showChoices = false;
    // Страница без текста: показывать нечего — сразу экран вариантов (вариант один тривиальный —
    // ждём «дальше», чтобы игрок увидел сцену)
    if (choicesMode() && !anyText(s) && singleTrivialChoice() < 0) advanceToChoices();
    // Конец игры на странице без текста — сразу сообщение (только в режиме экрана вариантов: в
    // режиме ссылок в тексте его нет, как в INSTEAD, — начать заново можно из меню)
    if (s.finished && choicesMode() && !anyText(s) && !s.externalGame) gameOver();
  }

  function advanceToChoices() {
    if (!choicesMode() || scr.more || scr.showChoices) return false;
    if (!enabledChoices().length) return false;
    scr.showChoices = true;
    return true;
  }

  function gameOver() {
    ctx.dialog(ctx.ui().theEnd, [{ label: ctx.ui().tryAgain, action: () => ctx.command('tryAgain') }]);
  }

  function pageDown() {
    scr.scroll += scr.pageH;
    render();
  }

  // «Дальше» (пробел, щелчок мимо ссылок): строки порции, следующая порция, а после последней —
  // экран вариантов или переход по единственной тривиальной ссылке
  function advanceText() {
    if (scr.showChoices) return;
    if (scr.more) { pageDown(); return; }
    if (scr.morePortions) { scr.portion++; scr.scroll = 0; render(); return; }
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
    const pageUrl = sizeUrl(s.pageImage);
    const textUrls = textImageUrls(s);
    const [loaded, pageSize, textLoaded] = await Promise.all([Promise.all(urls.map(imageSize)), imageSize(pageUrl),
      Promise.all(textUrls.map(imageSize))]);
    if (token !== renderToken) return;  // пришёл новый снимок
    const size = new Map(urls.map((u, i) => [u, loaded[i]]));
    textUrls.forEach((u, i) => textImageSizes.set(u, textLoaded[i]));
    const vn = isVN();
    const t = theme();
    fit();
    const stage = $('stage');
    stage.classList.toggle('vn', vn);
    stage.classList.toggle('standard', !vn);
    // вне экрана темы (scr.w×scr.h, вписан в 1920×1080) — цвет рамки scr.col.brd, на нём — scr.col.bg
    stage.style.background = t.border || '#000';
    place($('stage-screen'), t.screenArea);
    // картинка-фон страницы STANDARD заменяет фон темы (theme.gfx.bg в экспорте): вокруг неё — цвет рамки
    const pageBg = !vn && s.imageIsBackground && !!ctx.imageUrl(s.pageImage);
    $('stage-screen').style.background = pageBg ? (t.border || '#000') : t.background;
    // меню — шрифтом меню темы (menu.fnt.name), как в INSTEAD
    document.documentElement.style.setProperty('--menu-font', family(t.menuFont));
    renderThemeBackground(vn || pageBg, t);
    renderPicture(s, vn, t, pageSize);
    renderObjects(s, size, vn);
    if (vn) {
      renderVisualNovelText(s, t);
    } else {
      $('stage-band').hidden = true;
      $('stage-choices').hidden = true;
      renderStandardText(s, t, pageSize);
    }
    renderInventory(s, vn, t);
    renderMenuButton(t);
    const hint = $('stage-hint');
    hint.hidden = !state.selected;
    hint.textContent = ctx.ui().itemSelected;
    // текст без порций (STANDARD, VN с вариантами в тексте): озвучка — первая фраза при входе на
    // страницу (её запускает player.js); с порциями — фраза на каждую новую порцию
  }

  function renderThemeBackground(none, t) {
    // STANDARD: экспорт ставит странице без своего фона bgimg = nlb:std_bg() (gfx/bg.jpg темы);
    // если его нет — scr.gfx.bg из theme.ini. Фон — на экран темы (scr.w×scr.h). none — фона темы
    // нет: VN или своя картинка-фон страницы
    const bg = $('stage-theme-bg');
    const path = none ? '' : (t.pageBackground || t.backgroundImage);
    bg.hidden = !path;
    if (!path) return;
    place(bg, t.screenArea);
    const url = themeUrl(path);
    if (bg.dataset.url !== url) {
      bg.dataset.url = url;
      bg.src = url;
    }
  }

  // Размер картинки страницы — по первому её кадру (у анимации адрес кадра меняется каждый тик)
  const firstFrameUrl = new Map();
  function sizeUrl(image) {
    const url = ctx.imageUrl(image);
    if (!url) return null;
    const key = ctx.imageKey(image);
    if (!firstFrameUrl.has(key)) firstFrameUrl.set(key, url);
    return firstFrameUrl.get(key);
  }
  // Картинка в окне текста (scr.gfx.mode fixed/embedded): размер с масштабом темы, вписанный в
  // ширину окна и наибольшую высоту scr.gfx.h (game_pict_scale INSTEAD)
  function fitPicture(size, t) {
    if (!size) return null;
    let w = size.w * t.scale, h = size.h * t.scale;
    const maxH = t.gfxMaxHeight == null ? Infinity : t.gfxMaxHeight;
    const k = Math.min(1, t.text.w / w, maxH / h);
    return { w: Math.floor(w * k), h: Math.floor(h * k) };
  }
  const inWindowPicture = (s, vn, t) => !vn && !s.imageIsBackground && (t.gfxMode === 'fixed' || t.gfxMode === 'embedded');

  function renderPicture(s, vn, t, size) {
    // VN — во весь экран; STANDARD: картинка-фон — как фон INSTEAD (в своём размере с масштабом
    // темы, по центру экрана темы, вокруг — цвет рамки); scr.gfx.mode float — в области scr.gfx,
    // вписанной по центру; fixed — в окне текста под заголовком (место задаёт renderStandardText);
    // embedded — в тексте сцены (тоже там), а этот элемент не нужен
    const pic = $('stage-picture');
    const url = ctx.imageUrl(s.pageImage);
    const embedded = !vn && !s.imageIsBackground && t.gfxMode === 'embedded';
    if (!url || embedded) { pic.hidden = true; pic.removeAttribute('src'); pic.dataset.key = ''; return; }
    pic.hidden = false;
    pic.style.objectFit = 'fill';
    if (vn) {
      place(pic, { x: 0, y: 0, w: W, h: H });
    } else if (s.imageIsBackground) {
      const a = t.screenArea;
      const w = size ? size.w * t.scale : a.w, h = size ? size.h * t.scale : a.h;
      place(pic, { x: a.x + (a.w - w) / 2, y: a.y + (a.h - h) / 2, w: w, h: h });
    } else if (t.gfxMode === 'fixed') {
      const r = fitPicture(size, t);
      if (r) { pic.style.width = r.w + 'px'; pic.style.height = r.h + 'px'; }
    } else {
      place(pic, t.pictureArea);
      pic.style.objectFit = 'contain';
    }
    // ключ — без номера текущего кадра: на каждом тике таймера он новый, а анимацию ведёт аниматор
    const key = ctx.imageKey(s.pageImage);
    if (pic.dataset.key !== key) {
      pic.dataset.key = key;
      pic.src = url;
      ctx.animate(pic, s.pageImage);
    }
  }

  // Подпись, которую стоит рисовать: не внутреннее имя объекта (abilitiesTrigger)
  const isHumanLabel = (s) => !!s && /[ \u0080-\uffff]/.test(s);
  // Строки подписи (txtfn gobj.lua) — шрифтом подписей vn.lua: hudFont (STEINEMU 29), а строка
  // «== текст ==» — hudFont_H1 (45); пустая строка — отступ hud_indent_spacing (25)
  function labelLines(label) {
    return label.replace(/\r/g, '').split('\n').map((line) => {
      const h1 = /^== (.*) ==$/.exec(line);
      const size = (h1 ? 45 : 29) * textScale;
      return { text: h1 ? h1[1] : line, size: size, height: line ? fontHeight(size, labelFamily()) : 25 };
    });
  }
  function labelNodes(node, lines, x, y) {
    for (const line of lines) {
      if (line.text) {
        const t = el('div', 'stage-label', line.text);
        t.style.left = x + 'px';
        t.style.top = y + 'px';
        t.style.font = fontCss(line.size, labelFamily());
        t.style.lineHeight = line.height + 'px';
        t.style.color = labelColor();
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
        if (line.text) labelW = Math.max(labelW, textW(line.size, labelFamily(), line.text) + 9);
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
    let node = scr.nodes.get(o.id);
    if (!node) {
      node = el('div', 'stage-obj');
      node.dataset.id = o.id;
      scr.nodes.set(o.id, node);
    }
    return node;
  }

  function fillObject(node, o, r, spriteLabels) {
    const key = JSON.stringify([ctx.imageKey(o.image), ctx.imageKey(o.hoverImage), o.label, o.active, r.textOnly, spriteLabels]);
    place(node, r);
    if (node.dataset.key === key) return;
    node.dataset.key = key;
    node.textContent = '';
    node.classList.toggle('hit', !!o.active);
    node.onclick = o.active ? (ev) => { ev.stopPropagation(); ctx.command('object', { id: o.id }); } : null;
    if (r.textOnly) {
      // объект без картинки — только подпись, строками (draw_hud в vn.lua, в обеих раскладках)
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
    if (spriteLabels && isHumanLabel(o.label)) {
      // подпись спрайта (VN) — справа от картинки (+4 +5 px), по вертикали — по углу привязки
      const lines = labelLines(o.label);
      const total = lines.reduce((sum, line) => sum + line.height, 0);
      const row = Math.floor(ANCHORS.indexOf(o.placement.anchor) / 3);
      const coef = o.parentId ? 0.5 : row === 0 ? 0 : row === 1 ? 0.5 : 1;
      labelNodes(node, lines, r.w + 9, r.h * coef - total * coef);
    }
  }

  const before = new Map();  // объекты прошлого снимка (для эффекта исчезновения)
  function renderObjects(s, size, vn) {
    const layer = $('stage-objects');
    const rects = new Map();
    const present = new Set();
    const shown = new Map();
    const samePage = scr.renderedPage === s.pageId;
    const appear = [], hide = [];
    s.sceneObjects.forEach((o, index) => {
      if (!o.graphical) return;
      present.add(o.id);
      const r = objectRect(s, index, rects, size);
      rects.set(o.id, r);
      const visible = o.visible && !o.collapsed;
      shown.set(o.id, visible);
      const was = !!scr.shown.get(o.id);
      const node = objectNode(o);
      fillObject(node, o, r, vn);
      node.style.zIndex = String(index);
      if (node.parentNode !== layer) layer.appendChild(node);
      const e = o.appearance.effect;
      if (visible && !was && (e === 'FadeIn' || e === 'MoveIn' || e === 'ZoomIn')) appear.push([o, r]);
      if (!visible && was && e !== 'None' && e !== 'Overlap') hide.push([o, r, false]);
      if (!node.dataset.hiding) node.hidden = !visible;
    });
    // Объект исчез со страницы (взят, убран): эффект в обратную сторону на прежнем месте.
    // При смене страницы объекты уходят вместе со сценой — без эффекта
    for (const [id, node] of scr.nodes) {
      if (present.has(id)) continue;
      const old = before.get(id);
      const e = old ? old.appearance.effect : 'None';
      if (samePage && old && scr.shown.get(id) && e !== 'None' && e !== 'Overlap' && scr.rects.get(id)) {
        hide.push([old, scr.rects.get(id), true]);
      } else {
        node.remove();
        scr.nodes.delete(id);
      }
    }
    for (const [o, r] of appear) {
      for (const id of withChildren(s, o.id)) {
        const node = scr.nodes.get(id);
        if (node) playEffect(node, o, r, false, null);
      }
    }
    for (const [o, r, gone] of hide) {
      const node = scr.nodes.get(o.id);
      if (!node || node.dataset.hiding) continue;
      node.dataset.hiding = '1';
      node.hidden = false;
      playEffect(node, o, r, true, () => {
        delete node.dataset.hiding;
        if (gone) { node.remove(); scr.nodes.delete(o.id); } else { node.hidden = true; node.getAnimations().forEach((a) => a.cancel()); }
      });
    }
    // на экране вариантов объектов нет — как в INSTEAD, где на время выбора остаются фон и кнопка меню
    layer.hidden = scr.showChoices;
    scr.shown = shown;
    scr.rects = rects;
    scr.renderedPage = s.pageId;
    before.clear();
    for (const o of s.sceneObjects) before.set(o.id, o);
  }

  // Окно текста: абзацы в прокручиваемой области view (высота — целое число строк), стрелки
  // листания темы (win.gfx.up/down; без них — символы)
  function fillTextWindow(paragraphs, w, rect, lh, arrows) {
    const view = $('stage-text'), inner = $('stage-text-inner');
    view.hidden = false;
    const same = unchanged(inner, paragraphs, w, rect, lh, textScale, theme().align);
    if (!same) inner.textContent = '';
    const pageH = Math.max(lh, Math.floor(rect.h / lh) * lh);
    place(view, { x: rect.x, y: rect.y, w: rect.w, h: pageH });
    setColors(view, w);
    view.style.font = fontCss(fsize(w), family(w.font));
    view.style.lineHeight = lh + 'px';
    view.style.setProperty('--font-bold', family(w.fontBold || w.font));
    view.style.setProperty('--font-italic', family(w.fontItalic || w.font));
    view.style.setProperty('--em-style', w.fontItalic ? 'normal' : 'italic');
    const align = theme().align;
    view.style.textAlign = align === 'justify' || align === 'center' || align === 'right' ? align : 'left';
    if (!same) {
      for (const p of paragraphs) {
        if (p.picture) {
          // картинка в тексте сцены (scr.gfx.mode = embedded): по центру, прокручивается с текстом
          const box = el('div', 'stage-embedded');
          box.appendChild(embeddedPicture(p.picture, p.size));
          inner.appendChild(box);
        } else if (isBlank(p) && !p.cls) {
          // пустая строка: в STANDARD — целая строка (txt_layout INSTEAD), в VN — полстроки
          // (там пустая строка лишь разделяет порции), как GameScreen
          const gap = el('div', 'stage-gap');
          gap.style.height = Math.floor(isVN() ? lh / 2 : lh) + 'px';
          inner.appendChild(gap);
        } else {
          inner.appendChild(paragraphNode(p));
        }
      }
    }
    // Не уместившиеся строки листаются страницами окна: строка, которая видна не целиком, не
    // показывается, и следующая страница начинается с неё (GameScreen: m_firstHiddenY); последняя
    // страница не возвращается назад — прочитанное не повторяется
    const lines = textLines(inner, lh);
    const last = lines.length ? lines[lines.length - 1].top : 0;
    scr.scroll = Math.max(0, Math.min(scr.scroll, last));
    let shown = 0, firstHidden = -1;
    for (const line of lines) {
      if (line.top < scr.scroll - 0.5) continue;
      if (line.bottom <= scr.scroll + pageH + 0.5 || (!shown && firstHidden < 0)) {
        // строка выше окна (большая картинка) — показывается обрезанной, иначе листание встало бы
        shown = Math.min(pageH, line.bottom - scr.scroll);
      } else if (firstHidden < 0) firstHidden = line.top;
    }
    inner.style.transform = 'translateY(' + (-scr.scroll) + 'px)';
    view.style.height = Math.max(lh, shown) + 'px';
    scr.more = firstHidden >= 0;
    scr.pageH = scr.more ? Math.max(lh, firstHidden - scr.scroll) : pageH;
    const up = $('stage-up'), down = $('stage-down');
    up.hidden = !arrows || scr.scroll <= 0;
    down.hidden = !arrows || !scr.more;
    if (arrows) {
      // места стрелок — win.up.x/y, win.down.x/y темы (левый верхний угол картинки), иначе справа
      // от окна: вверху и внизу
      const has = (v) => v !== null && v !== undefined;
      arrow(up, w.upImage, '\u25b2', has(w.upX) ? w.upX : rect.x + rect.w + 6, has(w.upY) ? w.upY : rect.y, false, lh, w);
      arrow(down, w.downImage, '\u25bc', has(w.downX) ? w.downX : rect.x + rect.w + 6,
        has(w.downY) ? w.downY : rect.y + pageH, !has(w.downY), lh, w);
    }
  }
  // Строки текста окна (в координатах содержимого, без прокрутки): верх и низ. Строка текста —
  // высотой lh; строка с картинкой объекта — по картинке (текст рядом с ней — та же строка)
  function textLines(inner, lh) {
    const k = inner.getBoundingClientRect().height / Math.max(1, inner.offsetHeight) || 1;
    const base = inner.getBoundingClientRect().top;
    const boxes = [];
    const range = document.createRange();
    for (const child of inner.children) {
      if (!child.textContent.trim() && !child.querySelector('img')) continue;
      range.selectNodeContents(child);
      // картинки — отдельно: внутри ссылки (<a><img>) range отдаёт только строчный прямоугольник
      // самой ссылки, без картинки, и строка с ней считалась бы высотой в строку текста
      const rects = Array.from(range.getClientRects());
      for (const img of child.querySelectorAll('img')) rects.push(img.getBoundingClientRect());
      for (const r of rects) {
        if (r.height <= 0) continue;
        boxes.push({ top: Math.round((r.top - base) / k), bottom: Math.round((r.bottom - base) / k) });
      }
    }
    // прямоугольники одной строки (ссылки, картинка и текст рядом) пересекаются — одна строка
    boxes.sort((a, b) => a.top - b.top);
    const out = [];
    for (const b of boxes) {
      const cur = out[out.length - 1];
      if (cur && b.top < cur.bottom - 2) cur.bottom = Math.max(cur.bottom, b.bottom);
      else out.push({ top: b.top, bottom: b.bottom });
    }
    for (const line of out) line.bottom = Math.max(line.bottom, line.top + lh);
    return out;
  }

  function arrow(node, path, symbol, x, y, fromBottom, lh, w) {
    const url = path ? themeUrl(path) : '';
    if (node.dataset.url !== url) {
      node.dataset.url = url;
      node.textContent = '';
      if (url) {
        const img = el('img');
        img.draggable = false;
        img.onload = () => {
          // картинки темы — в масштабе её экрана (scr.w×scr.h -> 1920×1080)
          img.style.width = (img.naturalWidth * theme().scale) + 'px';
          img.style.height = (img.naturalHeight * theme().scale) + 'px';
          if (fromBottom) node.style.top = (y - img.naturalHeight * theme().scale) + 'px';
        };
        img.src = url;
        node.appendChild(img);
      } else {
        node.textContent = symbol;
      }
    }
    node.style.left = x + 'px';
    const img = node.querySelector('img');
    const h = img && img.naturalHeight ? img.naturalHeight * theme().scale : lh * 0.8;
    node.style.top = (fromBottom ? y - h : y) + 'px';
    node.style.color = w.link;
    node.style.fontSize = (lh * 0.8) + 'px';
  }

  // VN: полоса текста vn:geom(8, 864, 1904, 184) экспорта — полупрозрачная, во всю ширину, текст
  // внутри с полями по 240 px (vn.lua: textpad = 8); текст порциями или (варианты в тексте) весь
  function renderVisualNovelText(s, t) {
    const band = $('stage-band'), choices = $('stage-choices');
    $('stage-header').hidden = true;
    choices.hidden = !scr.showChoices;
    if (scr.showChoices) {
      band.hidden = true;
      $('stage-text').hidden = true;
      $('stage-up').hidden = $('stage-down').hidden = true;
      renderChoicesScreen(s, t);
      return;
    }
    const paragraphs = [];
    if (choicesMode()) {
      const parts = state.portions;
      if (scr.portion >= parts.length) scr.portion = Math.max(0, parts.length - 1);
      if (parts.length) paragraphs.push(...parts[scr.portion]);
      scr.morePortions = scr.portion + 1 < parts.length;
      // озвучка: каждая новая порция — следующая фраза (paginator text_page -> nextsnd)
      if (parts.length && scr.portion !== scr.voiced) {
        scr.voiced = scr.portion;
        nextVoice();
      }
    } else {
      scr.morePortions = false;
      paragraphs.push(...s.pageText, ...s.reactionText);
      // варианты в тексте — нумерованным списком под ним
      let n = 0;
      for (const c of s.choices) {
        if (c.enabled) paragraphs.push({ cls: 'choice-line', runs: [{ kind: 'ChoiceLink', text: (++n) + '. ' + c.text, target: String(c.index) }] });
        else if (c.altText) paragraphs.push({ cls: 'choice-line', runs: [{ kind: 'Plain', text: c.altText }] });
      }
    }
    const hasText = anyText(s) || (!choicesMode() && s.choices.length > 0);
    band.hidden = !hasText;
    if (!hasText) {
      scr.more = false;
      $('stage-text').hidden = true;
      $('stage-up').hidden = $('stage-down').hidden = true;
      return;
    }
    // крупный текст: полоса выше в той же пропорции (строк в ней столько же) и с меньшими полями;
    // низ — на месте, но не ниже строки инвентаря
    let bottom = 864 + 184;
    if (textScale > 1 && s.inventory.length) {
      const inv = t.inventory;
      const invLine = fontHeight(fsize(inv), family(inv.font)) * inv.lineHeight;
      bottom = Math.min(bottom, Math.min(inv.y, H - invLine) - 2);
    }
    const bandH = Math.min(184 * textScale, H * 0.5);
    const bandRect = { x: 8, y: bottom - bandH, w: 1904, h: bandH };
    const margin = 240 / Math.max(1, textScale);
    band.style.top = bandRect.y + 'px';
    band.style.height = (bandRect.h + 16) + 'px';
    const w = t.text;
    const lh = Math.floor(fontHeight(fsize(w), family(w.font)) * w.lineHeight);
    fillTextWindow(paragraphs, w, { x: bandRect.x + margin, y: bandRect.y + 8, w: bandRect.w - 2 * margin, h: bandRect.h - 16 }, lh, false);
  }

  // STANDARD — как INSTEAD (game.c, stead2): заголовок по центру цветом ссылки (щелчок —
  // «осмотреться»), под ним пути через « | » (или под текстом: win.ways.mode = bottom), затем сцена:
  // после действия — курсивом ответ; на только что открытой странице — текст модификаций (вывод
  // enter: stead.go выводит его перед сценой), описание комнаты; описания объектов, alt-тексты
  // недоступных ссылок, ссылки в тексте (xdsc). После действия на той же странице вывода enter
  // уже нет — только ответ и сцена (с game.forcedsc — описание комнаты, ответ и сцена)
  function composeStandard(s, waysBottom) {
    const header = [];
    if (s.roomTitle) header.push({ cls: 'title', runs: [{ kind: 'ChoiceLink', text: s.roomTitle, target: '-1' }] });
    const ways = [];
    for (const c of s.choices) {
      if (!c.enabled || c.inlineLink) continue;
      if (ways.length) ways.push({ kind: 'Plain', text: ' | ' });
      ways.push({ kind: 'ChoiceLink', text: c.text, target: String(c.index) });
    }
    if (ways.length && !waysBottom) header.push({ cls: 'ways', runs: ways });
    const scene = [];
    if (s.reactionText.length) {
      // game.forcedsc: после действия на той же странице STEAD2 (iface.fmt) выводит сначала
      // me():look() — описание комнаты без вывода enter, — затем ответ
      if (!scr.reactionThenRoom && s.forceDsc) scene.push(...s.roomText);
      // пустой абзац — пустая строка («^^» внутри ответа, «^» в конце перед следующей частью);
      // следующий ответ продолжает последнюю строку предыдущего (после «^» в конце — новую)
      for (const p of s.reactionText) {
        const last = scene[scene.length - 1];
        if (p.joinPrevious && last && last.cls === 'em') {
          last.runs = last.runs.concat([{ kind: 'Plain', text: ' ' }], p.runs);
        } else if (p.joinPrevious && last && last.reactionGap) {
          scene[scene.length - 1] = { cls: 'em', runs: p.runs.slice() };
        } else if (!p.runs.length) {
          scene.push({ runs: [], reactionGap: true });
        } else {
          scene.push({ cls: 'em', runs: p.runs.slice() });
        }
      }
      if (scr.reactionThenRoom) scene.push(...s.modText, ...s.roomText);
    } else {
      scene.push(...s.modText, ...s.roomText);
    }
    scene.push(...s.objectsText);
    for (const c of s.choices) {
      if (!c.enabled && c.altText) scene.push({ runs: [{ kind: 'Plain', text: c.altText }] });
    }
    for (const c of s.choices) {
      if (c.enabled && c.inlineLink) scene.push({ runs: [{ kind: 'ChoiceLink', text: c.text, target: String(c.index) }] });
    }
    // переводы строк в конце всей сцены не видны (INSTEAD)
    while (scene.length && !scene[scene.length - 1].picture && !scene[scene.length - 1].runs.length) scene.pop();
    if (ways.length && waysBottom) scene.push({ cls: 'ways', runs: ways });
    return { header: header, scene: scene };
  }

  // Элемент картинки в тексте сцены — один на всё время: перестройка текста на каждом тике
  // таймера не должна перезапускать её анимацию
  const embeddedImg = el('img');
  embeddedImg.alt = '';
  embeddedImg.draggable = false;
  function embeddedPicture(image, size) {
    const key = ctx.imageKey(image);
    if (embeddedImg.dataset.key !== key) {
      embeddedImg.dataset.key = key;
      embeddedImg.src = ctx.imageUrl(image);
      ctx.animate(embeddedImg, image);
    }
    embeddedImg.style.width = size ? size.w + 'px' : '';
    embeddedImg.style.height = size ? size.h + 'px' : '';
    return embeddedImg;
  }

  function renderStandardText(s, t, pageSize) {
    const w = t.text;
    const lh = Math.floor(fontHeight(fsize(w), family(w.font)) * w.lineHeight);
    const { header, scene } = composeStandard(s, t.waysBottom);
    // картинка в окне текста (scr.gfx.mode fixed/embedded, game.c INSTEAD): fixed — под заголовком,
    // не прокручивается, ниже — пути (а без них — отступ win_spacing), затем текст; embedded — в
    // начале текста сцены, за ней пути и сцена, всё прокручивается
    const picture = inWindowPicture(s, false, t) && ctx.imageUrl(s.pageImage) ? fitPicture(pageSize, t) : null;
    const spacing = Math.floor(fsize(w) * w.lineHeight / 2);  // game.c: win_spacing
    if (picture && t.gfxMode === 'embedded') {
      const ways = header.findIndex((p) => p.cls === 'ways');
      const moved = ways >= 0 ? header.splice(ways, 1) : [];
      scene.unshift({ picture: s.pageImage, size: picture, runs: [] }, ...moved);
    }
    const fixed = picture && t.gfxMode === 'fixed';
    // заголовок и пути (INSTEAD: el_title, el_ways) — над сценой, не прокручиваются
    const head = $('stage-header');
    head.hidden = !header.length && !fixed;
    let headerH = 0;
    if (header.length || fixed) {
      if (!unchanged(head, header, fixed ? picture : null, w, lh, textScale)) {
        head.textContent = '';
        head.style.left = w.x + 'px';
        head.style.top = w.y + 'px';
        head.style.width = w.w + 'px';
        setColors(head, w);
        head.style.font = fontCss(fsize(w), family(w.font));
        head.style.lineHeight = lh + 'px';
        head.style.setProperty('--font-bold', family(w.fontBold || w.font));
        let spacer = null;
        header.forEach((p) => {
          const node = paragraphNode(p);
          // game.c: win_spacing = font_size * font_height / 2 — после заголовка
          if (p.cls === 'title') node.style.marginBottom = spacing + 'px';
          if (fixed && p.cls === 'ways' && !spacer) {
            spacer = el('div', 'picture-spacer');
            spacer.style.height = picture.h + 'px';
            head.appendChild(spacer);
          }
          head.appendChild(node);
        });
        if (fixed && !spacer) {
          // путей над текстом нет — после картинки отступ
          spacer = el('div', 'picture-spacer');
          spacer.style.height = (picture.h + spacing) + 'px';
          head.appendChild(spacer);
        }
      }
      if (fixed) {
        const pic = $('stage-picture');
        pic.style.left = (w.x + (w.w - picture.w) / 2) + 'px';
        pic.style.top = (w.y + head.querySelector('.picture-spacer').offsetTop) + 'px';
      }
      headerH = head.offsetHeight;
    } else {
      head.dataset.sig = '';
    }
    fillTextWindow(scene, w, { x: w.x, y: w.y + headerH, w: w.w, h: Math.max(lh, w.h - headerH) }, lh, true);
  }

  function renderChoicesScreen(s, t) {
    // Варианты посередине экрана на полупрозрачной панели (GameScreen::renderChoicesScreen)
    const panel = $('stage-choices');
    panel.hidden = false;
    if (unchanged(panel, s.choices, t.text, textScale)) return;
    panel.textContent = '';
    const w = t.text;
    const fs = fsize(w) * 1.25;
    const lh = fontHeight(fs, family(w.font));
    const panelW = W * Math.min(0.95, 0.675 * Math.max(1, textScale * 0.8));  // крупный текст — подложка шире
    panel.style.left = ((W - panelW) / 2) + 'px';
    panel.style.width = panelW + 'px';
    panel.style.font = fontCss(fs, family(w.font));
    panel.style.lineHeight = lh + 'px';
    setColors(panel, w);
    let n = 0;
    // кавычки и тире — как в тексте страницы (модули quotes и dash экспорта)
    for (const c of s.choices) {
      if (!c.enabled && !c.altText) continue;
      const row = el('div', 'choice' + (c.enabled ? ' hit' : ' disabled'));
      row.style.marginBottom = (lh * 0.9) + 'px';
      if (c.enabled) {
        row.appendChild(el('span', 'num', (++n) + '. '));
        row.appendChild(el('span', 'body', typo(c.text)));
        row.addEventListener('click', (ev) => { ev.stopPropagation(); ctx.command('choose', { index: c.index }); });
      } else {
        row.appendChild(el('span', 'body', typo(c.altText)));
      }
      panel.appendChild(row);
    }
    panel.hidden = false;
    const total = panel.scrollHeight - lh * 0.5;
    const panelH = Math.max(H * 0.46, total + lh);
    panel.style.height = panelH + 'px';
    panel.style.top = Math.max(20, (H - panelH) / 2 - 80) + 'px';
  }

  function renderInventory(s, vn, t) {
    const inv = $('stage-inventory');
    if (unchanged(inv, s.inventory, state.selected, vn, t.inventory, t.inventoryHorizontal, t.inventoryAlign, textScale)) return;
    inv.textContent = '';
    if (!s.inventory.length) { inv.hidden = true; return; }
    inv.hidden = false;
    inv.classList.toggle('strip', vn);
    const w = t.inventory;
    const fam = family(w.font);
    const lh = Math.floor(fontHeight(fsize(w), fam) * w.lineHeight);
    const line = el('div', 'inv-items');
    line.style.font = fontCss(fsize(w), fam);
    line.style.lineHeight = lh + 'px';
    setColors(line, w);
    line.style.width = w.w + 'px';
    line.style.textAlign = t.inventoryHorizontal ? t.inventoryAlign : 'left';
    s.inventory.forEach((item, i) => {
      // предметы в строку разделяются « | », как hinv_delim в INSTEAD
      if (i > 0 && t.inventoryHorizontal) line.appendChild(document.createTextNode(' | '));
      // строки подписи («^» в disp: «Слава: 0^Боеспособность: 45») — с новой строки, как в INSTEAD
      const label = item.label;
      const node = item.active ? ctx.link(label, () => ctx.command('item', { id: item.id }), 'hit') : el('span', 'inactive', label);
      node.classList.add('item');
      if (item.id === state.selected) node.classList.add('selected');
      const icon = ctx.imageUrl(item.icon);
      if (icon) {
        const img = el('img');
        img.src = icon;
        img.style.height = lh + 'px';
        img.style.width = lh + 'px';
        node.insertBefore(img, node.firstChild);
      }
      if (!t.inventoryHorizontal) {
        const row = el('div');
        row.appendChild(node);
        line.appendChild(row);
      } else {
        line.appendChild(node);
      }
    });
    inv.appendChild(line);
    if (vn) {
      // VN: под инвентарём — сплошная чёрная полоса до низа экрана; строки не уходят за низ
      let y = w.y;
      const need = line.scrollHeight;
      if (y + need > H) y = Math.max(0, H - need);
      place(inv, { x: 0, y: y, w: W, h: H - y });
      line.style.marginLeft = w.x + 'px';
    } else {
      place(inv, w);
      line.style.marginLeft = '0';
    }
  }

  function renderMenuButton(t) {
    // Игра NLB ставит кнопку сама (nlb.lua, _syscall_showmenubtn): gfx/menubtn_ru.png (для русской
    // книги) или gfx/menubtn.png темы — в правый верхний угол экрана темы. Без неё —
    // menu.gfx.button в menu.button.x/y из theme.ini, как в INSTEAD
    const button = $('stage-menu-button');
    const nlb = state.language === 'ru' && t.menuButtonRu ? t.menuButtonRu : t.menuButton;
    const path = nlb || t.themeMenuButton;
    if (!path) {
      button.classList.add('text');
      button.textContent = ctx.ui().menu;
      button.style.left = '';
      button.style.right = '0';
      button.style.top = '0';
      return;
    }
    const url = themeUrl(path);
    const position = () => {
      const img = button.querySelector('img');
      const iw = img && img.naturalWidth ? img.naturalWidth * t.scale : 0;
      button.style.right = '';
      button.style.left = (nlb ? t.screenArea.x + t.screenArea.w - iw : t.menuButtonX) + 'px';
      button.style.top = (nlb ? t.screenArea.y : t.menuButtonY) + 'px';
    };
    if (button.dataset.url !== url) {
      button.dataset.url = url;
      button.classList.remove('text');
      button.textContent = '';
      const img = el('img');
      img.alt = ctx.ui().menu;
      img.draggable = false;
      img.onload = () => {
        img.style.width = (img.naturalWidth * t.scale) + 'px';
        img.style.height = (img.naturalHeight * t.scale) + 'px';
        position();
      };
      img.src = url;
      button.appendChild(img);
    }
    position();
  }

  // ------------------------------------------------------------------ ввод
  $('stage').addEventListener('click', (ev) => {
    if (!state) return;
    if (ev.target.closest('.hit') || ev.target.closest('#stage-menu-button')) {
      click();
      return;
    }
    if (ev.target.closest('#stage-up')) { click(); scr.scroll = Math.max(0, scr.scroll - scr.pageH); render(); return; }
    if (ev.target.closest('#stage-down')) { click(); pageDown(); return; }
    if (scr.showChoices) {
      // экран вариантов: щелчок мимо вариантов — назад к тексту
      scr.showChoices = false;
      render();
    } else if (choicesMode()) {
      advanceText();  // как paginator: щелчок мимо спрайтов — дальше по тексту
    } else if (scr.more && (isVN() || ev.target.closest('#stage-text'))) {
      pageDown();  // щелчок по окну текста — следующая его часть
    }
  });
  $('stage-menu-button').addEventListener('click', (ev) => { ev.stopPropagation(); click(); ctx.mainMenu(); });
  $('stage').addEventListener('wheel', (ev) => {
    if (!state || scr.showChoices) return;
    const w = theme().text;
    const lh = Math.floor(fontHeight(fsize(w), family(w.font)) * w.lineHeight);
    scr.scroll = Math.max(0, scr.scroll + Math.sign(ev.deltaY) * 3 * lh);
    render();
  }, { passive: true });
  // пункты меню — тоже со звуком щелчка
  $('dialog').addEventListener('click', (ev) => {
    if (state && ev.target.closest('.menu-item')) click();
  });

  // Клавиши: пробел и Enter — дальше, Esc — назад от экрана вариантов, PgUp/PgDn — листание.
  // true — клавиша обработана
  function key(ev) {
    if (ev.key === ' ' || ev.key === 'Enter') {
      if (choicesMode() && !scr.showChoices) advanceText();
      else if (scr.more) pageDown();
      else if (scr.showChoices) { scr.showChoices = false; render(); }
      else if (advanceToChoices()) render();
      return true;
    }
    if (ev.key === 'Escape' && scr.showChoices) {
      scr.showChoices = false;
      render();
      return true;
    }
    if (ev.key === 'PageDown' && scr.more) { pageDown(); return true; }
    if (ev.key === 'PageUp') { scr.scroll = Math.max(0, scr.scroll - scr.pageH); render(); return true; }
    return false;
  }

  // Пункт меню «Варианты: отдельным экраном / в тексте» (как в NLBNavigator; действует на VN)
  function menuItem() {
    return {
      label: choicesScreen ? ctx.ui().choicesScreen : ctx.ui().choicesInText,
      action: () => {
        choicesScreen = !choicesScreen;
        ctx.store.set('nlb.choicesScreen', choicesScreen);
        scr.showChoices = false;
        scr.scroll = 0;
        render();
      }
    };
  }

  // Пункт меню «Размер текста: N %» — по кругу 100 % -> 125 % -> … -> 200 % -> 100 %; меню
  // остаётся открытым, чтобы было видно, как меняется размер
  function textSizeItem() {
    return {
      label: ctx.ui().textSize + ': ' + Math.round(textScale * 100) + '%',
      action: () => {
        textScale = TEXT_SCALES.find((v) => v > textScale + 0.01) || TEXT_SCALES[0];
        ctx.store.set('nlb.textScale', textScale);
        applyTextScale();
        scr.scroll = 0;  // строки текста переразбиваются заново
        if (state) render();
      }
    };
  }
  // Меню, сообщения и подсказка (вне сцены) — в том же масштабе
  function applyTextScale() {
    document.documentElement.style.setProperty('--text-scale', String(textScale));
  }
  applyTextScale();

  return {
    loadTheme: loadTheme,
    defaultMode: () => defaultMode,
    textSizeItem: textSizeItem,
    update: update,
    render: render,
    key: key,
    menuItem: menuItem,
    voicesByPortion: () => choicesMode()
  };
};
