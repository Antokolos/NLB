// Прогон экспорта URQ в ядре UrqW (js/quest.js, parser.js, evaluator.js, functions.js,
// player/*.js, tools.js из github.com/urqw/UrqW) в node, без браузера: интерфейс (Client, $, Audio)
// — заглушки, текст и кнопки — из Client.render.
//
// Запуск:
//   node urqw-run.js <каталог UrqW> <book.qst (windows-1251)> "<шаги через запятую>"
//   шаги: подпись кнопки; link:<N> — N-я ссылка в тексте; o:<подпись> — ссылка по подписи;
//   inv:<подпись> — щелчок по предмету
//   node urqw-run.js <каталог UrqW> <book.qst> random:<N>   — N случайных нажатий
// DETERMINISTIC=1 — случайность как --deterministic Java-плеера (PlayerContext): RANDOM — счётчик
// (0, 1, 2… по модулю, +1), SHUFFLE — разворот списка; так прогоны со случайностью (колода в бою
// «Фронтира») сравнимы с плеером.
// VARS=a,b — в конце вывести значения этих переменных.
// MEDIA=1 — картинки в тексте (IMG) и звуки с прошлого шага (SND: MUSIC — музыка, PLAY — эффект,
// STOP — музыка остановлена)
const fs = require('fs'), vm = require('vm'), path = require('path');
// шаги — через запятую или, если в подписях есть запятые, через «;;»
const U = process.argv[2], questFile = process.argv[3], arg4 = process.argv[4] || '',
  script = arg4.split(arg4.includes(';;') ? ';;' : ',').filter(x => x);
const jq = function (sel) {
  if (sel === '<img>') {  // оператор image: $('<img>').attr({src…}).prop('outerHTML')
    const a = {}; const img = { attr: (m) => { Object.assign(a, m); return img; }, prop: () => '<img src="' + a.src + '">' }; return img;
  }
  const o = new Proxy(function () {}, { get: (t, k) => k === 'length' ? 0 : (k === 'text' || k === 'html' || k === 'val' ? () => '' : () => o), apply: () => o }); return o;
};
// $.each — обход объекта (invkill без аргумента так очищает инвентарь)
jq.each = (obj, fn) => { for (const k of Object.keys(obj)) if (fn(k, obj[k]) === false) break; return obj; };
const ctx = { console, Math, Date, JSON, parseInt, parseFloat, isNaN, isFinite, String, Number, Object, Array, RegExp, Error, setTimeout, clearTimeout,
  Audio: function () {
    this.paused = true; this.src = '';
    this.play = () => { this.paused = false; ctx.sounds.push((this === ctx.gameMusic ? 'MUSIC ' : 'PLAY ') + this.src); };
    this.pause = () => { this.paused = true; };
    this.getAttribute = (k) => this[k];
    this.removeAttribute = (k) => { this[k] = ''; };
  },
  resetAudio: (a) => { if (a === ctx.gameMusic && !a.paused) ctx.sounds.push('STOP ' + a.src); a.pause(); a.src = ''; }, sounds: [], $: jq, jQuery: jq, i18next: { t: k => k }, navigator: {}, document: {}, localStorage: { getItem: () => null, setItem: () => {} },
  announceForAccessibility: () => {}, getGameFileURL: x => x, mode: 'urqw', manifest: {}, htmlSupport: true, files: null, questPath: '.' };
ctx.window = ctx; vm.createContext(ctx);
for (const f of ['js/tools.js', 'js/player/player.js', 'js/player/engine.js', 'js/quest.js', 'js/parser.js', 'js/functions.js', 'js/evaluator.js']) {
  vm.runInContext(fs.readFileSync(path.join(U, f), 'utf8'), ctx, { filename: f });
}
vm.runInContext('var __Expression = Expression; Expression = function (s) { try { return __Expression(s); } catch (e) { console.log("ВЫРАЖЕНИЕ: " + s); throw e; } };', ctx);
vm.runInContext(`
var screens = [], linkText = {};
function Client() {
  var str = function (t) { try { return typeof t === 'string' ? t : (Array.isArray(t) ? str(t[0]) : (t && typeof t.text === 'string' ? t.text : '[объект]')); } catch (e) { return '[объект]'; } };
  var render = function (data) { screens.push({ text: data.text.map(str).join('|'), buttons: data.buttons.map(b => b.desc + '=>' + b.command), status: data.status }); };
  // ссылка в тексте ([[текст|метка]]): подпись запоминается для шага o:<подпись>
  var convertToLink = function (text, id) { linkText[id] = text; return '<a>' + text + '</a>'; };
  return new Proxy({}, { get: (t, k) => k === 'render' ? render : (k === 'convertToLink' ? convertToLink : (k in t ? t[k] : function () {})), set: (t, k, v) => { t[k] = v; return true; } });
}
`, ctx);
const quest = fs.readFileSync(questFile, 'latin1');
const text = require('buffer').Buffer.from(quest, 'latin1');
const decoded = new (require('util').TextDecoder)('windows-1251').decode(text);
ctx.questText = process.env.DETERMINISTIC ? deterministic(decoded) : decoded;
vm.runInContext(`
Game = new Quest(questText); Game.name = 'test'; Game.init();
GlobalPlayer = new Player(); Game.sysVarInit();
GlobalPlayer.continue();
`, ctx);
// варианты хода: кнопки, ссылки в тексте ([[текст|метка]]), предметы инвентаря (метка use_<предмет>)
const options = () => {
  const result = [];
  for (const b of vm.runInContext('GlobalPlayer.buttons', ctx)) result.push({ kind: 'btn', name: b.desc.trim(), id: b.id });
  const links = vm.runInContext('GlobalPlayer.links', ctx);
  const texts = vm.runInContext('linkText', ctx);
  for (const id in links) if (links[id] !== null && links[id] !== undefined) result.push({ kind: 'link', name: String(texts[id] !== undefined ? texts[id] : links[id]), id: id });
  const items = vm.runInContext('Game.items', ctx);
  for (const item in items) {
    const label = 'use_' + item.toLowerCase();
    if (vm.runInContext('Game.useLabels', ctx)[label] !== undefined) {
      result.push({ kind: 'inv', name: String(vm.runInContext(`Game.getVar(${JSON.stringify('display_use_' + item)})`, ctx)), label: label });
    }
  }
  return result;
};
// подписи кнопок UrqW хранит с сущностями (&#34; — кавычка)
const decode = (t) => t.replace(/&#(\d+);/g, (m, c) => String.fromCharCode(+c)).replace(/&quot;/g, '"').replace(/&amp;/g, '&');
const run = (o) => {
  if (o.kind === 'btn') vm.runInContext(`GlobalPlayer.action(${JSON.stringify(o.id)})`, ctx);
  else if (o.kind === 'link') vm.runInContext(`GlobalPlayer.action(${JSON.stringify(o.id)}, true)`, ctx);
  else vm.runInContext(`GlobalPlayer.useAction(${JSON.stringify(o.label)})`, ctx);
};
const show = () => {
  const s = ctx.screens[ctx.screens.length - 1];
  console.log('TEXT: ' + s.text.replace(/<[^>]+>/g, '') + '\nBUTTONS: ' + JSON.stringify(s.buttons.map(b => b.split('=>')[0])) + '\nINV: ' + JSON.stringify(options().filter(o => o.kind === 'inv').map(o => o.name))
    + (process.env.MEDIA ? '\nIMG: ' + JSON.stringify([...s.text.matchAll(/<img[^>]* src="([^"]*)"/g)].map(m => m[1])) + '\nSND: ' + JSON.stringify(ctx.sounds) : ''));
  ctx.sounds.length = 0;
};
show();
if (script[0] && script[0].startsWith('random:')) {
  const steps = parseInt(script[0].split(':')[1]); let seen = new Set(), n = 0;
  for (; n < steps; n++) {
    const opts = options();
    if (!opts.length) break;
    run(opts[Math.floor(Math.random() * opts.length)]);
    const s = ctx.screens[ctx.screens.length - 1]; seen.add(s.text.slice(0, 40));
  }
  console.log('ходов: ' + n + ', разных экранов: ' + seen.size + ', последний: ' + ctx.screens[ctx.screens.length - 1].text.replace(/<[^>]+>/g, '').slice(0, 80));
  process.exit(0);
}
for (const choice of script) {
  let opt;
  if (choice.startsWith('link:')) opt = options().filter(o => o.kind === 'link')[parseInt(choice.slice(5)) - 1];
  else if (choice.startsWith('inv:')) opt = options().find(o => o.kind === 'inv' && o.name === choice.slice(4));
  else if (choice.startsWith('o:')) opt = options().find(o => o.kind === 'link' && o.name.replace(/<[^>]*>/g, '') === choice.slice(2));
  else opt = options().find(o => o.kind === 'btn' && decode(o.name) === choice);
  if (!opt) { console.log('нет ' + choice); break; }
  run(opt);
  console.log('> ' + choice); show();
}
const vars = vm.runInContext('Game.vars', ctx);
// VARS=a,b — только эти переменные (имена в UrqW — в нижнем регистре), иначе служебные
const wanted = (process.env.VARS || '').split(',').filter(x => x).map(x => x.toLowerCase());
console.log('VARS: ' + JSON.stringify(Object.fromEntries(Object.entries(vars).filter(([k]) => wanted.length ? wanted.includes(k.toLowerCase())
  : /^(counter|dice|lucky|i|x|nlb_)/.test(k)))));

function deterministic(text) {
  const nl = text.includes('\r\n') ? '\r\n' : '\n';
  text = text.replace(/^([ \t]*)(\w+)=rnd#(.+)\$$/gm, (m, ind, v, max) => [ind + 'nlb_dmax=' + max, ind + 'proc nlb_detrnd', ind + v + '=nlb_dres'].join(nl));
  const start = text.indexOf(':nlb_lshuffle' + nl), endMark = ':nlb_lshuffle_end' + nl + 'end';
  const end = text.indexOf(endMark, start);
  if (start < 0 || end < 0) throw new Error('DETERMINISTIC: нет метки nlb_lshuffle');
  const shuffle = [':nlb_lshuffle', 'nlb_k=0', 'nlb_j=nlb_ln_#%nlb_lname$-1', ':nlb_lshuffle_loop',
    'if nlb_k>=nlb_j then goto nlb_lshuffle_end',
    'nlb_lres = nlb_li_#%nlb_lname$_#nlb_k$',
    'nlb_li_#%nlb_lname$_#nlb_k$ = nlb_li_#%nlb_lname$_#nlb_j$',
    'nlb_li_#%nlb_lname$_#nlb_j$ = nlb_lres',
    'nlb_k=nlb_k+1', 'nlb_j=nlb_j-1', 'goto nlb_lshuffle_loop'].join(nl) + nl;
  return text.slice(0, start) + shuffle + text.slice(end) + nl
    + [':nlb_detrnd', 'nlb_dres=nlb_detc-nlb_dmax*int(nlb_detc/nlb_dmax)+1', 'nlb_detc=nlb_detc+1', 'end', ''].join(nl);
}
