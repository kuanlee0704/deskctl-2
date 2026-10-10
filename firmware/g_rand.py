"""隨機取數: 設定範圍(000~999)與個數(XX), 左右換位數、上下換數字, 中央抽。
結果太多一頁放不下時, 上下(單按或長按)捲動。"""
import random
from app import Page
from keys import *
from ui import *

# 游標位置: 0-2 = 最小值三位, 3-5 = 最大值三位, 6-7 = 個數兩位, 8 = 可否重複
NPOS = 9
COLS_Y = 15


class RandPage(Page):
    title = "隨機取數"
    reset_label = "回到設定"

    def __init__(self, app):
        Page.__init__(self, app)
        lo, hi, n, uq = self.st.cfg.get("rnd")
        self.dg = [lo // 100 % 10, lo // 10 % 10, lo % 10,
                   hi // 100 % 10, hi // 10 % 10, hi % 10,
                   n // 10 % 10, n % 10]
        self.uniq = 1 if uq else 0
        self.pos = 0
        self.res = None          # None = 設定畫面; list = 結果畫面
        self.top = 0
        self.w = 3

    # ---- 數值 ----
    def vals(self):
        d = self.dg
        lo = d[0] * 100 + d[1] * 10 + d[2]
        hi = d[3] * 100 + d[4] * 10 + d[5]
        return lo, hi, d[6] * 10 + d[7]

    def on_reset(self, now):
        self.res = None

    # ---- 抽 ----
    def roll(self):
        lo, hi, n = self.vals()
        if lo > hi:
            lo, hi = hi, lo
        span = hi - lo + 1
        if n < 1:
            self.app.toast("個數至少 1")
            return False
        if self.uniq and n > span:
            n = span
            self.app.toast("不重複: 最多 %d 個" % span)
        out = []
        if self.uniq:
            d = {}                              # 稀疏 Fisher-Yates, 不用整串清單
            for i in range(n):
                j = i + random.randrange(span - i)
                out.append(d.get(j, j) + lo)
                d[j] = d.get(i, i)
        else:
            for _ in range(n):
                out.append(lo + random.randrange(span))
        self.res = out
        self.w = len(str(hi))
        self.top = 0
        self.st.cfg.set("rnd", [lo, hi, n, self.uniq])
        return True

    # ---- 按鍵 ----
    def on_key(self, k, ev, now):
        app = self.app
        self.dirty = True
        if self.res is None:                    # 設定畫面
            if k == K_CENTER:
                if ev == EV_CLICK:
                    self.roll()
                    return True
                if ev == EV_DOUBLE:
                    app.go("games", now)
                    return True
                return ev in (EV_PRESS, EV_RELEASE)
            if k == K_LEFT and ev == EV_CLICK:
                self.pos = (self.pos - 1) % NPOS
            elif k == K_RIGHT and ev == EV_CLICK:
                self.pos = (self.pos + 1) % NPOS
            elif k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
                step = 1 if k == K_UP else -1
                if self.pos == 8:
                    if ev == EV_CLICK:
                        self.uniq ^= 1
                else:
                    self.dg[self.pos] = (self.dg[self.pos] + step) % 10
            else:
                return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE)
            return True
        # 結果畫面
        rows = self.rows()
        if k == K_CENTER:
            if ev == EV_CLICK:
                self.roll()                     # 再抽一次
                return True
            if ev == EV_DOUBLE:
                self.res = None
                return True
            return ev in (EV_PRESS, EV_RELEASE)
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_LONG, EV_HOLD):
            self.top = min(max(0, rows - 4), max(0, self.top + (-1 if k == K_UP else 1)))
            return True
        if k in (K_UP, K_DOWN):
            return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE)
        return False                            # 左右單擊 = 切頁

    def cols(self):
        return max(1, 148 // (self.w * 6 + 10))

    def rows(self):
        n = len(self.res)
        cl = self.cols()
        return (n + cl - 1) // cl

    # ---- 畫面 ----
    def draw(self, c, now):
        if self.res is None:
            self.draw_setup(c)
        else:
            self.draw_result(c)

    def box(self, c, x, y, w, h, ch, sel):
        c.frect(x, y, w, h, PANEL)
        if sel:
            c.rect(x, y, w, h, ACC)
        c.text_big(ch, x + 3, y, FG, 2, 6)

    def draw_setup(self, c):
        c.text("範圍", 4, 17, DIM)
        for i in range(6):
            x = 40 + i * 18 + (8 if i >= 3 else 0)
            self.box(c, x, 15, 16, 24, str(self.dg[i]), self.pos == i)
        c.text("~", 94, 20, DIM)
        c.text("個數", 4, 45, DIM)
        for i in (6, 7):
            self.box(c, 40 + (i - 6) * 18, 43, 16, 24, str(self.dg[i]), self.pos == i)
        sel = self.pos == 8
        x = 82
        c.frect(x, 47, 74, 16, PANEL)
        if sel:
            c.rect(x, 47, 74, 16, ACC)
        c.text("重複" if not self.uniq else "不重複", x + 4, 49, GOOD if self.uniq else WARN)
        c.text("左右:位 上下:改 中:抽", 4, 68, FAINT)

    def draw_result(self, c):
        res = self.res
        cl = self.cols()
        cw = self.w * 6 + 10
        rows = self.rows()
        y = COLS_Y
        for r in range(self.top, min(rows, self.top + 4)):
            for j in range(cl):
                i = r * cl + j
                if i >= len(res):
                    break
                c.text(("%0" + str(self.w) + "d") % res[i], 4 + j * cw, y, FG if (i % 2 == 0) else ACC)
            y += 13
        if rows <= 4:
            c.text("中央:再抽 雙擊:設定", 4, 67, FAINT)
        else:
            h = max(6, 52 * 4 // rows)
            c.frect(158, 15 + (52 - h) * self.top // (rows - 4), 2, h, FAINT)
            c.text("%d/%d" % (min(rows, self.top + 4), rows), 4, 67, FAINT)
            c.text_r("共%d個" % len(res), 154, 67, FAINT)
