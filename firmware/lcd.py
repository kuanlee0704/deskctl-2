"""ST7735S 0.96" 80x160 IPS 驅動 (橫向 160x80)。"""
import time
from machine import Pin, SPI
from config import *


class LCD:
    def __init__(self, orient=0, bgr=True, inv=True, panel=0):
        self.cs = Pin(PIN_CS, Pin.OUT, value=1)
        self.dc = Pin(PIN_DC, Pin.OUT, value=0)
        self.rst = Pin(PIN_RST, Pin.OUT, value=1)
        self.bl = None
        try:                                   # GPIO11 在部分核心板上是 flash 電源腳, 失敗就算了(背光常亮)
            self.bl = Pin(PIN_BL, Pin.OUT, value=1)
        except Exception:
            self.bl = None
        self.spi = self._make_spi()
        self.orient = orient
        self.bgr = bgr
        self.inv = inv
        self.panel = panel
        time.sleep_ms(150)          # 等電源穩定 (冷開機)
        self.init()
        time.sleep_ms(60)
        self.init()                 # 重複初始化一次, 避免冷開機第一次沒吃到

    def _make_spi(self):
        kw = dict(baudrate=SPI_BAUD, polarity=0, phase=0, sck=Pin(PIN_SCK), mosi=Pin(PIN_MOSI))
        for extra in ({"miso": None}, {}):
            try:
                return SPI(1, **kw, **extra)
            except Exception:
                pass
        from machine import SoftSPI
        return SoftSPI(baudrate=8_000_000, polarity=0, phase=0, sck=Pin(PIN_SCK),
                       mosi=Pin(PIN_MOSI), miso=Pin(12, Pin.IN))   # miso 不使用

    def cmd(self, c, data=None):
        self.cs(0)
        self.dc(0)
        self.spi.write(bytes((c,)))
        if data:
            self.dc(1)
            self.spi.write(data)
        self.cs(1)

    def init(self):
        self.rst(1); time.sleep_ms(10)
        self.rst(0); time.sleep_ms(20)
        self.rst(1); time.sleep_ms(120)
        c = self.cmd
        c(0x01); time.sleep_ms(150)
        c(0x11); time.sleep_ms(255)
        c(0xB1, b"\x01\x2C\x2D")
        c(0xB2, b"\x01\x2C\x2D")
        c(0xB3, b"\x01\x2C\x2D\x01\x2C\x2D")
        c(0xB4, b"\x07")
        c(0xC0, b"\xA2\x02\x84")
        c(0xC1, b"\xC5")
        c(0xC2, b"\x0A\x00")
        c(0xC3, b"\x8A\x2A")
        c(0xC4, b"\x8A\xEE")
        c(0xC5, b"\x0E")
        c(0x3A, b"\x05")
        c(0xE0, b"\x02\x1C\x07\x12\x37\x32\x29\x2D\x29\x25\x2B\x39\x00\x01\x03\x10")
        c(0xE1, b"\x03\x1D\x07\x06\x2E\x2C\x29\x2D\x2E\x2E\x37\x3F\x00\x00\x02\x10")
        self.apply_mode()
        c(0x13); time.sleep_ms(10)
        c(0x29); time.sleep_ms(100)

    def apply_mode(self):
        self.cmd(0x36, bytes((ORIENTS[self.orient % 4] | (0x08 if self.bgr else 0),)))
        self.cmd(0x21 if self.inv else 0x20)

    def set_mode(self, orient=None, bgr=None, inv=None, panel=None):
        if panel is not None:
            self.panel = panel
        if orient is not None:
            self.orient = orient
        if bgr is not None:
            self.bgr = bgr
        if inv is not None:
            self.inv = inv
        self.apply_mode()

    def backlight(self, on):
        if self.bl is not None:
            try:
                self.bl(1 if on else 0)
            except Exception:
                pass

    def show(self, buf):
        x0, y0 = PANELS[self.panel % len(PANELS)]
        self.cmd(0x2A, bytes((x0 >> 8, x0 & 255, (x0 + LCD_W - 1) >> 8, (x0 + LCD_W - 1) & 255)))
        self.cmd(0x2B, bytes((y0 >> 8, y0 & 255, (y0 + LCD_H - 1) >> 8, (y0 + LCD_H - 1) & 255)))
        self.cs(0)
        self.dc(0)
        self.spi.write(b"\x2C")
        self.dc(1)
        self.spi.write(buf)
        self.cs(1)
