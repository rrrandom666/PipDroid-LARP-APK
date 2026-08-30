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
    r                RADIOPWR:1 (переключает экран на RADIO, как физический тумблер на ESP32)
    R (Shift+r)      RADIOPWR:0 (остаётся на текущем экране, обновляет только статус-строку —
                     протокол, раздел 3.2)
    c                свободный ввод "КЛЮЧ:ЗНАЧЕНИЕ" (Enter — отправить, пустая строка — отмена)
    q  или  Ctrl+C   выход

    Реальное радио (протокол, разделы 3.2/3.3, roadmap этап 23) — RADIOFREQ шлётся как
    абсолютное значение (МГц×10, как реально сделал бы ESP32 после тюнинга), VOLUME — только
    дельтой (у ESP32 нет отправки абсолютной громкости, см. CLAUDE.md/протокол):
    [                RADIOFREQ на шаг вниз (-0.1 МГц)
    ]                RADIOFREQ на шаг вверх (+0.1 МГц)
    ,                VOLUME:-1
    .                VOLUME:+1

    Гейгер (протокол, раздел 3.4) — симулятор "нахождения в сети": выбранная клавишей
    сеть остаётся активной, пока не выбрана другая. Пока активна сеть с ненулевой дозой,
    раз в секунду уходит GEIGER:<рад/сек> — так же, как реальная прошивка (main.py,
    GEIGER_DOSE_SEND_INTERVAL_MS). Вне сети (0) ничего не шлётся вообще — не GEIGER:0, а
    просто тишина: отправка нулей признана бессмысленной (наличие/отсутствие BLE-связи
    и так отслеживается отдельно, не по этому потоку). Переключение — не тумблер
    "вкл/выкл на месте", а выбор одной из пяти взаимоисключающих ситуаций:
    0                вне зоны действия любой сети (радиации нет) — переход в тишину
    5                в сети R10  — шлёт GEIGER:5  (5 рад/сек)
    6                в сети R20  — шлёт GEIGER:10 (10 рад/сек)
    7                в сети R50  — шлёт GEIGER:25 (25 рад/сек)
    8                в сети R100 — шлёт GEIGER:50 (50 рад/сек, смертельная доза за 20 сек)

    Все команды идут через "adb -s <серийник>" на автоматически определённое устройство —
    если подключено больше одного (например, забыли отключить USB после настройки adb по
    Wi-Fi), скрипт откажется стартовать, а не будет молча ронять каждую команду с
    "error: more than one device/emulator".
"""
from __future__ import annotations

import shlex
import subprocess
import sys
import termios
import threading
import tty

PACKAGE = "com.malto4.pipdroid"
ACTION = "com.malto4.pipdroid.DEBUG_BLE_COMMAND"
EXTRA_RAW = "raw"

# Имя сети -> доза, рад/сек (протокол, раздел 3.4; те же числа, что в GEIGER_BEACON_RATES
# в ESP32_S3_Mini_PythonFiles/main.py — если ставки поменяются там, поправить и тут).
GEIGER_NETWORKS = {
    "0": (None, 0),
    "5": ("R10", 5),
    "6": ("R20", 10),
    "7": ("R50", 25),
    "8": ("R100", 50),
}

# Реальное радио (roadmap, этап 23) — FM-диапазон 87.5-108.0 МГц, значение в МГц×10
# (протокол, разделы 3.2/3.3), шаг 1 = 0.1 МГц — тот же шаг, что типично даёт RDA5807M.
RADIO_FREQ_MIN = 875
RADIO_FREQ_MAX = 1080
RADIO_FREQ_STEP = 1
radio_freq = 998  # 99.8 МГц — тот же пример, что в протоколе (RADIOFREQ:998)

geiger_lock = threading.Lock()
geiger_rate = 0
geiger_wake = threading.Event()
geiger_stop = threading.Event()

DEVICE_SERIAL: str | None = None


def resolve_device_serial() -> str:
    """Ровно одно устройство должно быть видно adb — иначе каждая команда ниже молча
    (для человека, не смотрящего в консоль) падает с "error: more than one device", а
    приложение просто никогда не получает GEIGER/ENC/итд. Так уже случалось: забытый
    подключённым USB-кабель поверх настроенного adb по Wi-Fi."""
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    ready = [
        parts[0]
        for line in result.stdout.splitlines()[1:]
        if line.strip() and len(parts := line.split()) >= 2 and parts[1] == "device"
    ]
    if len(ready) == 1:
        return ready[0]
    if not ready:
        print("adb не видит ни одного устройства (`adb devices` пуст).")
        print("Подключите телефон (USB или Wi-Fi adb) и запустите заново.")
    else:
        print("adb видит несколько устройств одновременно — неясно, куда слать команды:")
        for serial in ready:
            print(f"  {serial}")
        print("Отключите лишнее (например, USB-кабель, если уже настроен adb по Wi-Fi)")
        print("и запустите заново.")
    sys.exit(1)


def send(raw: str) -> None:
    cmd = (
        f"am broadcast -p {PACKAGE} -a {ACTION} --es {EXTRA_RAW} {shlex.quote(raw)}"
    )
    result = subprocess.run(
        ["adb", "-s", DEVICE_SERIAL, "shell", cmd], capture_output=True, text=True
    )
    status = "OK" if result.returncode == 0 else f"ОШИБКА ({result.returncode})"
    print(f"  -> {raw:<20} [{status}]")
    if result.returncode != 0 and result.stderr.strip():
        print(f"     {result.stderr.strip()}")


def geiger_sender_loop() -> None:
    """Фоновый поток — пока активна сеть с ненулевой дозой, раз в секунду шлёт
    GEIGER:<текущая ставка>. При дозе 0 (вне сети) не шлёт вообще ничего — молчание,
    не GEIGER:0, см. докстринг модуля. wake позволяет применить смену сети немедленно,
    не дожидаясь конца текущей секунды ожидания."""
    while not geiger_stop.is_set():
        with geiger_lock:
            rate = geiger_rate
        if rate > 0:
            send(f"GEIGER:{rate}")
        geiger_wake.wait(timeout=1.0)
        geiger_wake.clear()


def set_geiger_network(name: str | None, rate: int) -> None:
    global geiger_rate
    with geiger_lock:
        geiger_rate = rate
    label = "вне зоны действия сети (0 рад/сек)" if name is None else f"в сети {name} ({rate} рад/сек)"
    print(f"  == Гейгер: {label} ==")
    geiger_wake.set()


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
    global DEVICE_SERIAL, radio_freq
    print(__doc__)
    DEVICE_SERIAL = resolve_device_serial()
    print(f"Устройство: {DEVICE_SERIAL}")
    print("Готово. Ожидаю нажатий... (Гейгер по умолчанию вне зоны действия сети)\n")

    sender = threading.Thread(target=geiger_sender_loop, daemon=True)
    sender.start()

    try:
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
            elif key == "[":
                radio_freq = max(RADIO_FREQ_MIN, radio_freq - RADIO_FREQ_STEP)
                send(f"RADIOFREQ:{radio_freq}")
            elif key == "]":
                radio_freq = min(RADIO_FREQ_MAX, radio_freq + RADIO_FREQ_STEP)
                send(f"RADIOFREQ:{radio_freq}")
            elif key == ",":
                send("VOLUME:-1")
            elif key == ".":
                send("VOLUME:+1")
            elif key in GEIGER_NETWORKS:
                set_geiger_network(*GEIGER_NETWORKS[key])
            elif key == "c":
                print("  Свободная команда (КЛЮЧ:ЗНАЧЕНИЕ), Enter — отправить, пусто — отмена:")
                raw = read_line_cooked().strip()
                if raw:
                    send(raw)
            else:
                continue
    finally:
        geiger_stop.set()
        geiger_wake.set()
        sender.join(timeout=2.0)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nВыход.")
