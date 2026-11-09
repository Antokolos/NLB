// Прогон экспорта QSP в настоящем движке QSP (WebAssembly-сборка qspfoundation, @qsp/wasm-engine)
// в node; текст -> .qsp — @qsp/converters (readQsps/writeQsp), как в веб-плеере qSpider.
//
// Пакеты (в отдельный каталог <пакеты>):
//   npm pack @qsp/wasm-engine@1.6.1 && tar xzf qsp-wasm-engine-1.6.1.tgz            -> <пакеты>/package
//   npm pack @qsp/converters@2.0.0 && mkdir conv && tar xzf qsp-converters-2.0.0.tgz -C conv
//                                                                                     -> <пакеты>/conv/package
// Запуск:
//   node qsp-run.js <абсолютный путь к пакетам> <book.txt (UTF-16LE)> "<шаги через ;;>"
// Шаги: подпись действия; obj:<подпись предмета> — выбрать предмет инвентаря; link:<N> — N-я
// ссылка на объект в тексте; exec:<код> — выполнить код QSP; random:<N> — N случайных шагов
// (действия, ссылки, предметы) с подсчётом ошибок движка. TAIL=1 — показывать конец текста,
// VARS=a,b — в конце вывести значения переменных.
// restartGame движка первую локацию не выполняет, поэтому игра начинается с GOTO первой локации.
const fs = require('fs'), path = require('path');
const P = process.argv[2], file = process.argv[3], script = (process.argv[4] || '').split(';;').filter(x => x);
const { readQsps, writeQsp } = require(path.join(P, 'conv/package/dist/index.cjs'));

(async () => {
  const { initQspEngine } = await import('file://' + path.join(P, 'package/dist/wasm-engine.modern.js'));
  const text = fs.readFileSync(file).toString('utf16le').replace(/^﻿/, '');
  const game = writeQsp(readQsps(text));
  const wasm = fs.readFileSync(path.join(P, 'package/dist/qsp-engine.wasm'));
  const api = await initQspEngine(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
  let main = '', actions = [], objects = [], errors = [];
  api.on('main_changed', t => { main = t; });
  api.on('actions_changed', a => { actions = a; });
  api.on('objects_changed', o => { objects = o; });
  api.on('error', e => { errors.push(JSON.stringify(e)); });
  api.on('wait', (ms, done) => done());
  api.on('system_cmd', () => {});
  const show = (label) => {
    console.log((label ? '> ' + label + '\n' : '') + 'TEXT: ' + (process.env.TAIL ? '…' + main.replace(/<[^>]+>/g, '').replace(/\r?\n/g, '|').slice(-120) : main.replace(/\r?\n/g, '|').replace(/<(?!a )[^>]+>/g, '')) +
      '\nACTIONS: ' + JSON.stringify(actions.map(a => a.name)) + '\nINV: ' + JSON.stringify(objects.map(o => o.name)) +
      (errors.length ? '\nERRORS: ' + errors.join(' | ') : ''));
    errors = [];
  };
  const tick = () => new Promise(r => setTimeout(r, 50));
  api.openGame(game, true);
  api.execCode("GOTO '" + api.getLocationsList()[0] + "'");
  await tick();
  show();
  if (script[0] && script[0].startsWith('random:')) {
    const steps = parseInt(script[0].split(':')[1]); const seen = new Set(); let errs = 0, n = 0;
    errors = [];
    for (; n < steps; n++) {
      const links = [...main.matchAll(/EXEC:([^"]*)"/g)].map(m => ({ kind: 'exec', code: m[1] }));
      const opts = actions.map((a, i) => ({ kind: 'act', i })).concat(links).concat(objects.map((o, i) => ({ kind: 'obj', i })));
      if (!opts.length) break;
      const o = opts[Math.floor(Math.random() * opts.length)];
      if (o.kind === 'act') { api.selectAction(o.i); api.execSelectedAction(); }
      else if (o.kind === 'exec') api.execCode(o.code);
      else api.selectObject(o.i);
      await tick();
      seen.add(main.replace(/<[^>]+>/g, '').slice(0, 30));
      if (errors.length) { errs++; if (errs <= 3) console.log('ОШИБКА: ' + errors.join(' | ')); errors = []; }
    }
    console.log('ПОСЛЕДНИЙ: ' + main.replace(/\r?\n/g, '|').slice(0, 600) + ' ACTIONS ' + JSON.stringify(actions.map(a => a.name)) + ' CURLOC ' + api.calculateStringExpression('$CURLOC'));
    console.log('ходов: ' + n + ', разных экранов: ' + seen.size + ', ошибок: ' + errs + ', инвентарь: ' + JSON.stringify(objects.map(o => o.name)));
    return;
  }
  for (const step of script) {
    if (step.startsWith('obj:')) {
      const i = objects.findIndex(o => o.name === step.slice(4));
      if (i < 0) { console.log('нет предмета ' + step); break; }
      api.selectObject(i);
    } else if (step.startsWith('link:')) {
      const links = [...main.matchAll(/EXEC:([^"]*)"/g)].map(m => m[1]);
      api.execCode(links[parseInt(step.slice(5)) - 1]);
    } else if (step.startsWith('exec:')) {
      api.execCode(step.slice(5));
    } else {
      const i = actions.findIndex(a => a.name === step);
      if (i < 0) { console.log('нет действия ' + step); break; }
      api.selectAction(i); api.execSelectedAction();
    }
    await tick();
    show(step);
  }
  const vars = {};
  for (const v of (process.env.VARS || '').split(',').filter(x => x)) vars[v] = api.readVariable(v);
  if (Object.keys(vars).length) console.log('VARS: ' + JSON.stringify(vars));
})().catch(e => { console.error('FAIL', e); process.exit(1); });
