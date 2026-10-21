// Веб-плеер NLB: рисует снимок экрана, который отдаёт сервер (/play/api), и передаёт ему
// щелчки игрока. Вся логика книги — на сервере; здесь — раскладка экрана как в INSTEAD, звук,
// сохранения и достижения (их хранит браузер: у игрока нет учётной записи).
(function () {
  'use strict';

  // ------------------------------------------------------------------ тексты интерфейса
  const UI = {
    ru: {
      menu: 'Меню', save: 'Сохранить игру', saveForbidden: 'Сохранить игру — здесь нельзя', load: 'Загрузить игру',
      restart: 'Начать заново', restartQuestion: 'Начать игру заново? Несохранённый прогресс будет потерян.',
      yes: 'Да', no: 'Нет', soundOff: 'Выключить звук', soundOn: 'Включить звук', language: 'Язык',
      library: 'К списку книг', back: 'Вернуться в игру', saveToSlot: 'Сохранить в слот', loadFromSlot: 'Загрузить из слота',
      empty: 'пусто', cancel: 'Отмена', saved: 'Игра сохранена', loaded: 'Игра загружена', failed: 'Не удалось: ',
      overwrite: 'Слот занят. Перезаписать?', noSaves: 'Сохранений нет.',
      itemSelected: 'Выбран предмет: щёлкните, к чему применить (правая кнопка или Esc — отмена)',
      achievement: 'Получено достижение: ', theEnd: 'Конец игры.', tryAgain: 'Начать заново',
      externalGame: 'Внешняя игра «%s»: в этом проигрывателе её нет.', backToBook: 'Вернуться в книгу',
      books: 'Книги', noBooks: 'В библиотеке нет книг.', mode: 'Режим', modeVn: 'визуальная новелла',
      modeStandard: 'стандартная тема', sessionLost: 'Игра на сервере не найдена — начинаем заново.',
      otherBook: ' (другая книга)', loading: 'Загрузка…',
      choicesScreen: 'Варианты: отдельным экраном', choicesInText: 'Варианты: в тексте'
    },
    en: {
      menu: 'Menu', save: 'Save game', saveForbidden: 'Save game — not allowed here', load: 'Load game',
      restart: 'Restart', restartQuestion: 'Restart the game? Unsaved progress will be lost.',
      yes: 'Yes', no: 'No', soundOff: 'Turn sound off', soundOn: 'Turn sound on', language: 'Language',
      library: 'Back to the books', back: 'Back to the game', saveToSlot: 'Save to slot', loadFromSlot: 'Load from slot',
      empty: 'empty', cancel: 'Cancel', saved: 'Game saved', loaded: 'Game loaded', failed: 'Failed: ',
      overwrite: 'The slot is not empty. Overwrite?', noSaves: 'No saved games.',
      itemSelected: 'Item selected: click what to use it on (right button or Esc — cancel)',
      achievement: 'Achievement unlocked: ', theEnd: 'The end.', tryAgain: 'Start again',
      externalGame: 'External game «%s» is not available in this player.', backToBook: 'Return to the book',
      books: 'Books', noBooks: 'There are no books in the library.', mode: 'Mode', modeVn: 'visual novel',
      modeStandard: 'standard theme', sessionLost: 'The game was not found on the server — starting anew.',
      otherBook: ' (other book)', loading: 'Loading…',
      choicesScreen: 'Choices: separate screen', choicesInText: 'Choices: in the text'
    }
  };
  const LANGUAGE_NAMES = {
    en: 'English', ru: 'Русский', uk: 'Українська', be: 'Беларуская', de: 'Deutsch', fr: 'Français',
    es: 'Español', it: 'Italiano', pt: 'Português', pl: 'Polski', cs: 'Čeština', zh: '中文', ja: '日本語', ko: '한국어'
  };
  let ui = UI.ru;
  const SAVE_SLOTS = 9;

  // ------------------------------------------------------------------ хранилище браузера
  const store = {
    get(key, fallback) {
      try {
        const v = localStorage.getItem(key);
        return v === null ? fallback : JSON.parse(v);
      } catch (e) {
        return fallback;
      }
    },
    set(key, value) {
      try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* хранилище недоступно */ }
    },
    session(key, value) {
      try {
        if (value === undefined) return sessionStorage.getItem(key);
        if (value === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, value);
      } catch (e) { return null; }
      return null;
    }
  };

  // ------------------------------------------------------------------ сервер
  class ApiError extends Error {
    constructor(message, status) { super(message); this.status = status; }
  }
  async function api(path, body) {
    const options = body === undefined ? {} : {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    };
    const response = await fetch('api/' + path, options);
    let data = {};
    try { data = await response.json(); } catch (e) { /* не JSON */ }
    if (!response.ok) throw new ApiError(data.error || response.statusText, response.status);
    return data;
  }

  const $ = (id) => document.getElementById(id);
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  // ------------------------------------------------------------------ состояние игры
  const params = new URLSearchParams(location.search);
  const game = {
    bookId: params.get('book'),
    session: null,
    state: null,
    mode: params.get('mode') === 'standard' ? 'standard' : 'vn',
    lastPage: null,
    lastReaction: '',
    reactionThenRoom: true,
    queue: Promise.resolve(),
    timer: null,
    lastTick: 0,
    animation: null
  };

  // Команды игрока выполняются по очереди: ответ одной — снимок, по которому рисуется экран
  function command(name, body) {
    game.queue = game.queue.then(() => send(name, body)).catch(() => {});
    return game.queue;
  }
  async function send(name, body) {
    try {
      const state = await api('sessions/' + encodeURIComponent(game.session) + '/' + name, body || {});
      apply(state, name);
      return state;
    } catch (e) {
      if (e.status === 404 && /игра не найдена|not found/i.test(e.message)) {
        toast(ui.sessionLost);
        await newGame();
      } else {
        toast(ui.failed + e.message);
      }
      return null;
    }
  }

  function preferredLanguages() {
    const list = [];
    const chosen = store.get('nlb.language.' + game.bookId, null);
    if (chosen) list.push(chosen);
    if (params.get('lang')) list.push(params.get('lang'));
    (navigator.languages || [navigator.language || '']).forEach((l) => list.push(l));
    return list;
  }

  async function newGame() {
    const state = await api('sessions', {
      book: game.bookId, mode: game.mode, languages: preferredLanguages(),
      achievements: store.get('nlb.achievements.' + game.bookId, null)
    });
    game.session = state.session;
    store.session('nlb.session.' + game.bookId + '.' + game.mode, game.session);
    game.lastPage = null;
    apply(state);
  }

  async function startGame() {
    await vnScreen.loadTheme();
    const saved = store.session('nlb.session.' + game.bookId + '.' + game.mode);
    if (saved) {
      try {
        game.session = saved;
        apply(await api('sessions/' + encodeURIComponent(saved)));
        return;
      } catch (e) { /* игры уже нет на сервере — новая */ }
    }
    try {
      await newGame();
    } catch (e) {
      $('game').hidden = false;
      $('text').textContent = ui.failed + e.message;
    }
  }

  // ------------------------------------------------------------------ ответ сервера
  function plain(paragraphs) {
    return paragraphs.map((p) => p.runs.map((r) => r.text).join('')).join('\n');
  }

  // Экран VN (vn.js) — для страниц VN в режиме VN; страницы STANDARD и режим STANDARD — экран INSTEAD
  const isVnView = (state) => state.mode === 'vn' && state.screen.layout === 'VisualNovel';

  function apply(state, commandName) {
    game.state = state;
    ui = state.language === 'ru' ? UI.ru : UI.en;
    document.documentElement.lang = state.language || 'en';
    store.set('nlb.achievements.' + game.bookId, state.achievements);
    const s = state.screen;
    const reaction = plain(s.reactionText);
    // Как GameScreen: действие привело на новую страницу — ответ (если есть) и описание комнаты;
    // новое действие на той же странице — только ответ
    const newPage = s.pageId !== game.lastPage || ['restart', 'tryAgain', 'load'].includes(commandName);
    if (newPage) {
      game.reactionThenRoom = true;
    } else if (reaction && reaction !== game.lastReaction) {
      game.reactionThenRoom = false;
    }
    game.lastReaction = reaction;
    const vnView = isVnView(state);
    if (vnView) vnScreen.update(state, commandName);
    processEvents(state.events, newPage, vnView);
    game.lastPage = s.pageId;
    document.body.classList.toggle('vn-mode', vnView);
    $('game').hidden = vnView;
    $('vn').hidden = !vnView;
    if (vnView) vnScreen.render(); else render();
    scheduleTimer();
  }

  // ------------------------------------------------------------------ звук
  const audio = {
    muted: store.get('nlb.muted', false),
    music: null,
    musicName: '',
    voice: null
  };
  function mediaUrl(name) {
    return 'media/' + encodeURIComponent(game.bookId) + '/' + name.split('/').map(encodeURIComponent).join('/');
  }
  function playMusic(name) {
    if (name === audio.musicName) return;  // та же дорожка не перезапускается (set_music)
    audio.musicName = name;
    if (audio.music) { audio.music.pause(); audio.music = null; }
    if (!name || audio.muted) return;
    audio.music = new Audio(mediaUrl(name));
    audio.music.loop = true;
    audio.music.play().catch(() => {});
  }
  function playEffect(name) {
    if (!name || audio.muted) return;
    new Audio(mediaUrl(name)).play().catch(() => {});
  }
  function playVoice(name) {
    if (audio.voice) { audio.voice.pause(); audio.voice = null; }
    if (!name || audio.muted) return;
    audio.voice = new Audio(mediaUrl(name));
    audio.voice.play().catch(() => {});
  }
  function setMuted(muted) {
    audio.muted = muted;
    store.set('nlb.muted', muted);
    const name = audio.musicName;
    audio.musicName = null;
    if (muted) {
      if (audio.music) audio.music.pause();
      audio.music = null;
      playVoice('');
      audio.musicName = name;
    } else {
      playMusic(name || '');
    }
  }

  function processEvents(events, newPage, vnView) {
    for (const e of events) {
      switch (e.kind) {
        case 'Music': playMusic(e.text); break;
        case 'Sound': playEffect(e.text); break;
        case 'Achievement': toast(ui.achievement + (e.id || e.text)); break;
        case 'ExternalGame': externalGame(e.text); break;
        default: break;
      }
    }
    // Озвучка страницы: без порций текста — первая фраза при входе на страницу; уход — стоп.
    // На экране VN с порциями фразы идут по порциям (vn.js)
    if (newPage) {
      const byPortions = vnView && vnScreen.voicesByPortion();
      playVoice(!byPortions && game.state.voices.length ? game.state.voices[0] : '');
    }
    if (!audio.musicName && game.state.music && !audio.muted && !audio.music) {
      playMusic(game.state.music);  // после перезагрузки страницы браузера — музыка игры
    }
  }

  // ------------------------------------------------------------------ таймер страницы
  // Время книги идёт на сервере (advance): таймер страницы и кадры анимации
  function scheduleTimer() {
    const s = game.state.screen;
    const active = s.timerActive && !s.finished && !s.externalGame;
    if (!active) {
      if (game.timer) { clearInterval(game.timer); game.timer = null; }
      return;
    }
    if (game.timer) return;
    game.lastTick = performance.now();
    game.timer = setInterval(() => {
      if (document.hidden || !$('dialog').hidden) { game.lastTick = performance.now(); return; }
      const now = performance.now();
      const interval = Math.max(game.state.screen.timerIntervalMs || 200, 200);
      if (now - game.lastTick < interval) return;
      const ms = Math.min(Math.round(now - game.lastTick), 5000);
      game.lastTick = now;
      command('advance', { ms: ms });
    }, 100);
  }

  // ------------------------------------------------------------------ картинки
  function imageUrl(image, frame) {
    if (!image) return null;
    if (image.frameCount > 0 && image.framePattern) {
      return mediaUrl(image.framePattern.replace('%d', String(frame || image.currentFrame || 1)));
    }
    return image.file ? mediaUrl(image.file) : null;
  }
  // Анимация (картинка с числом кадров): кадры сменяются по 25 в секунду, как nlb._fps
  function animate(img, image) {
    if (!(image.frameCount > 0)) return;
    let tick = Math.max(0, (image.currentFrame || 1) - 1);
    const id = setInterval(() => {
      if (!img.isConnected) { clearInterval(id); return; }
      tick++;
      const frame = image.looped ? (tick % image.frameCount) + 1 : Math.min(tick, image.frameCount - 1) + 1;
      img.src = imageUrl(image, frame);
      if (!image.looped && tick >= image.frameCount - 1) clearInterval(id);
    }, 40);
  }
  function picture(image, cls, alt) {
    const url = imageUrl(image);
    if (!url) return null;
    const img = el('img', cls);
    img.src = url;
    img.alt = alt || '';
    img.onerror = () => { img.hidden = true; };
    animate(img, image);
    return img;
  }

  // ------------------------------------------------------------------ текст
  function link(text, onClick, cls) {
    const a = el('a', 'link' + (cls ? ' ' + cls : ''), text);
    a.href = '#';
    a.addEventListener('click', (ev) => { ev.preventDefault(); onClick(); });
    return a;
  }
  function runNode(run) {
    if (run.kind === 'ObjectLink') return link(run.text, () => command('object', { id: run.target }));
    if (run.kind === 'ChoiceLink') {
      return run.target === '-1' ? link(run.text, () => command('look'))
        : link(run.text, () => command('choose', { index: Number(run.target) }));
    }
    return document.createTextNode(run.text);
  }
  function paragraphNode(p) {
    const node = el('p', p.cls || '');
    for (const run of p.runs) node.appendChild(runNode(run));
    return node;
  }
  const choiceRun = (c) => ({ kind: 'ChoiceLink', text: c.text, target: String(c.index) });

  // Как GameScreen::renderTextWindow для STANDARD (INSTEAD, stead2): заголовок комнаты по центру
  // (щелчок — осмотреться), пути через « | », сцена: описание комнаты, описания объектов;
  // после действия — курсивом ответ (на новой странице — и её описание)
  function composeStandard(s) {
    const out = [];
    if (s.roomTitle) out.push({ cls: 'title', runs: [{ kind: 'ChoiceLink', text: s.roomTitle, target: '-1' }] });
    const ways = [];
    for (const c of s.choices) {
      if (!c.enabled || c.inlineLink) continue;
      if (ways.length) ways.push({ kind: 'Plain', text: ' | ' });
      ways.push(choiceRun(c));
    }
    if (ways.length) out.push({ cls: 'ways', runs: ways });
    const scene = [];
    if (s.reactionText.length) {
      for (const p of s.reactionText) {
        if (!p.runs.length) continue;
        const last = scene[scene.length - 1];
        if (p.joinPrevious && last && last.cls === 'em') {
          last.runs = last.runs.concat([{ kind: 'Plain', text: ' ' }], p.runs);
        } else {
          scene.push({ cls: 'em', runs: p.runs.slice() });
        }
      }
      if (game.reactionThenRoom) scene.push(...s.roomText);
    } else {
      scene.push(...s.roomText);
    }
    scene.push(...s.objectsText);
    for (const c of s.choices) {
      if (!c.enabled && c.altText) scene.push({ runs: [{ kind: 'Plain', text: c.altText }] });
    }
    for (const c of s.choices) {
      if (c.enabled && c.inlineLink) scene.push({ cls: 'inline-link', runs: [choiceRun(c)] });
    }
    scene.push(...s.modText);
    return out.concat(scene);
  }

  // Страница VN (полноценная раскладка VN — этап 6): текст, ответ и варианты в окне текста
  function composeVisualNovel(s) {
    const out = s.pageText.slice();
    for (const p of s.reactionText) out.push({ cls: 'em', runs: p.runs });
    for (const c of s.choices) {
      if (c.enabled) out.push({ cls: 'inline-link', runs: [choiceRun(c)] });
      else if (c.altText) out.push({ runs: [{ kind: 'Plain', text: c.altText }] });
    }
    return out;
  }

  // ------------------------------------------------------------------ экран
  function render() {
    const state = game.state;
    const s = state.screen;
    $('title').textContent = state.book.title || '';
    document.title = state.book.title || 'NLB';
    $('menu-button').textContent = ui.menu;

    const pic = $('picture');
    pic.textContent = '';
    const page = picture(s.pageImage, '', '');
    if (page) pic.appendChild(page);

    // Графические объекты — рядом с картинкой, без подписей, как GameScreen::renderSceneObjects
    // в STANDARD (на своих местах и с эффектами — клиент VN, этап 6); остальные объекты — в тексте
    const objects = $('objects');
    objects.textContent = '';
    for (const o of s.sceneObjects) {
      if (!o.graphical || !o.visible || o.collapsed) continue;
      const img = picture(o.image, '', o.label);
      if (!img) continue;
      const box = el('div', 'object');
      box.title = o.label;
      if (o.active) {
        const a = link('', () => command('object', { id: o.id }));
        a.appendChild(img);
        box.appendChild(a);
      } else {
        box.appendChild(img);
      }
      objects.appendChild(box);
    }

    const text = $('text');
    text.textContent = '';
    const paragraphs = s.layout === 'VisualNovel' ? composeVisualNovel(s) : composeStandard(s);
    for (const p of paragraphs) {
      if (!p.runs.length) { text.appendChild(el('p', '', ' ')); continue; }
      text.appendChild(paragraphNode(p));
    }
    if (s.finished) {
      const end = el('p', 'end', ui.theEnd + ' ');
      end.appendChild(link(ui.tryAgain, () => command('tryAgain')));
      text.appendChild(end);
    }

    renderInventory(s, state.selected);
    const hint = $('hint');
    hint.hidden = !state.selected;
    hint.textContent = ui.itemSelected;
  }

  function renderInventory(s, selected) {
    const inv = $('inventory');
    inv.textContent = '';
    const ids = new Set(s.inventory.map((i) => i.id));
    for (const item of s.inventory) {
      const label = item.label.replace(/\n/g, ' ');
      const node = item.active ? link(label, () => command('item', { id: item.id })) : el('span', '', label);
      node.classList.add('item');
      if (!item.active) node.classList.add('inactive');
      if (item.id === selected) node.classList.add('selected');
      if (item.parentId && ids.has(item.parentId)) node.classList.add('child');
      const icon = picture(item.icon, '', '');
      if (icon) node.insertBefore(icon, node.firstChild);
      inv.appendChild(node);
    }
  }

  // ------------------------------------------------------------------ сообщения и меню
  function toast(message) {
    const t = el('div', 'toast', message);
    $('toasts').appendChild(t);
    setTimeout(() => t.remove(), 4000);
  }

  function dialog(title, items, note) {
    const body = $('dialog-body');
    body.textContent = '';
    if (title) body.appendChild(el('h2', '', title));
    if (note) body.appendChild(el('p', 'note', note));
    for (const item of items) {
      const b = el('button', 'menu-item', item.label);
      b.type = 'button';
      b.disabled = !!item.disabled;
      b.addEventListener('click', () => { closeDialog(); if (item.action) item.action(); });
      body.appendChild(b);
    }
    $('dialog').hidden = false;
    const first = body.querySelector('button:not(:disabled)');
    if (first) first.focus();
  }
  function closeDialog() { $('dialog').hidden = true; }

  function externalGame(name) {
    dialog('', [{ label: ui.backToBook, action: () => command('finishExternal') }], ui.externalGame.replace('%s', name));
  }

  const savesKey = () => 'nlb.saves.' + game.bookId;
  function slotLabel(slot) {
    return slot ? slot.time + ' — ' + slot.label : '<' + ui.empty + '>';
  }
  function saveMenu() {
    const saves = store.get(savesKey(), {});
    const items = [];
    for (let n = 1; n <= SAVE_SLOTS; n++) {
      items.push({
        label: n + '. ' + slotLabel(saves[n]),
        action: () => {
          const doSave = async () => {
            const state = await send('save');
            if (!state || !state.saved) return;
            const all = store.get(savesKey(), {});
            all[n] = { save: state.saved.save, time: state.saved.time, label: state.saved.label };
            store.set(savesKey(), all);
            toast(ui.saved);
          };
          if (saves[n]) {
            dialog(ui.overwrite, [{ label: ui.yes, action: () => { game.queue = game.queue.then(doSave); } }, { label: ui.no, action: saveMenu }]);
          } else {
            game.queue = game.queue.then(doSave);
          }
        }
      });
    }
    items.push({ label: ui.cancel, action: null });
    dialog(ui.saveToSlot, items);
  }
  function loadMenu() {
    const saves = store.get(savesKey(), {});
    const items = [];
    for (let n = 1; n <= SAVE_SLOTS; n++) {
      if (!saves[n]) continue;
      items.push({
        label: n + '. ' + slotLabel(saves[n]),
        action: () => {
          game.queue = game.queue.then(async () => {
            const state = await send('load', { save: saves[n].save });
            if (state) toast(ui.loaded);
          });
        }
      });
    }
    items.push({ label: ui.cancel, action: null });
    dialog(ui.loadFromSlot, items, items.length === 1 ? ui.noSaves : null);
  }
  function languageMenu() {
    const items = game.state.languages.map((l) => ({
      label: (LANGUAGE_NAMES[l] || l) + (l === game.state.language ? ' ✓' : ''),
      action: () => {
        store.set('nlb.language.' + game.bookId, l);
        command('language', { language: l });
      }
    }));
    items.push({ label: ui.cancel, action: null });
    dialog(ui.language, items);
  }
  function mainMenu() {
    const s = game.state.screen;
    const items = [
      { label: s.canSave ? ui.save : ui.saveForbidden, disabled: !s.canSave, action: saveMenu },
      { label: ui.load, action: loadMenu },
      {
        label: ui.restart,
        action: () => dialog(ui.restartQuestion, [
          { label: ui.yes, action: () => command('restart') }, { label: ui.no, action: mainMenu }])
      }
    ];
    if (game.state.languages.length > 1) items.push({ label: ui.language, action: languageMenu });
    if (game.state.mode === 'vn') items.push(vnScreen.menuItem());
    items.push({ label: audio.muted ? ui.soundOn : ui.soundOff, action: () => setMuted(!audio.muted) });
    items.push({ label: ui.library, action: () => { location.href = location.pathname; } });
    items.push({ label: ui.back, action: null });
    dialog(ui.menu, items);
  }

  // ------------------------------------------------------------------ библиотека книг
  async function showLibrary() {
    $('library').hidden = false;
    ui = (navigator.language || '').toLowerCase().startsWith('ru') ? UI.ru : UI.en;
    $('library-title').textContent = ui.books;
    const list = $('books');
    list.textContent = ui.loading;
    let data;
    try {
      data = await api('books');
    } catch (e) {
      list.textContent = ui.failed + e.message;
      return;
    }
    list.textContent = '';
    if (!data.books.length) list.textContent = ui.noBooks;
    for (const book of data.books) {
      const li = el('li');
      const title = el('span', 'book-title', book.title || book.id);
      const mode = el('select');
      for (const [value, label] of [['vn', ui.modeVn], ['standard', ui.modeStandard]]) {
        const o = el('option', '', label);
        o.value = value;
        mode.appendChild(o);
      }
      mode.value = store.get('nlb.mode.' + book.id, book.mode || 'vn');
      mode.addEventListener('change', () => store.set('nlb.mode.' + book.id, mode.value));
      title.addEventListener('click', () => {
        location.href = '?book=' + encodeURIComponent(book.id) + '&mode=' + mode.value;
      });
      li.appendChild(title);
      li.appendChild(el('div', 'book-meta', [book.author, book.languages.map((l) => LANGUAGE_NAMES[l] || l).join(', ')]
        .filter(Boolean).join(' · ')));
      const meta = el('div', 'book-meta', ui.mode + ':');
      meta.appendChild(mode);
      li.appendChild(meta);
      list.appendChild(li);
    }
  }

  // ------------------------------------------------------------------ ввод
  $('menu-button').addEventListener('click', mainMenu);
  $('dialog').addEventListener('click', (ev) => { if (ev.target === $('dialog')) closeDialog(); });
  document.addEventListener('contextmenu', (ev) => {
    if (game.state && game.state.selected) { ev.preventDefault(); command('clear'); }
  });
  document.addEventListener('keydown', (ev) => {
    if (!game.state) return;
    if ($('dialog').hidden && isVnView(game.state) && !ev.ctrlKey && !ev.altKey && !ev.metaKey && vnScreen.key(ev)) {
      ev.preventDefault();
      return;
    }
    if (ev.key === 'Escape') {
      if (!$('dialog').hidden) closeDialog();
      else if (game.state.selected) command('clear');
      else mainMenu();
      return;
    }
    if (!$('dialog').hidden || ev.ctrlKey || ev.altKey || ev.metaKey) return;
    // Цифры — варианты по порядку, как в консольном плеере
    if (/^[1-9]$/.test(ev.key)) {
      const enabled = game.state.screen.choices.filter((c) => c.enabled);
      const c = enabled[Number(ev.key) - 1];
      if (c) command('choose', { index: c.index });
    }
  });

  const vnScreen = window.NLBVisualNovel({
    el: el, api: api, store: store, link: link, command: command, dialog: dialog, mainMenu: mainMenu,
    imageUrl: (image) => imageUrl(image), animate: animate, playVoice: playVoice,
    ui: () => ui, bookId: () => game.bookId
  });

  if (game.bookId) {
    startGame();
  } else {
    showLibrary();
  }
})();
