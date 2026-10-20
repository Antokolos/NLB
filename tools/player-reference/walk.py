#!/usr/bin/env python3
"""Случайное прохождение книги в консольном плеере NLBNavigator (nlb_main).

Скрипт играет за игрока: читает экран, выбирает случайное действие (переход, объект на сцене,
предмет инвентаря, применение предмета, ожидание) и записывает ввод в файл. Журнал прохождения
пишет сам nlb_main (--log). Пара «ввод + журнал» — эталон для сверки Java-плеера: Java-плеер
получает тот же ввод и должен выдать тот же журнал.

Выбор действий зависит только от зерна (--seed) и экрана, поэтому при той же книге и том же
nlb_main прохождение воспроизводится.

Сценарий (--script) задаёт начало прохождения — для книг, где случайный игрок не доходит до
нужных мест. Строки сценария — по тексту, а не по номерам (номера вариантов меняются):
    go Холл              переход по варианту с этим текстом
    obj кнопка           щелчок по объекту сцены, имя которого начинается с этого текста
    inv Задания          щелчок по предмету инвентаря
    use Ключ > дверь     применить предмет к объекту сцены или предмету инвентаря
    obj кран #2          второй из объектов с таким именем (то же для inv и use)
    o2, 1, w, ...        команда nlb_main как есть
    # комментарий
Если действия сценария на экране нет, но есть вариант «Далее», нажимается «Далее», а строка
сценария ждёт следующего экрана. После сценария игра продолжается случайно.
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
# Объекты и предметы: по одному на строке (вложенные — с отступом) или в одну строку
# после «Здесь:» / «Инвентарь:» («Здесь:  o1. кровать  o2. стол»)
RE_SCENE = re.compile(r"(?<!\S)o(\d+)\. ")
RE_INVENTORY = re.compile(r"(?<!\S)i(\d+)\. ")
RE_WAIT = re.compile(r"^w\. ", re.M)
RE_ITEM_TAIL = re.compile(r"\s+(\[[^\]]*\]|\(можно взять\))\s*$")
NEXT_TEXTS = ("Далее", "Next")


class Player:
    def __init__(self, args, home):
        # --deterministic: случайность книги (RND, SHUFFLE) воспроизводима — k-й rnd(n) даёт
        # (k mod n) + 1, SHUFFLE разворачивает список; так же делает Java-плеер в режиме сверки
        cmd = [args.player, args.book, "explore", args.mode, "--log", args.log, "--no-sound",
               "--deterministic"]
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


def named_items(line, regex):
    """Номера и имена объектов в строке: «o1. кровать  o2. стол [left-top +1,2]»."""
    matches = list(regex.finditer(line))
    result = []
    for k, m in enumerate(matches):
        end = matches[k + 1].start() if k + 1 < len(matches) else len(line)
        name = line[m.end():end].strip()
        while RE_ITEM_TAIL.search(name):
            name = RE_ITEM_TAIL.sub("", name)
        result.append((m.group(1), name))
    return result


def parse_screen(screen):
    """Варианты, объекты сцены и инвентарь экрана: списки пар (номер, текст)."""
    choices = [(m.group(1), screen[m.end():].split("\n", 1)[0].strip())
               for m in RE_CHOICE.finditer(screen)]
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
        if section == "scene":
            scene += named_items(line, RE_SCENE)
        elif section == "inventory":
            inventory += named_items(line, RE_INVENTORY)
    return choices, scene, inventory


def resolve_script_line(line, screen):
    """Команда nlb_main для строки сценария на этом экране; None — действия на экране нет."""
    choices, scene, inventory = parse_screen(screen)
    verb, _, arg = line.partition(" ")
    arg = arg.strip()

    def find(items, name):
        # «кран #2» — второй из подходящих по имени
        name, _, ordinal = name.partition(" #")
        matches = [number for number, text in items if text.startswith(name.strip())]
        index = int(ordinal) - 1 if ordinal else 0
        return matches[index] if index < len(matches) else None

    if verb == "go":
        for number, text in choices:
            if text == arg:
                return number
        return None
    if verb == "obj":
        number = find(scene, arg)
        return None if number is None else "o" + number
    if verb == "inv":
        number = find(inventory, arg)
        return None if number is None else "i" + number
    if verb == "use":
        source, _, target = arg.partition(">")
        source_number = find(inventory, source.strip())
        target_number = find(scene, target.strip())
        target_ref = None if target_number is None else "o" + target_number
        if target_ref is None:
            target_number = find(inventory, target.strip())
            target_ref = None if target_number is None else "i" + target_number
        if source_number is None or target_ref is None:
            return None
        return "u%s %s" % (source_number, target_ref)
    return line


def next_choice(screen):
    for number, text in parse_screen(screen)[0]:
        if text in NEXT_TEXTS:
            return number
    return None


def read_script(path):
    lines = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                lines.append(line)
    return lines


def screen_actions(screen):
    """Действия, доступные на экране (последнее меню в выводе)."""
    choices, scene, inventory = parse_screen(screen)
    choices = [number for number, _ in choices]
    scene = [number for number, _ in scene]
    inventory = [number for number, _ in inventory]
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


# Вес действий: переходы двигают сюжет, остальное — чтобы задеть объекты и инвентарь;
# ожидание — чтобы на страницах с таймером он успевал тикать
WEIGHTS = {"choice": 6.0, "scene": 2.0, "inventory": 1.0, "use": 1.0, "wait": 3.0}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--player", required=True, help="путь к nlb_main")
    parser.add_argument("--book", required=True, help="каталог книги")
    parser.add_argument("--mode", default="vn", choices=["vn", "standard"])
    parser.add_argument("--config", help="nlb.json (настройки экспорта)")
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--max-steps", type=int, default=200, help="случайных шагов после сценария")
    parser.add_argument("--log", required=True, help="журнал прохождения (пишет nlb_main)")
    parser.add_argument("--input", required=True, help="файл для записи ввода")
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--script", help="сценарий начала прохождения (см. выше)")
    args = parser.parse_args()
    args.log = os.path.abspath(args.log)
    args.book = os.path.abspath(args.book)
    if args.config:
        args.config = os.path.abspath(args.config)

    rng = random.Random(args.seed)
    script = read_script(args.script) if args.script else []
    inputs = []
    with tempfile.TemporaryDirectory() as home:
        player = Player(args, home)
        steps = 0  # случайные шаги (после сценария)
        scripted = 0
        while True:
            screen, prompt = player.read_screen(args.timeout)
            if prompt is None:
                break
            if prompt == CONFIRM_PROMPT:
                line = "1"  # подтверждаем выход
            elif END_MARK in screen:
                if script:
                    player.proc.kill()
                    sys.exit("Игра кончилась раньше сценария, осталось: %s" % script[0])
                line = "0"
            elif script:
                line = resolve_script_line(script[0], screen)
                if line is not None:
                    script.pop(0)
                else:
                    line = next_choice(screen)
                    if line is None:
                        player.proc.kill()
                        sys.exit("Действие сценария «%s» не найдено на экране:\n%s"
                                 % (script[0], screen[-1500:]))
                scripted += 1
            elif steps >= args.max_steps:
                line = "0"
            else:
                actions = screen_actions(screen)
                if not actions:
                    line = "0"
                else:
                    # Сначала вид действия по весу, затем действие этого вида: иначе применений
                    # (каждый предмет к каждой цели) было бы больше, чем переходов
                    kinds = sorted({kind for kind, _ in actions}, key=list(WEIGHTS).index)
                    kind = rng.choices(kinds, weights=[WEIGHTS[k] for k in kinds])[0]
                    line = rng.choice([cmd for k, cmd in actions if k == kind])
                    steps += 1
            inputs.append(line)
            player.send(line)
        player.proc.wait(timeout=args.timeout)
    with open(args.input, "w", encoding="utf-8") as f:
        f.write("\n".join(inputs) + "\n")
    print("%s: %d шагов по сценарию, %d случайных, ввод: %s, журнал: %s"
          % (os.path.basename(args.book), scripted, steps, args.input, args.log))
    return 0


if __name__ == "__main__":
    sys.exit(main())
