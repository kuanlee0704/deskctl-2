"""捷徑頁: 顯示手機 App 設定好的捷徑名單, 中央執行 (手機端依序開 App / 開網址 / 點擊 / 播放)。"""
from app import Page
from keys import *
from ui import *


class ShortcutsPage(Page):
    title = "捷徑"
    reset_label = "重新同步"

    def __init__(self, app):
        Page.__init__(self, app)
        self.sel = 0

    def on_reset(self, now):
        self.app.link.send({"c": "sync"})

    def enter(self, now):
        Page.enter(self, now)
        n = len(self.st.acts)
        if self.sel >= n:
            self.sel = 0

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        return d

    def on_key(self, k, ev, now):
        acts = self.st.acts
        n = len(acts)
        self.dirty = True
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            if n:
                self.sel = (self.sel + (1 if k == K_DOWN else -1)) % n
            return True
        if k == K_CENTER and ev == EV_CLICK:
            app = self.app
            if not app.link.online(now):
                app.toast("手機未連線")
            elif not n:
                app.toast("手機還沒設定捷徑")
            elif app.link.send({"c": "run", "i": self.sel}):
                app.toast("已送出", 900)
            else:
                app.toast("送出失敗")
            return True
        if k in (K_UP, K_DOWN, K_CENTER):
            return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE)
        return False

    def draw(self, c, now):
        acts = self.st.acts
        n = len(acts)
        if not n:
            c.text("手機還沒設定捷徑", 6, 20, DIM)
            c.text("到手機 App 的「捷徑」", 6, 36, FAINT)
            c.text("分頁新增後會出現在這", 6, 50, FAINT)
            return
        rows = 4
        top = min(max(self.sel - 1, 0), max(0, n - rows))
        y = TOP_H + 1
        for i in range(top, min(n, top + rows)):
            if i == self.sel:
                c.frect(2, y - 1, 154, 14, PANEL)
                c.text(">", 5, y, ACC)
            c.text("%d" % (i + 1), 16, y, FAINT)
            c.text(clip_text(c, acts[i], 118), 30, y, FG if i == self.sel else DIM)
            y += 14
        c.text("上下:選  中央:執行", 4, 68, FAINT)
        if n > rows:
            h = 52 * rows // n
            yy = TOP_H + 1 + (52 - h) * top // max(1, n - rows)
            c.frect(158, yy, 2, h, FAINT)
