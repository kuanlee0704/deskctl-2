import time
import sys
import machine
from config import *


def boot():
    from gfx import Font, Canvas
    from lcd import LCD
    from keys import Keys
    from state import State
    st = State()
    cfg = st.cfg
    print("DeskCtl v" + VERSION, cfg.d)
    keys = Keys()
    force_calib = bool(keys.read_raw()[2])       # 開機時按住中央 -> 強制進入校正
    lcd = LCD(cfg.get("orient"), bool(cfg.get("bgr")), bool(cfg.get("inv")), cfg.get("panel"))
    font = Font(FONT_FILE)
    c = Canvas(LCD_W, LCD_H, font)
    if force_calib or not cfg.get("calib"):
        import calib
        calib.run(lcd, c, keys, cfg)
        del sys.modules["calib"]
    try:
        from ble import Link
        link = Link()
    except Exception as e:                      # 沒有藍牙也能開機
        sys.print_exception(e)
        from ble import NullLink
        link = NullLink()
    from app import App
    app = App(c, lcd, keys, link, st, time.ticks_ms(), machine.reset)
    return app, lcd, c


def show_error(lcd, c, e):
    try:
        import io
        buf = io.StringIO()
        sys.print_exception(e, buf)
        msg = buf.getvalue().replace("\n", " ")[-150:]
    except Exception:
        msg = repr(e)
    c.fill(1)
    y = 2
    line = ""
    for ch in msg:
        if c.text_w(line + ch) > 152:
            c.text(line, 4, y, 0xFFFF)
            y += 12
            line = ""
            if y > 66:
                break
        line += ch
    c.text(line, 4, y, 0xFFFF)
    lcd.show(c.buf)


def run():
    app = lcd = c = None
    try:
        app, lcd, c = boot()
        while True:
            app.step(time.ticks_ms())
            time.sleep_ms(8)
    except KeyboardInterrupt:
        raise
    except Exception as e:
        sys.print_exception(e)
        if lcd is not None:
            show_error(lcd, c, e)
        time.sleep(8)
        machine.reset()


run()
