"""螢幕校正: 第一次開機或從設定頁進入。
上=換方向  右=換面板型號(偏移)  下=反相  左=RGB/BGR  中央=儲存
判斷標準: 外框四邊完整、直線不歪斜、文字不鏡像、按鍵在右、R/G/B 色塊顏色正確"""
import time
from keys import *
from ui import *
from gfx import rgb
from config import PANELS, VERSION


def draw(c, o, panel, bgr, inv, last=""):
    c.fill(BG)
    c.rect(0, 0, 160, 80, FG)
    c.rect(1, 1, 158, 78, FAINT)
    c.line(0, 0, 159, 79, FAINT)
    c.line(0, 79, 159, 0, FAINT)
    c.frect(30, 12, 100, 56, BG)
    c.text("左上", 3, 3, FG)
    c.text_r("右上", 156, 3, FG)
    c.text("左下", 3, 65, FG)
    c.text_r("右下 按鍵→", 156, 65, FG)
    c.text_c("螢幕校正", 80, 13, ACC)
    c.text_c("字正+框直 → 按中央", 80, 26, DIM)
    for i, (col, ch) in enumerate(((rgb(255, 0, 0), "R"), (rgb(0, 255, 0), "G"), (rgb(0, 0, 255), "B"))):
        c.frect(40 + i * 26, 40, 22, 12, col)
        c.text(ch, 48 + i * 26, 40, rgb(255, 255, 255))
    c.text_c("方向%d 型號%d %s %s" % (o + 1, panel + 1, "BGR" if bgr else "RGB", "反" if inv else "正"), 80, 54, WARN)
    c.text_c("v" + VERSION + ("  按到:" + last if last else ""), 80, 2, GOOD if last else FAINT)


def run(lcd, canvas, keys, cfg):
    c = canvas
    o, panel = cfg.get("orient"), cfg.get("panel")
    bgr, inv = bool(cfg.get("bgr")), bool(cfg.get("inv"))
    lcd.set_mode(o, bgr, inv, panel)
    print("校正: 上=方向 右=面板型號 下=反相 左=色序 中=儲存")
    redraw = True
    last = ""
    while True:
        now = time.ticks_ms()
        for k, ev in keys.poll(now):
            if ev == EV_PRESS:
                last = KEY_NAMES[k]
                redraw = True
                continue
            if ev != EV_CLICK:
                continue
            if k == K_UP:
                o = (o + 1) % 4
            elif k == K_RIGHT:
                panel = (panel + 1) % len(PANELS)
            elif k == K_DOWN:
                inv = not inv
            elif k == K_LEFT:
                bgr = not bgr
            elif k == K_CENTER:
                cfg.d.update({"orient": o, "panel": panel, "bgr": int(bgr), "inv": int(inv), "calib": 1})
                cfg._dirty = True
                cfg.save()
                return
            lcd.set_mode(o, bgr, inv, panel)
            redraw = True
        if redraw:
            redraw = False
            draw(c, o, panel, bgr, inv, last)
            lcd.show(c.buf)
        time.sleep_ms(15)
