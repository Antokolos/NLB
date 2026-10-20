#!/usr/bin/env python3
"""Случайное прохождение книги в консольном плеере NLBNavigator (nlb_main).

Скрипт играет за игрока: читает экран, выбирает случайное действие (переход, объект на сцене,
предмет инвентаря, применение предмета, ожидание) и записывает ввод в файл. Журнал прохождения
пишет сам nlb_main (--log). Пара «ввод + журнал» — эталон для сверки Java-плеера: Java-плеер
получает тот же ввод и должен выдать тот же журнал.

Выбор действий зависит только от зерна (--seed) и экрана, поэтому при той же книге и том же
nlb_main прохождение воспроизводится.
"""
import argparse
import os
import random
import re
import select
import subprocess
import sys
import tempfile

CHOICE_PROMPT = "Ваш выбор: "
CONFIRM_PROMPT = "нет: "
END_MARK = "=== КОНЕЦ ==="

RE_CHOICE = re.compile(r"^(\d+)\. ", re.M)
RE_SCENE = re.compile(r"^\s*o(\d+)\. ", re.M)
RE_INVENTORY = re.compile(r"^\s*i(\d+)\. ", re.M)
RE_WAIT = re.compile(r"^w\. ", re.M)


class Player:
    def __init__(self, args, home):
        cmd = [args.player, args.book, "explore", args.mode, "--log", args.log, "--no-sound"]
        if args.config:
            cmd += ["--config", args.config]
        env = dict(os.environ, HOME=home)
        self.proc = subprocess.Popen(
            cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            cwd=home, env=env,
        )
        self.buffer = b""

    def read_screen(self, timeout):
        """Читает вывод до приглашения; возвращает (текст, приглашение) или (текст, None) при выходе."""
        while True:
            text = self.buffer.decode("utf-8", errors="replace")
            for prompt in (CHOICE_PROMPT, CONFIRM_PROMPT):
                if text.endswith(prompt):
                    self.buffer = b""
                    return text, prompt
            ready, _, _ = select.select([self.proc.stdout], [], [], timeout)
            if not ready:
                raise TimeoutError("nlb_main не ответил за %s с" % timeout)
            chunk = os.read(self.proc.stdout.fileno(), 65536)
            if not chunk:
                self.buffer = b""
                return text, None
            self.buffer += chunk

    def send(self, line):
        self.proc.stdin.write((line + "\n").encode("utf-8"))
        self.proc.stdin.flush()


def screen_actions(screen):
    """Действия, доступные на экране (последнее меню в выводе)."""
    # Берём только последний экран: от последней пустой строки перед меню
    choices = RE_CHOICE.findall(screen)
    scene = []
    inventory = []
    section = None
    for line in screen.splitlines():
        if line.startswith("Здесь:"):
            section = "scene"
        elif line.startswith("Инвентарь:"):
            section = "inventory"
        elif not line.startswith(" "):
            section = None
        m = RE_SCENE.match(line)
        if m and section == "scene":
            scene.append(m.group(1))
        m = RE_INVENTORY.match(line)
        if m and section == "inventory":
            inventory.append(m.group(1))
    actions = []
    actions += [("choice", c) for c in choices]
    actions += [("scene", "o" + o) for o in scene]
    actions += [("inventory", "i" + i) for i in inventory]
    for i in inventory:
        for target in ["o" + o for o in scene] + ["i" + j for j in inventory if j != i]:
            actions.append(("use", "u%s %s" % (i, target)))
    if RE_WAIT.search(screen):
        actions.append(("wait", "w"))
    return actions


# Вес действий: переходы двигают сюжет, остальное — чтобы задеть объекты и инвентарь
WEIGHTS = {"choice": 6.0, "scene": 2.0, "inventory": 1.0, "use": 1.0, "wait": 1.0}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--player", required=True, help="путь к nlb_main")
    parser.add_argument("--book", required=True, help="каталог книги")
    parser.add_argument("--mode", default="vn", choices=["vn", "standard"])
    parser.add_argument("--config", help="nlb.json (настройки экспорта)")
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--max-steps", type=int, default=200)
    parser.add_argument("--log", required=True, help="журнал прохождения (пишет nlb_main)")
    parser.add_argument("--input", required=True, help="файл для записи ввода")
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()
    args.log = os.path.abspath(args.log)
    args.book = os.path.abspath(args.book)
    if args.config:
        args.config = os.path.abspath(args.config)

    rng = random.Random(args.seed)
    inputs = []
    with tempfile.TemporaryDirectory() as home:
        player = Player(args, home)
        steps = 0
        while True:
            screen, prompt = player.read_screen(args.timeout)
            if prompt is None:
                break
            if prompt == CONFIRM_PROMPT:
                line = "1"  # подтверждаем выход
            elif END_MARK in screen or steps >= args.max_steps:
                line = "0"
            else:
                actions = screen_actions(screen)
                if not actions:
                    line = "0"
                else:
                    weights = [WEIGHTS[kind] for kind, _ in actions]
                    line = rng.choices(actions, weights=weights)[0][1]
                    steps += 1
            inputs.append(line)
            player.send(line)
        player.proc.wait(timeout=args.timeout)
    with open(args.input, "w", encoding="utf-8") as f:
        f.write("\n".join(inputs) + "\n")
    print("%s: %d шагов, ввод: %s, журнал: %s" % (os.path.basename(args.book), steps, args.input, args.log))
    return 0


if __name__ == "__main__":
    sys.exit(main())
