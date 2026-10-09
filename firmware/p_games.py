from app import Page
from keys import *
from ui import *
import registry


class GamesPage(Page):
    title = "遊戲"
    reset_label = ""

    def __init__(self, app):
        Page.__init__(self, app)
        self.sel = 0
        self.items = registry.GAMES

    def on_key(self, k, ev, now):
        n = len(self.items)
        self.dirty = True
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            self.sel = (self.sel + (1 if k == K_DOWN else -1)) % n
            return True
        if k == K_CENTER and ev == EV_CLICK:
            self.app.go(self.items[self.sel][0], now)
            return True
        return False

    def draw(self, c, now):
        n = len(self.items)
        rows = 4
        top = min(max(self.sel - 1, 0), max(0, n - rows))
        y = TOP_H + 1
        for i in range(top, min(n, top + rows)):
            if i == self.sel:
                c.frect(2, y - 1, 156, 14, PANEL)
                c.text(">", 5, y, ACC)
            c.text(self.items[i][1], 16, y, FG if i == self.sel else DIM)
            y += 14
        hi = self.st.cfg.get("hi")
        c.text("上下:選擇  中:開始", 4, 68, FAINT)
        if hi:
            c.text_r("蛇最高 %d" % hi, 156, 68, WARN)
