// Прогон экспорта QSP в настоящем движке QSP (WebAssembly-сборка qspfoundation, @qsp/wasm-engine)
// в node; текст -> .qsp — @qsp/converters (readQsps/writeQsp), как в веб-плеере qSpider.
//
// Пакеты (в отдельный каталог <пакеты>):
//   npm pack @qsp/wasm-engine@1.6.1 && tar xzf qsp-wasm-engine-1.6.1.tgz            -> <пакеты>/package
//   npm pack @qsp/converters@2.0.0 && mkdir conv && tar xzf qsp-converters-2.0.0.tgz -C conv
//                                                                                     -> <пакеты>/conv/package
// Запуск:
//   node qsp-run.js <абсолютный путь к пакетам> <book.txt (UTF-16LE) или game.qsp> "<шаги через ;;>"
// Файл .qsp (.gam) играется как есть — так проверяется результат txt2gam:
//   txt2gam book.txt game.qsp && node qsp-run.js <пакеты> game.qsp "random:300"
// (txt2gam — github.com/QSPFoundation/txt2gam, сборка cmake; число локаций он печатает в конце)
// Шаги: подпись действия; obj:<подпись предмета> — выбрать предмет инвентаря; link:<N> — N-я
// ссылка на объект в тексте; o:<подпись> — ссылка на объект по подписи; exec:<код> — выполнить код QSP; random:<N> — N случайных шагов
// (действия, ссылки, предметы) с подсчётом ошибок движка. TAIL=1 — показывать конец текста,
// VARS=a,b — в конце вывести значения переменных; MEDIA=1 — картинки в тексте (IMG), звуки
// с прошлого шага (SND: PLAY/CLOSE) и картинки предметов инвентаря.
// DETERMINISTIC=1 (только для текста) — случайность как --deterministic Java-плеера
// (PlayerContext): RANDOM — счётчик (0, 1, 2… по модулю, +1), SHUFFLE — разворот списка; так
// прогоны со случайностью (колода в бою «Фронтира») сравнимы с плеером.
// restartGame движка первую локацию не выполняет, поэтому игра начинается с GOTO первой локации.
const fs = require('fs'), path = require('path');
const P = process.argv[2], file = process.argv[3], script = (process.argv[4] || '').split(';;').filter(x => x);
const { readQsps, writeQsp } = require(path.join(P, 'conv/package/dist/index.cjs'));

(async () => {
  const { initQspEngine } = await import('file://' + path.join(P, 'package/dist/wasm-engine.modern.js'));
  // готовый .qsp (например, от txt2gam) — как есть; текст — через @qsp/converters, как qSpider
  let source = /\.(qsp|gam)$/i.test(file) ? null : fs.readFileSync(file).toString('utf16le').replace(/^\uFEFF/, '');
  if (source !== null && process.env.DETERMINISTIC) source = deterministic(source);
  const game = source === null ? new Uint8Array(fs.readFileSync(file)) : writeQsp(readQsps(source));
  const wasm = fs.readFileSync(path.join(P, 'package/dist/qsp-engine.wasm'));
  const api = await initQspEngine(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
  let main = '', actions = [], objects = [], errors = [];
  api.on('main_changed', t => { main = t; });
  api.on('actions_changed', a => { actions = a; });
  api.on('objects_changed', o => { objects = o; });
  api.on('error', e => { errors.push(JSON.stringify(e)); });
  api.on('wait', (ms, done) => done());
  api.on('system_cmd', () => {});
  let sounds = [];
  api.on('play_file', (file, volume, done) => { sounds.push('PLAY ' + file); done(); });
  api.on('close_file', (file, done) => { sounds.push('CLOSE ' + (file || '*')); done(); });
  api.on('is_play', (file, done) => done(false));
  const show = (label) => {
    console.log((label ? '> ' + label + '\n' : '') + 'TEXT: ' + (process.env.TAIL ? '…' + main.replace(/<[^>]+>/g, '').replace(/\r?\n/g, '|').slice(-120) : main.replace(/\r?\n/g, '|').replace(/<(?!a )[^>]+>/g, '')) +
      '\nACTIONS: ' + JSON.stringify(actions.map(a => a.name)) + '\nINV: ' + JSON.stringify(objects.map(o => o.name)) +
      (process.env.MEDIA ? '\nIMG: ' + JSON.stringify([...main.matchAll(/<img src="([^"]*)"/g)].map(m => m[1]))
        + '\nSND: ' + JSON.stringify(sounds) + '\nINVIMG: ' + JSON.stringify(objects.map(o => o.image || '')) : '') +
      (errors.length ? '\nERRORS: ' + errors.join(' | ') : ''));
    errors = []; sounds = [];
  };
  const tick = () => new Promise(r => setTimeout(r, 50));
  api.openGame(game, true);
  console.log('LOCATIONS: ' + api.getLocationsList().length);
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
    } else if (step.startsWith('o:')) {
      // ссылка на объект в тексте по подписи
      // подпись — без разметки (в ссылке может быть картинка объекта)
      const m = [...main.matchAll(/<a href="EXEC:([^"]*)">(.*?)<\/a>/g)].find(x => x[2].replace(/<[^>]*>/g, '') === step.slice(2));
      if (!m) { console.log('нет ссылки ' + step); break; }
      api.execCode(m[1].replace(/''/g, "'"));
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

function deterministic(text) {
  const nl = text.includes('\r\n') ? '\r\n' : '\n';
  text = text.replace(/RAND\(1, ([^)]+)\)/g, "FUNC('nlb_detrnd', $1)");
  const start = text.indexOf('#nlb_lshuffle' + nl), end = text.indexOf(nl + '---', start);
  if (start < 0 || end < 0) throw new Error('DETERMINISTIC: нет локации nlb_lshuffle');
  const shuffle = ['#nlb_lshuffle', 'LOCAL nlb_k, nlb_j, $nlb_v', 'nlb_k = 0', 'nlb_j = nlb_ln[$ARGS[0]] - 1',
    'LOOP WHILE nlb_k < nlb_j:',
    "    $nlb_v = $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)]",
    "    $nlb_li[$ARGS[0] + ',' + $STR(nlb_k)] = $nlb_li[$ARGS[0] + ',' + $STR(nlb_j)]",
    "    $nlb_li[$ARGS[0] + ',' + $STR(nlb_j)] = $nlb_v",
    '    nlb_k = nlb_k + 1', '    nlb_j = nlb_j - 1', 'END'].join(nl);
  return text.slice(0, start) + shuffle + text.slice(end) + nl
    + ['#nlb_detrnd', 'RESULT = nlb_detc MOD ARGS[0] + 1', 'nlb_detc = nlb_detc + 1', '--- nlb_detrnd ---------------------------------', ''].join(nl);
}
