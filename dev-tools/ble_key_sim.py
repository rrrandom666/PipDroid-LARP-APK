#!/usr/bin/env python3
"""
Отладочная эмуляция периферии (кнопки/энкодер/POWER/Гейгер) с клавиатуры компьютера,
без реального ESP32 и вообще без BLE (roadmap, этап 7, "быстрая отладка логики экранов").

Требует debug-сборку PipDroid на подключённом телефоне (или эмуляторе — этот путь не
использует BLE вообще, поэтому эмулятор тоже подходит) и adb в PATH. Каждое нажатие
клавиши шлёт adb-broadcast прямо в MainActivity.registerDebugCommandReceiver(), которая
пускает строку в тот же handleBleCommand(), что и реальные команды от ESP32.

Запуск:
    python3 dev-tools/ble_key_sim.py

Клавиши:
    1 / 2 / 3 / 4   STATS / ITEMS / DATA / RADIO
    h  или  ←        ENC:-1 (энкодер влево)
    l  или  →        ENC:+1 (энкодер вправо)
    Enter / Space    ENCBTN (нажатие энкодера)
    p                POWER:1
    o                POWER:0
    g                GEIGER — по кругу 0 → 64 → 128 → 192 → 255 → 0 ...
    r                RADIOPWR:1
    R (Shift+r)      RADIOPWR:0 (сейчас no-op в handleBleCommand() — обновление статуса
                     радио на экране ещё не реализовано, roadmap этап 7; клавиша тут
                     на будущее и для проверки, что это действительно ничего не делает)
    c                свободный ввод "КЛЮЧ:ЗНАЧЕНИЕ" (Enter — отправить, пустая строка — отмена)
    q  или  Ctrl+C   выход
"""
import shlex
import subprocess
import sys
import termios
import tty

PACKAGE = "com.malto4.pipdroid"
ACTION = "com.malto4.pipdroid.DEBUG_BLE_COMMAND"
EXTRA_RAW = "raw"

GEIGER_LEVELS = [0, 64, 128, 192, 255]


def send(raw: str) -> None:
    cmd = (
        f"am broadcast -p {PACKAGE} -a {ACTION} --es {EXTRA_RAW} {shlex.quote(raw)}"
    )
    result = subprocess.run(
        ["adb", "shell", cmd], capture_output=True, text=True
    )
    status = "OK" if result.returncode == 0 else f"ОШИБКА ({result.returncode})"
    print(f"  -> {raw:<20} [{status}]")
    if result.returncode != 0 and result.stderr.strip():
        print(f"     {result.stderr.strip()}")


def read_key() -> str:
    """Читает одно нажатие без Enter; распознаёт стрелки как escape-последовательность."""
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        ch = sys.stdin.read(1)
        if ch == "\x1b":
            rest = sys.stdin.read(2)
            return {"[A": "UP", "[B": "DOWN", "[C": "RIGHT", "[D": "LEFT"}.get(
                rest, "ESC"
            )
        return ch
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)


def read_line_cooked() -> str:
    """Временно возвращает обычный построчный ввод — для свободной команды через 'c'."""
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)
        return input()
    finally:
        pass


def main() -> None:
    geiger_index = 0
    print(__doc__)
    print("Готово. Ожидаю нажатий...\n")
    while True:
        key = read_key()
        if key in ("q", "\x03"):
            print("Выход.")
            return
        if key in ("1",):
            send("STATS")
        elif key in ("2",):
            send("ITEMS")
        elif key in ("3",):
            send("DATA")
        elif key in ("4",):
            send("RADIO")
        elif key in ("h", "LEFT"):
            send("ENC:-1")
        elif key in ("l", "RIGHT"):
            send("ENC:+1")
        elif key in ("\r", "\n", " "):
            send("ENCBTN")
        elif key == "p":
            send("POWER:1")
        elif key == "o":
            send("POWER:0")
        elif key == "r":
            send("RADIOPWR:1")
        elif key == "R":
            send("RADIOPWR:0")
        elif key == "g":
            send(f"GEIGER:{GEIGER_LEVELS[geiger_index]}")
            geiger_index = (geiger_index + 1) % len(GEIGER_LEVELS)
        elif key == "c":
            print("  Свободная команда (КЛЮЧ:ЗНАЧЕНИЕ), Enter — отправить, пусто — отмена:")
            raw = read_line_cooked().strip()
            if raw:
                send(raw)
        else:
            continue


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nВыход.")
