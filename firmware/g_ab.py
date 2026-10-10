import random
from app import Page
from keys import *
from ui import *


class ABPage(Page):
    title = "1A2B"
    reset_label = "重新開始"

    def __init__(self, app):
        Page.__init__(self, app)
        self.new()

    def new(self):
        d = list("0123456789")
        for i in range(9, 0, -1):          # MicroPython 沒有 random.shuffle
            j = random.randrange(i + 1)
            d[i], d[j] = d[j], d[i]
        self.secret = d[:4]
        self.guess = [1, 2, 3, 4]
        self.pos = 0
        self.hist = []
        self.scroll = 0
        self.won = False

    def on_reset(self, now):
        self.new()

    def get_title(self):
        return "1A2B 第%d次" % (len(self.hist) + (0 if self.won else 1))

    def on_key(self, k, ev, now):
        app = self.app
        self.dirty = True
        if k == K_CENTER:
            if ev == EV_CLICK:
                if self.won:
                    self.new()
                    return True
                g = [str(x) for x in self.guess]
                if len(set(g)) < 4:
                    app.toast("數字不能重複")
                    return True
                a = sum(1 for i in range(4) if g[i] == self.secret[i])
                b = sum(1 for x in g if x in self.secret) - a
                self.hist.append(("".join(g), a, b))
                self.scroll = max(0, len(self.hist) - 5)
                if a == 4:
                    self.won = True
                    app.toast("答對了!")
                return True
            if ev == EV_DOUBLE:
                app.go("games", now)
                return True
            return ev in (EV_PRESS, EV_RELEASE)
        if self.won:
            return True
        if k == K_LEFT and ev == EV_CLICK:
            self.pos = (self.pos - 1) % 4
        elif k == K_RIGHT and ev == EV_CLICK:
            self.pos = (self.pos + 1) % 4
        elif k in (K_UP, K_DOWN) and ev == EV_CLICK:
            self.guess[self.pos] = (self.guess[self.pos] + (1 if k == K_UP else -1)) % 10
        elif k in (K_UP, K_DOWN) and ev in (EV_LONG, EV_HOLD):
            self.scroll_hist(-1 if k == K_UP else 1)       # 長按: 翻看過去的紀錄
        else:
            return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE)
        return True

    def scroll_hist(self, d):
        self.scroll = min(max(0, len(self.hist) - 5), max(0, self.scroll + d))

    def draw(self, c, now):
        for i in range(4):
            x = 4 + i * 19
            sel = (i == self.pos) and not self.won
            c.frect(x, 15, 17, 24, PANEL)
            if sel:
                c.rect(x, 15, 17, 24, ACC)
            ch = self.secret[i] if self.won else str(self.guess[i])
            c.text_big(ch, x + 3, 15, GOOD if self.won else FG, 2, 6)
        if self.won:
            c.text("答案!", 4, 42, GOOD)
            c.text("中央:再玩", 4, 54, DIM)
        else:
            c.text("左右:選位", 4, 42, DIM)
            c.text("上下:改數", 4, 54, DIM)
            c.text("中央:送出", 4, 66, DIM)
        # 歷史 (超過 5 筆: 長按上下翻頁)
        n = len(self.hist)
        top = min(self.scroll, max(0, n - 5))
        c.vline(84, 14, 64, FAINT)
        y = 15
        for g, a, b in self.hist[top:top + 5]:
            c.text(g, 90, y, FG)
            c.text("%dA%dB" % (a, b), 122, y, GOOD if a == 4 else ACC)
            y += 13
        if n > 5:
            h = max(6, 52 * 5 // n)
            c.frect(158, 15 + (52 - h) * top // (n - 5), 2, h, FAINT)
