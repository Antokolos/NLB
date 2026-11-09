// Прогон экспорта URQ в ядре UrqW (js/quest.js, parser.js, evaluator.js, functions.js,
// player/*.js, tools.js из github.com/urqw/UrqW) в node, без браузера: интерфейс (Client, $, Audio)
// — заглушки, текст и кнопки — из Client.render.
//
// Запуск:
//   node urqw-run.js <каталог UrqW> <book.qst (windows-1251)> "<подписи кнопок через запятую>"
//   node urqw-run.js <каталог UrqW> <book.qst> random:<N>   — N случайных нажатий
const fs = require('fs'), vm = require('vm'), path = require('path');
const U = process.argv[2], questFile = process.argv[3], script = (process.argv[4] || '').split(',').filter(x => x);
const jq = function () { const o = new Proxy(function () {}, { get: (t, k) => k === 'length' ? 0 : (k === 'text' || k === 'html' || k === 'val' ? () => '' : () => o), apply: () => o }); return o; };
const ctx = { console, Math, Date, JSON, parseInt, parseFloat, isNaN, isFinite, String, Number, Object, Array, RegExp, Error, setTimeout, clearTimeout,
  Audio: function () { this.play = () => {}; this.pause = () => {}; }, $: jq, jQuery: jq, i18next: { t: k => k }, navigator: {}, document: {}, localStorage: { getItem: () => null, setItem: () => {} },
  announceForAccessibility: () => {}, getGameFileURL: x => x, mode: 'urqw', manifest: {} };
ctx.window = ctx; vm.createContext(ctx);
for (const f of ['js/tools.js', 'js/player/player.js', 'js/player/engine.js', 'js/quest.js', 'js/parser.js', 'js/functions.js', 'js/evaluator.js']) {
  vm.runInContext(fs.readFileSync(path.join(U, f), 'utf8'), ctx, { filename: f });
}
vm.runInContext(`
var screens = [];
function Client() {
  var str = function (t) { try { return typeof t === 'string' ? t : (Array.isArray(t) ? str(t[0]) : (t && typeof t.text === 'string' ? t.text : '[объект]')); } catch (e) { return '[объект]'; } };
  var render = function (data) { screens.push({ text: data.text.map(str).join('|'), buttons: data.buttons.map(b => b.desc + '=>' + b.command), status: data.status }); };
  return new Proxy({}, { get: (t, k) => k === 'render' ? render : (k in t ? t[k] : function () {}), set: (t, k, v) => { t[k] = v; return true; } });
}
`, ctx);
const quest = fs.readFileSync(questFile, 'latin1');
const text = require('buffer').Buffer.from(quest, 'latin1');
const decoded = new (require('util').TextDecoder)('windows-1251').decode(text);
ctx.questText = decoded;
vm.runInContext(`
Game = new Quest(questText); Game.name = 'test'; Game.init();
GlobalPlayer = new Player(); Game.sysVarInit();
GlobalPlayer.continue();
`, ctx);
const show = () => { const s = ctx.screens[ctx.screens.length - 1]; console.log('TEXT: ' + s.text.replace(/<[^>]+>/g, '') + '\nBUTTONS: ' + JSON.stringify(s.buttons.map(b => b.split('=>')[0]))); };
show();
if (script[0] && script[0].startsWith('random:')) {
  const steps = parseInt(script[0].split(':')[1]); let seen = new Set(), n = 0;
  for (; n < steps; n++) {
    const buttons = vm.runInContext('GlobalPlayer.buttons', ctx);
    if (!buttons.length) break;
    const b = buttons[Math.floor(Math.random() * buttons.length)];
    vm.runInContext(`GlobalPlayer.action(${JSON.stringify(b.id)})`, ctx);
    const s = ctx.screens[ctx.screens.length - 1]; seen.add(s.text.slice(0, 40));
  }
  console.log('ходов: ' + n + ', разных экранов: ' + seen.size + ', последний: ' + ctx.screens[ctx.screens.length - 1].text.replace(/<[^>]+>/g, '').slice(0, 80));
  process.exit(0);
}
for (const choice of script) {
  const s = ctx.screens[ctx.screens.length - 1];
  const b = vm.runInContext('GlobalPlayer.buttons', ctx).find(x => x.desc.trim() === choice);
  if (!b) { console.log('нет кнопки ' + choice); break; }
  vm.runInContext(`GlobalPlayer.action(${JSON.stringify(b.id)})`, ctx);
  console.log('> ' + choice); show();
}
const vars = vm.runInContext('Game.vars', ctx);
console.log('VARS: ' + JSON.stringify(Object.fromEntries(Object.entries(vars).filter(([k]) => /^(counter|dice|lucky|i|x|nlb_)/.test(k)))));
