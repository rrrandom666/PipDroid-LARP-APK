# PipBoy ESP32-S3 firmware — BLE-мост (Nordic UART Service) + внешние контролы.
# Плата: ESP32-S3-DevKitC-1 (модуль WROOM-1). Полная карта GPIO и описание команд —
# см. PipBoy_BLE_Protocol_v0.2.md, раздел 4.0.

import bluetooth
import random
import time
import _thread
import network
import vars
from ble_advertising import advertising_payload
from machine import Pin, PWM, I2C

from micropython import const

_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)
_IRQ_GATTS_WRITE = const(3)

_FLAG_READ = const(0x0002)
_FLAG_WRITE_NO_RESPONSE = const(0x0004)
_FLAG_WRITE = const(0x0008)
_FLAG_NOTIFY = const(0x0010)

# --- Карта GPIO (WROOM-1, зафиксировано в PipBoy_BLE_Protocol_v0.2.md, раздел 4.0) ---
# Strapping-пины 0/3/45/46, USB D+/D- 19/20 и флеш-регион 26-32 — не использовать.

# Кнопки основного меню (уже реализовано, без изменений)
PIN_STATS = 16
PIN_ITEMS = 17
PIN_DATA = 18

# Power (раздел 3.1)
PIN_POWER = 4

# Энкодер меню — навигация по подразделам (раздел 5 roadmap, "Модель навигации")
PIN_ENC_MENU_A = 5
PIN_ENC_MENU_B = 6
PIN_ENC_MENU_SW = 7

# I2C — тюнер RDA5807M (резерв: MCP4725, если ШИМ Гейгера даст заметные пульсации)
PIN_I2C_SDA = 8
PIN_I2C_SCL = 9

# Гейгер — мгновенное чтение по Wi-Fi-скану (roadmap, Phase A)
PIN_GEIGER_METER = 10  # ШИМ -> RC-фильтр -> стрелочный вольтметр
PIN_GEIGER_PIEZO = 11  # пьезодинамик (треск)

# Подсветка — два независимых канала (раздел 3.1)
PIN_BACKLIGHT_BUTTONS = 12  # кнопки + циферблаты Гейгера/радио — дискретно, вместе с POWER
PIN_LAMPS = 13  # лампы накала — плавный программный fade, вместе с POWER

# Радио (раздел 3.2 — тумблер вкл/выкл, раздел 3.3 — энкодер тюнинга/громкости)
PIN_RADIO_TOGGLE = 15
PIN_ENC_RADIO_A = 33
PIN_ENC_RADIO_B = 34
PIN_ENC_RADIO_SW = 35

# USB Host D-/D+ (голодиск) — GPIO 19/20, зарезервированы под usb_host, здесь не читаются

# Свободно про запас: 1, 2, 14, 21, 36-42, 47, 48

buttonSTATS = Pin(PIN_STATS, Pin.IN, Pin.PULL_UP)
buttonITEMS = Pin(PIN_ITEMS, Pin.IN, Pin.PULL_UP)
buttonDATA = Pin(PIN_DATA, Pin.IN, Pin.PULL_UP)

# --- POWER (протокол, раздел 3.1) ---
# ESP32 — хозяин состояния: сначала физика (подсветка), потом notify. powerState по
# умолчанию False — совпадает с безопасным дефолтом OFF на стороне приложения.

buttonPOWER = Pin(PIN_POWER, Pin.IN, Pin.PULL_UP)
pwmBacklight = PWM(Pin(PIN_BACKLIGHT_BUTTONS), freq=1000, duty_u16=0)
pwmLamps = PWM(Pin(PIN_LAMPS), freq=1000, duty_u16=0)

powerState = False
_lastPowerPinValue = 1  # pull-up: 1 = отпущена

_LAMP_FADE_STEPS = 40
_LAMP_FADE_STEP_MS = 15  # ~600 мс на полный разгорев/затухание


def set_backlight(on):
    # Кнопки и циферблаты — дискретно, без разгорева.
    pwmBacklight.duty_u16(65535 if on else 0)


def fade_lamps(turning_on):
    # Блокирующий fade (~600 мс) — намеренное упрощение: POWER нажимается редко и
    # осознанно, кратковременная пауза в опросе остальных кнопок в эти ~600 мс не мешает.
    start, end = (0, 65535) if turning_on else (65535, 0)
    step = (end - start) // _LAMP_FADE_STEPS
    for i in range(_LAMP_FADE_STEPS + 1):
        pwmLamps.duty_u16(max(0, min(65535, start + step * i)))
        time.sleep_ms(_LAMP_FADE_STEP_MS)
    pwmLamps.duty_u16(end)


def apply_power_state(on):
    # Физика применяется немедленно и безусловно — независимо от того, подключён ли
    # сейчас телефон по BLE (протокол, раздел 3.1).
    set_backlight(on)
    fade_lamps(on)


# --- Энкодеры (протокол, раздел 4; roadmap, "Модель навигации энкодером") ---
# Декодирование — через прерывания, не через опрос главного цикла: переходы на A/B при
# обычном прокруте идут на масштабе единиц миллисекунд, 350-мс опрос их не поймает.
# Общий класс — используется и для энкодера меню, и для радио-энкодера (протокол,
# раздел 4: "тот же decode... вынести в переиспользуемую функцию/класс").

# Таблица переходов Gray-кода: индекс — (пред.состояние<<2)|тек.состояние, состояние —
# 2 бита (A<<1|B). Валидный шаг даёт +1/-1, недопустимая пара (дребезг/пропуск) — 0.
_ENC_TRANSITIONS = (
    0, -1, 1, 0,
    1, 0, 0, -1,
    -1, 0, 0, 1,
    0, 1, -1, 0,
)


class QuadratureEncoder:
    def __init__(self, pin_a, pin_b):
        self._pin_a = Pin(pin_a, Pin.IN, Pin.PULL_UP)
        self._pin_b = Pin(pin_b, Pin.IN, Pin.PULL_UP)
        self._prev_state = 0
        self._quarter_steps = 0
        self.delta = 0  # накопленные завершённые "щелчки" с прошлой проверки

        self._pin_a.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=self._irq)
        self._pin_b.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=self._irq)

    def _irq(self, _pin):
        state = (self._pin_a.value() << 1) | self._pin_b.value()
        self._quarter_steps += _ENC_TRANSITIONS[(self._prev_state << 2) | state]
        self._prev_state = state
        # 4 четверть-шага = один полный механический "щелчок".
        if self._quarter_steps >= 4:
            self.delta += 1
            self._quarter_steps = 0
        elif self._quarter_steps <= -4:
            self.delta -= 1
            self._quarter_steps = 0

    def take_delta(self):
        # Забрать накопленный чистый сдвиг с прошлой проверки и обнулить счётчик.
        d = self.delta
        self.delta = 0
        return d


encMenu = QuadratureEncoder(PIN_ENC_MENU_A, PIN_ENC_MENU_B)
buttonEncMenuSW = Pin(PIN_ENC_MENU_SW, Pin.IN, Pin.PULL_UP)
_lastEncMenuSWValue = 1


# --- Радио (протокол, разделы 3.2 и 3.3) ---
# ⚠️ Точные позиции битов в регистрах 0x02/0x03/0x05 RDA5807M — по общедоступной
# документации чипа, не проверялись на реальном железе. Свериться с даташитом и
# поправить при бринг-апе в Phase A, прежде чем полагаться на конкретные значения.

_RDA_I2C_ADDR = 0x11  # sequential-access режим: пишем регистры 0x02..0x07 одним блоком
_RDA_REG_CTL = 0   # индекс регистра 0x02 в _radioRegs
_RDA_REG_CHAN = 1  # индекс регистра 0x03
_RDA_REG_VOL = 3   # индекс регистра 0x05
_RDA_TUNE_BIT = 1 << 4
_DEFAULT_VOLUME = 8  # 0-15, временный дефолт — пока нет отдельной команды регулировки

FM_BAND_LOW_KHZ = 87000
FM_CHANNEL_SPACING_KHZ = 100

i2cRadio = I2C(0, scl=Pin(PIN_I2C_SCL), sda=Pin(PIN_I2C_SDA), freq=100000)
_radioRegs = [0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000]


def _radio_write_registers():
    data = bytearray(12)
    for i, reg in enumerate(_radioRegs):
        data[i * 2] = (reg >> 8) & 0xFF
        data[i * 2 + 1] = reg & 0xFF
    i2cRadio.writeto(_RDA_I2C_ADDR, data)


def radio_power(on):
    # DHIZ|DMUTE|ENABLE (0xC001) — нормальная работа, звук не заглушен, чип включён.
    _radioRegs[_RDA_REG_CTL] = 0xC001 if on else 0x0000
    _radioRegs[_RDA_REG_VOL] = (_radioRegs[_RDA_REG_VOL] & 0xFFF0) | (_DEFAULT_VOLUME if on else 0)
    _radio_write_registers()


def radio_set_frequency(freq_mhz_x10):
    freq_khz = freq_mhz_x10 * 100
    channel = (freq_khz - FM_BAND_LOW_KHZ) // FM_CHANNEL_SPACING_KHZ
    channel = max(0, min(0x3FF, channel))
    _radioRegs[_RDA_REG_CHAN] = (channel << 6) | _RDA_TUNE_BIT
    _radio_write_registers()


buttonRadioToggle = Pin(PIN_RADIO_TOGGLE, Pin.IN, Pin.PULL_UP)
radioPowerState = False
_lastRadioTogglePinValue = None  # None -> синхронизироваться с реальным тумблером на первом проходе

encRadio = QuadratureEncoder(PIN_ENC_RADIO_A, PIN_ENC_RADIO_B)
buttonRadioTuneSW = Pin(PIN_ENC_RADIO_SW, Pin.IN, Pin.PULL_UP)
radioTuneMode = True  # по умолчанию тюнинг, не громкость (протокол, раздел 3.3)
_lastRadioTuneSWValue = 1


# --- Гейгер (roadmap, Phase A; протокол, раздел 3) ---
# Мгновенное чтение: периодический Wi-Fi-скан на маяки R10/R20/R50, есть сигнал —
# уровень от силы RSSI, нет сигнала — тишина и ноль. Без памяти, без накопления, без
# сброса на уровне самого сканера (это отдельная логика вне PipBoy, roadmap раздел 2).
#
# ⚠️ Формула пересчёта RSSI -> 0-255 (_rssi_to_level) — линейная заглушка, не
# откалибрована на реальном железе (протокол, раздел 7, открытый вопрос).
#
# ⚠️ Wi-Fi и BLE делят один радиотракт на ESP32-S3 — риск-тест из Phase A ("работают ли
# все три роли вместе") ещё не пройден на макетке. Ниже — рабочая реализация ожидаемого
# поведения, а не подтверждение, что BLE не будет заикаться во время скана.
#
# MicroPython `WLAN.scan()` — блокирующий вызов (в отличие от `WiFi.scanNetworks(true)`
# из Arduino/ESP-IDF C++, на который ссылается roadmap — в MicroPython такого асинхронного
# режима нет). Чтобы не подвешивать BLE-нотификации и опрос кнопок на время скана,
# сканирование вынесено в отдельный поток на втором ядре (`_thread`), а не в главный цикл.

GEIGER_BEACON_PREFIXES = ("R10", "R20", "R50")
GEIGER_SCAN_INTERVAL_S = 6  # roadmap: "раз в 6 сек"
PIEZO_CLICK_MAX_PROB = 0.6  # вероятность клика за один тик главного цикла при макс. уровне
PIEZO_CLICK_MS = 8

pwmGeigerMeter = PWM(Pin(PIN_GEIGER_METER), freq=1000, duty_u16=0)
pwmGeigerPiezo = PWM(Pin(PIN_GEIGER_PIEZO), freq=2000, duty_u16=0)

wlanSTA = network.WLAN(network.STA_IF)
wlanSTA.active(True)

geigerLevel = 0  # 0-255, обновляется фоновым потоком сканирования
_lastSentGeigerLevel = -1  # -1 -> форсировать первую отправку/применение, даже если 0


def _matches_geiger_beacon(ssid):
    for prefix in GEIGER_BEACON_PREFIXES:
        if ssid.startswith(prefix):
            return True
    return False


def _rssi_to_level(rssi):
    if rssi is None:
        return 0
    # Заглушка: типичный диапазон -90..-30 дБм линейно растянут в 0..255.
    level = int((rssi + 90) * (255 / 60))
    return max(0, min(255, level))


def _geiger_scan_loop():
    global geigerLevel
    while True:
        try:
            found = wlanSTA.scan()
        except OSError:
            found = ()

        best_rssi = None
        for ssid_bytes, _bssid, _channel, rssi, _authmode, _hidden in found:
            ssid = ssid_bytes.decode('utf-8', 'ignore') if isinstance(ssid_bytes, bytes) else ssid_bytes
            if _matches_geiger_beacon(ssid):
                if best_rssi is None or rssi > best_rssi:
                    best_rssi = rssi

        geigerLevel = _rssi_to_level(best_rssi)
        time.sleep(GEIGER_SCAN_INTERVAL_S)


_thread.start_new_thread(_geiger_scan_loop, ())


def geiger_set_meter(level):
    pwmGeigerMeter.duty_u16(level * 257)  # 0-255 -> 0-65535


def geiger_piezo_click():
    pwmGeigerPiezo.duty_u16(32768)
    time.sleep_ms(PIEZO_CLICK_MS)
    pwmGeigerPiezo.duty_u16(0)


_UART_UUID = bluetooth.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
_UART_TX = (
    bluetooth.UUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"),
    _FLAG_READ | _FLAG_NOTIFY,
)
_UART_RX = (
    bluetooth.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"),
    _FLAG_WRITE | _FLAG_WRITE_NO_RESPONSE,
)
_UART_SERVICE = (
    _UART_UUID,
    (_UART_TX, _UART_RX),
)


class BLESimplePeripheral:
    def __init__(self, ble, name="pipd-ble"):
        self._ble = ble
        self._ble.active(True)
        self._ble.irq(self._irq)
        ((self._handle_tx, self._handle_rx),) = self._ble.gatts_register_services((_UART_SERVICE,))
        self._connections = set()
        self._write_callback = None
        self._connect_callback = None
        self._payload = advertising_payload(name=name, services=[_UART_UUID])
        self._advertise()

    def _irq(self, event, data):
        # Track connections so we can send notifications.
        if event == _IRQ_CENTRAL_CONNECT:
            conn_handle, _, _ = data
            print("New connection", conn_handle)
            self._connections.add(conn_handle)
            if self._connect_callback:
                # Ресинхронизация состояния (POWER, RADIOPWR, ...) — телефон не должен
                # ждать нового нажатия кнопки, чтобы узнать текущее состояние (протокол,
                # раздел 3.1).
                self._connect_callback()
        elif event == _IRQ_CENTRAL_DISCONNECT:
            conn_handle, _, _ = data
            print("Disconnected", conn_handle)
            self._connections.remove(conn_handle)
            # Start advertising again to allow a new connection.
            self._advertise()
        elif event == _IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            value = self._ble.gatts_read(value_handle)
            if value_handle == self._handle_rx and self._write_callback:
                self._write_callback(value)

    def send(self, data):
        for conn_handle in self._connections:
            self._ble.gatts_notify(conn_handle, self._handle_tx, data)

    def is_connected(self):
        return len(self._connections) > 0

    def _advertise(self, interval_us=500000):
        print("Starting advertising")
        self._ble.gap_advertise(interval_us, adv_data=self._payload)

    def on_write(self, callback):
        self._write_callback = callback

    def on_connect(self, callback):
        self._connect_callback = callback


def run():
    global powerState, _lastPowerPinValue
    global _lastEncMenuSWValue
    global radioPowerState, _lastRadioTogglePinValue
    global radioTuneMode, _lastRadioTuneSWValue
    global _lastSentGeigerLevel

    ble = bluetooth.BLE()
    p = BLESimplePeripheral(ble)

    # Физическое состояние применяется сразу при старте прошивки, до первого подключения
    # телефона — подсветка не должна ждать BLE (протокол, раздел 3.1).
    apply_power_state(powerState)

    def on_rx(v):
        global radioPowerState
        command = v.decode('utf-8').strip()
        vars.sentVALUE = command

        key, _, value = command.partition(":")

        # Существующая RADIOPWR от приложения — соотношение с физическим тумблером
        # (GPIO 15) сознательно не решено (протокол, раздел 3.2): применяется как есть,
        # но при следующем изменении положения тумблера будет перезаписана им.
        if key == "RADIOPWR" and value:
            radioPowerState = value == "1"
            radio_power(radioPowerState)
        elif key == "RADIOFREQ" and value:
            try:
                radio_set_frequency(int(value))
            except ValueError:
                pass

    def on_connect():
        vars.sentVALUE = "POWER:{}".format(1 if powerState else 0)
        p.send(vars.sentVALUE)
        vars.sentVALUE = "RADIOPWR:{}".format(1 if radioPowerState else 0)
        p.send(vars.sentVALUE)

    p.on_write(on_rx)
    p.on_connect(on_connect)

    while True:
        powerPinValue = buttonPOWER.value()
        if powerPinValue == 0 and _lastPowerPinValue == 1:
            # Только по факту нажатия (переход "отпущена" -> "нажата"), не на каждый
            # проход цикла, пока кнопка удерживается — иначе POWER будет тумблериться
            # по несколько раз за одно нажатие (протокол, раздел 4).
            powerState = not powerState
            apply_power_state(powerState)
            vars.sentVALUE = "POWER:{}".format(1 if powerState else 0)
            p.send(vars.sentVALUE)
        _lastPowerPinValue = powerPinValue

        menuDelta = encMenu.take_delta()
        if menuDelta != 0:
            # Накопленный чистый сдвиг с прошлой проверки — одним сообщением, а не по
            # одному ENC:+1 на "щелчок" (протокол, раздел 4).
            sign = "+" if menuDelta > 0 else ""
            vars.sentVALUE = "ENC:{}{}".format(sign, menuDelta)
            p.send(vars.sentVALUE)

        encSwValue = buttonEncMenuSW.value()
        if encSwValue == 0 and _lastEncMenuSWValue == 1:
            vars.sentVALUE = "ENCBTN"
            p.send(vars.sentVALUE)
        _lastEncMenuSWValue = encSwValue

        # Тумблер вкл/выкл радио — не кнопка, а фиксированное положение (протокол,
        # раздел 3.2): реагируем на любое изменение уровня пина, а не на "нажатие".
        radioPinValue = buttonRadioToggle.value()
        if radioPinValue != _lastRadioTogglePinValue:
            radioPowerState = (radioPinValue == 0)
            radio_power(radioPowerState)
            vars.sentVALUE = "RADIOPWR:{}".format(1 if radioPowerState else 0)
            p.send(vars.sentVALUE)
            _lastRadioTogglePinValue = radioPinValue

        radioDelta = encRadio.take_delta()
        if radioDelta != 0:
            sign = "+" if radioDelta > 0 else ""
            key = "RADIOTUNE" if radioTuneMode else "VOLUME"
            vars.sentVALUE = "{}:{}{}".format(key, sign, radioDelta)
            p.send(vars.sentVALUE)

        radioTuneSWValue = buttonRadioTuneSW.value()
        if radioTuneSWValue == 0 and _lastRadioTuneSWValue == 1:
            radioTuneMode = not radioTuneMode
            vars.sentVALUE = "RADIOTUNEBTN"
            p.send(vars.sentVALUE)
        _lastRadioTuneSWValue = radioTuneSWValue

        # geigerLevel обновляется фоновым потоком сканирования (~раз в 6 сек) — здесь
        # только реагируем на изменение, не сканируем сами.
        if geigerLevel != _lastSentGeigerLevel:
            geiger_set_meter(geigerLevel)
            vars.sentVALUE = "GEIGER:{}".format(geigerLevel)
            p.send(vars.sentVALUE)
            _lastSentGeigerLevel = geigerLevel

        if geigerLevel > 0 and random.random() < (geigerLevel / 255) * PIEZO_CLICK_MAX_PROB:
            geiger_piezo_click()

        if p.is_connected():
            if buttonSTATS.value() == 0:
                vars.sentVALUE = "STATS"
                p.send(vars.sentVALUE)
            if buttonITEMS.value() == 0:
                vars.sentVALUE = "ITEMS"
                p.send(vars.sentVALUE)
            if buttonDATA.value() == 0:
                vars.sentVALUE = "DATA"
                p.send(vars.sentVALUE)

        print(vars.sentVALUE)
        time.sleep_ms(350)


if __name__ == "__main__":
    run()
