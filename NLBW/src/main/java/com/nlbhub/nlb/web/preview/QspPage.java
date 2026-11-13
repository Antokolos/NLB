/**
 * @(#)QspPage.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.web.preview;

/**
 * Страница игры QSP для проверки экспорта: движок QSP (@qsp/wasm-engine, WebAssembly) и
 * преобразование текста QSP в игру (@qsp/converters: readQsps + writeQsp) — как в веб-плеере
 * qSpider, но без его оболочки. Экран — основное описание (USEHTML, ссылки EXEC), дополнительное
 * описание (stats), действия, инвентарь; окна MSG, MENU, INPUT, VIEW; звук (PLAY/CLOSE), таймер
 * (SETTIMER — счётчик), WAIT. Ошибки движка — внизу страницы, с локацией и строкой.
 * Игра начинается с первой локации (как qSpider: restartGame движка её не выполняет).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
final class QspPage {
    private QspPage() {
    }

    static String html(String book) {
        String bookJs = book.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3c");
        String title = book.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return PAGE.replace("@TITLE@", title).replace("@BOOK@", bookJs);
    }

    private static final String PAGE = """
            <!doctype html>
            <html><head><meta charset='utf-8'><title>@TITLE@ — QSP</title>
            <meta name='viewport' content='width=device-width, initial-scale=1'>
            <style>
            body{font-family:Georgia,serif;margin:0;background:#faf8f2;color:#222}
            #top{padding:.4em 1em;font:14px sans-serif;background:#eee8d8;border-bottom:1px solid #d8d0b8}
            #top a,#top button{margin-right:1em}
            #wrap{display:flex;gap:1em;max-width:72em;margin:1em auto;padding:0 1em}
            #left{flex:3;min-width:0}#right{flex:1;min-width:12em}
            #main{line-height:1.5;white-space:pre-wrap}#main img{max-width:100%}
            #stats{line-height:1.4;white-space:pre-wrap;margin-bottom:1em}
            h3{font:bold 13px sans-serif;color:#776;margin:.6em 0 .3em;text-transform:uppercase}
            #actions button{display:block;width:100%;text-align:left;margin:.3em 0;padding:.4em .8em;font:inherit;
              border:1px solid #886;border-radius:.4em;background:#fff;cursor:pointer}
            #actions button img,#objects img{max-height:2em;vertical-align:middle;margin-right:.4em}
            #objects div{padding:.3em .5em;cursor:pointer;border-radius:.3em}
            #objects div:hover{background:#efe9d9}#objects div.sel{background:#e2d8bd}
            #errors{color:#a00;white-space:pre-wrap;font:13px monospace;padding:0 1em}
            .modal{position:fixed;inset:0;background:rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center}
            .modal>div{background:#fff;max-width:40em;max-height:80vh;overflow:auto;padding:1em 1.4em;border-radius:.5em;white-space:pre-wrap}
            .modal button{margin-top:.8em;font:inherit}.modal img{max-width:80vw;max-height:70vh}
            .menu div{padding:.3em .6em;cursor:pointer}.menu div:hover{background:#efe9d9}
            .hidden{display:none}
            </style></head><body>
            <div id='top'><a href='../'>← к списку</a><button id='restart'>Начать заново</button><span id='state'>Загрузка движка QSP…</span></div>
            <div id='wrap'><div id='left'><div id='main'></div><h3 id='acth' class='hidden'>Действия</h3><div id='actions'></div></div>
            <div id='right'><div id='stats'></div><h3 id='objh' class='hidden'>Инвентарь</h3><div id='objects'></div></div></div>
            <div id='errors'></div>
            <script type='module'>
            import { initQspEngine } from './runtime/wasm-engine/dist/wasm-engine.modern.js';
            import { readQsps, writeQsp } from './runtime/converters/dist/index.js';
            const book = '@BOOK@';
            const $ = (id) => document.getElementById(id);
            const error = (text) => { $('errors').append(document.createTextNode(text + '\\n')); };
            window.addEventListener('error', (e) => error(e.message));
            window.addEventListener('unhandledrejection', (e) => error(String(e.reason)));
            function modal(build) {
              const back = document.createElement('div'); back.className = 'modal';
              const box = document.createElement('div'); back.append(box); document.body.append(back);
              build(box, () => back.remove());
            }
            function fill(id, items, make) {
              const el = $(id); el.textContent = '';
              items.forEach((item, i) => el.append(make(item, i)));
            }
            function withImage(el, item) {
              if (item.image) { const img = document.createElement('img'); img.src = item.image; el.append(img); }
              const span = document.createElement('span'); span.innerHTML = item.name; el.append(span);
              return el;
            }
            async function load() {
              const response = await fetch(encodeURIComponent(book));
              if (!response.ok) throw new Error(book + ': HTTP ' + response.status);
              const data = await response.arrayBuffer();
              if (/[.](qsp|gam)$/i.test(book)) return data;
              // экспорт NLB — текст QSP в UTF-16LE (как txt2gam); в игру — как qSpider
              const text = new TextDecoder('utf-16le').decode(data).replace(/^\\uFEFF/, '');
              return writeQsp(readQsps(text));
            }
            const wasm = await (await fetch('runtime/wasm-engine/dist/qsp-engine.wasm')).arrayBuffer();
            const api = await initQspEngine(wasm);
            const sounds = new Map();
            let timer = null, timerMs = 500;
            const setTimer = (ms) => {
              if (timer) clearInterval(timer);
              timerMs = ms; timer = ms > 0 ? setInterval(() => api.execCounter(), ms) : null;
            };
            api.on('main_changed', (text) => { $('main').innerHTML = text; });
            api.on('stats_changed', (text) => { $('stats').innerHTML = text; });
            api.on('actions_changed', (actions) => {
              $('acth').classList.toggle('hidden', actions.length === 0);
              fill('actions', actions, (a, i) => {
                const b = withImage(document.createElement('button'), a);
                b.onclick = () => { api.selectAction(i); api.execSelectedAction(); };
                return b;
              });
            });
            api.on('objects_changed', (objects) => {
              $('objh').classList.toggle('hidden', objects.length === 0);
              const selected = api.getSelectedObjectIndex();
              fill('objects', objects, (o, i) => {
                const d = withImage(document.createElement('div'), o);
                if (i === selected) d.className = 'sel';
                d.onclick = () => api.selectObject(i);
                return d;
              });
            });
            api.on('panel_visibility', (type, shown) => {
              const ids = { 2: ['stats'], 4: ['actions', 'acth'], 8: ['objects', 'objh'] }[type] || [];
              ids.forEach((id) => { $(id).style.display = shown ? '' : 'none'; });
            });
            api.on('error', (e) => error('Ошибка QSP ' + e.errorCode + ': ' + e.description + ' — локация ' + e.location
              + ', строка ' + e.line + (e.lineSrc ? ': ' + e.lineSrc : '')));
            api.on('msg', (text, closed) => modal((box, close) => {
              box.innerHTML = text; const ok = document.createElement('button'); ok.textContent = 'OK';
              ok.onclick = () => { close(); closed(); }; box.append(ok); ok.focus();
            }));
            api.on('menu', (items, select) => modal((box, close) => {
              box.className = 'menu';
              items.forEach((item, i) => {
                const d = withImage(document.createElement('div'), item);
                d.onclick = () => { close(); select(i); }; box.append(d);
              });
              const cancel = document.createElement('button'); cancel.textContent = 'Отмена';
              cancel.onclick = () => { close(); select(-1); }; box.append(cancel);
            }));
            api.on('input', (text, onInput) => onInput(window.prompt(text.replace(/<[^>]*>/g, '')) || ''));
            api.on('view', (path) => { if (path) modal((box, close) => {
              const img = document.createElement('img'); img.src = path; box.append(img);
              const ok = document.createElement('button'); ok.textContent = 'OK'; ok.onclick = close; box.append(ok);
            }); });
            api.on('wait', (ms, done) => setTimeout(done, ms));
            api.on('timer', (ms) => setTimer(ms));
            api.on('version', (type, done) => done('NLB export preview'));
            api.on('system_cmd', () => {});
            api.on('user_input', () => {});
            api.on('open_game', (path, isNew, done) => done());
            api.on('save_game', (path, done) => done());
            api.on('load_save', (path, done) => done());
            api.on('play_file', (path, volume, done) => {
              let audio = sounds.get(path);
              if (!audio) { audio = new Audio(path); sounds.set(path, audio); }
              audio.volume = Math.max(0, Math.min(1, (volume || 100) / 100));
              audio.currentTime = 0;
              audio.play().catch(() => {});
              done();
            });
            api.on('close_file', (path, done) => {
              for (const [key, audio] of sounds) if (!path || key === path) audio.pause();
              done();
            });
            api.on('is_play', (path, done) => { const a = sounds.get(path); done(!!a && !a.paused && !a.ended); });
            const game = await load();
            function start() {
              $('errors').textContent = '';
              for (const audio of sounds.values()) audio.pause();
              setTimer(0);
              api.openGame(game.slice ? game.slice(0) : game, true);
              const first = api.getLocationsList()[0];
              if (first) api.execCode("GOTO '" + first.replace(/'/g, "''") + "'");
            }
            // ссылки EXEC: в тексте — код QSP
            document.addEventListener('click', (e) => {
              const a = e.target.closest('a');
              const href = a && a.getAttribute('href');
              if (href && /^exec:/i.test(href)) { e.preventDefault(); api.execCode(href.slice(5)); }
            });
            $('restart').onclick = start;
            $('state').textContent = 'QSP ' + api.version();
            start();
            </script>
            </body></html>
            """;
}
