"""螢幕方向/色彩校正: 第一次開機或從設定頁進入。
上=換方向  下=反相  左=RGB/BGR  中央=儲存"""
import time
from keys import *
from ui import *
from gfx import rgb


def run(lcd, canvas, keys, cfg):
    c = canvas
    o, bgr, inv = cfg.get("orient"), bool(cfg.get("bgr")), bool(cfg.get("inv"))
    lcd.set_mode(o, bgr, inv)
    redraw = True
    while True:
        now = time.ticks_ms()
        for k, ev in keys.poll(now):
            if ev != EV_CLICK and ev != EV_PRESS:
                continue
            if ev == EV_PRESS and k != K_UP and k != K_LEFT and k != K_DOWN:
                continue
            if k == K_UP:
                o = (o + 1) % 4
            elif k == K_DOWN:
                inv = not inv
            elif k == K_LEFT:
                bgr = not bgr
            elif k == K_CENTER and ev == EV_CLICK:
                cfg.d.update({"orient": o, "bgr": int(bgr), "inv": int(inv), "calib": 1})
                cfg._dirty = True
                cfg.save()
                return
            lcd.set_mode(o, bgr, inv)
            redraw = True
        if redraw:
            redraw = False
            c.fill(BG)
            c.rect(0, 0, 160, 80, FG)
            c.text("左上角", 3, 3, FG)
            c.text_r("右上", 156, 3, FG)
            c.text("左下", 3, 65, FG)
            c.text_r("右下 按鍵→", 156, 65, FG)
            c.text_c("螢幕方向校正", 80, 17, ACC)
            c.text_c("字正、按鍵在右 -> 按中央", 80, 30, DIM)
            for i, (col, ch) in enumerate(((rgb(255, 0, 0), "R"), (rgb(0, 255, 0), "G"), (rgb(0, 0, 255), "B"))):
                c.frect(40 + i * 26, 44, 22, 14, col)
                c.text(ch, 48 + i * 26, 45, rgb(255, 255, 255))
            c.text_c("上:轉向 下:反相 左:色序", 80, 56 if False else 59, DIM)
            lcd.show(c.buf)
        time.sleep_ms(15)
