from app import Page
from keys import *
from ui import *


def _onoff(v):
    return "開" if v else "關"


class SettingsPage(Page):
    title = "快捷設定"
    reset_label = ""

    # (名稱, 設定鍵, 選項值, 顯示函式)
    ITEMS = (
        ("時間制", "h24", (1, 0), lambda v: "24小時" if v else "12小時"),
        ("首頁顯示秒", "secs", (0, 1), _onoff),
        ("自動熄屏", "idle", (0, 30, 60, 300), lambda v: "永不" if not v else ("%d秒" % v if v < 60 else "%d分" % (v // 60))),
        ("跳轉秒數", "seek", (5, 10, 15, 30), lambda v: "%d秒" % v),
        ("音量步進", "vstep", (1, 2, 3), lambda v: "%d格" % v),
        ("重新連線藍牙", None, None, None),
        ("螢幕方向校正", None, None, None),
        ("還原所有設定", None, None, None),
    )

    def __init__(self, app):
        Page.__init__(self, app)
        self.sel = 0
        self._confirm = 0

    def change(self, d, now):
        name, key, opts, fmt = self.ITEMS[self.sel]
        if key is None:
            return
        cfg = self.st.cfg
        cur = cfg.get(key)
        i = opts.index(cur) if cur in opts else 0
        cfg.set(key, opts[(i + d) % len(opts)])

    def act(self, now):
        name, key, opts, fmt = self.ITEMS[self.sel]
        app = self.app
        if key is not None:
            self.change(1, now)
            return
        if name == "重新連線藍牙":
            app.link.disconnect()
            app.toast("已中斷, 等手機重連")
        elif name == "螢幕方向校正":
            self.st.cfg.set("calib", 0)
            app.toast("重啟後進入校正")
            app.do_reboot()
        else:
            if self._confirm:
                self.st.cfg.reset()
                self._confirm = 0
                app.toast("已還原")
            else:
                self._confirm = 1
                app.toast("再按中央確認還原")

    def on_key(self, k, ev, now):
        n = len(self.ITEMS)
        self.dirty = True
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            self.sel = (self.sel + (1 if k == K_DOWN else -1)) % n
            self._confirm = 0
            return True
        if k in (K_LEFT, K_RIGHT) and ev == EV_CLICK:
            self.change(1 if k == K_RIGHT else -1, now)
            return True
        if k == K_CENTER and ev == EV_CLICK:
            self.act(now)
            return True
        return ev in (EV_PRESS, EV_RELEASE)

    def draw(self, c, now):
        n = len(self.ITEMS)
        rows = 4
        top = min(max(self.sel - 1, 0), max(0, n - rows))
        y = TOP_H + 1
        cfg = self.st.cfg
        for i in range(top, min(n, top + rows)):
            name, key, opts, fmt = self.ITEMS[i]
            if i == self.sel:
                c.frect(2, y - 1, 156, 14, PANEL)
                c.text(">", 5, y, ACC)
            c.text(name, 16, y, FG if i == self.sel else DIM)
            if key is not None:
                c.text_r(fmt(cfg.get(key)), 154, y, ACC)
            y += 14
        c.text("上下:選  左右:改  中:執行", 4, 68, FAINT)
        if n > rows:
            h = 52 * rows // n
            yy = TOP_H + 1 + (52 - h) * top // max(1, n - rows)
            c.frect(158, yy, 2, h, FAINT)
