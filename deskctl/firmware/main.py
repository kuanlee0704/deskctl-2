import time
import sys
import machine
from config import *


def boot():
    import gc
    # 藍牙控制器要最先開, 趁記憶體還沒被畫面/字型緩衝區佔走
    try:
        import bluetooth
        bluetooth.BLE().active(True)
        print("bt early ok, reset cause", machine.reset_cause())
    except Exception as e:
        print("bt early fail:", e, "reset cause", machine.reset_cause())
    from gfx import Font, Canvas
    from lcd import LCD
    from keys import Keys
    from state import State
    gc.collect()
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
        del calib
        gc.collect()
    from ble import NullLink
    from app import App
    app = App(c, lcd, keys, NullLink(), st, time.ticks_ms(), machine.reset)   # 先建好 UI 與大緩衝區
    gc.collect()
    try:
        from ble import Link
        app.link = Link()
        if cfg.get("btrst"):
            cfg.set("btrst", 0)
    except Exception as e:                      # 沒有藍牙也能開機
        sys.print_exception(e)
        if not cfg.get("btrst"):                # 藍牙卡住 -> 硬重啟一次 (只試一次, 避免無限重啟)
            cfg.set("btrst", 1)
            time.sleep_ms(200)
            machine.reset()
    gc.collect()
    print("mem free", gc.mem_free(), "alloc", gc.mem_alloc())
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
    import gc
    try:
        import micropython
        micropython.alloc_emergency_exception_buf(100)
    except Exception:
        pass
    app = lcd = c = None
    mem_errs = []
    try:
        app, lcd, c = boot()
        while True:
            try:
                app.step(time.ticks_ms())
            except MemoryError:
                # 記憶體不足: 先整理再繼續; 10 秒內連續 8 次才重啟
                gc.collect()
                now = time.ticks_ms()
                mem_errs.append(now)
                while mem_errs and time.ticks_diff(now, mem_errs[0]) > 10000:
                    mem_errs.pop(0)
                print("MemoryError", gc.mem_free())
                if len(mem_errs) >= 8:
                    raise
            app.pm.nap()
    except KeyboardInterrupt:
        raise
    except Exception as e:
        sys.print_exception(e)
        if lcd is not None:
            show_error(lcd, c, e)
        time.sleep(3)
        machine.reset()


run()
