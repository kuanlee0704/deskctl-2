from app import Page
from keys import *
from ui import *
from config import BL_CONTROL


def _dur(v):
    if not v:
        return "永不"
    if v < 60:
        return "%d秒" % v
    if v < 3600:
        return "%d分" % (v // 60)
    return "%d小時" % (v // 3600) if v % 3600 == 0 else "%.1f小時" % (v / 3600)


def _onoff(v):
    return "開" if v else "關"


class SettingsPage(Page):
    title = "快捷設定"
    reset_label = ""

    # (名稱, 設定鍵, 選項值, 顯示函式)
    _ALL = (
        ("時間制", "h24", (1, 0), lambda v: "24小時" if v else "12小時"),
        ("首頁顯示秒", "secs", (0, 1), _onoff),
        ("狀態欄顯示秒", "tsecs", (0, 1), _onoff),
        ("背光亮度", "bright", (30, 50, 80, 100), lambda v: "%d%%" % v),
        ("閒置省電", "pwr", (1, 0), _onoff),
        ("變暗時間", "dimt", (15, 30, 60, 120, 300, 600), _dur),
        ("低亮度/時鐘待機", "lowt", (60, 120, 300, 600, 1200, 1800, 3600), _dur),
        ("自動熄屏", "idle", (0, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200), _dur),
        ("深度睡眠(實驗)", "lsleep", (0, 1), _onoff),
        ("新訊息喚醒螢幕", "msgw", (1, 0), _onoff),
        ("狀態欄訊息圖示", "msgt", (1, 0), _onoff),
        ("跳轉秒數", "seek", (5, 10, 15, 30), lambda v: "%d秒" % v),
        ("音量步進", "vstep", (1, 2, 3), lambda v: "%d格" % v),
        ("藍牙診斷(中央歸零)", None, None, None),
        ("重新連線藍牙", None, None, None),
        ("螢幕方向校正", None, None, None),
        ("還原所有設定", None, None, None),
    )

    ITEMS = tuple(i for i in _ALL if BL_CONTROL or i[1] not in ("bright", "dimt"))

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
        if name.startswith("藍牙診斷"):
            lk = app.link
            lk.n_conn = lk.n_disc = 0
            app.toast("診斷已歸零")
        elif name == "重新連線藍牙":
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
            c.text(name if not name.startswith("藍牙診斷") else "藍牙診斷", 16, y, FG if i == self.sel else DIM)
            if key is not None:
                c.text_r(fmt(cfg.get(key)), 154, y, ACC)
            elif name.startswith("藍牙診斷"):
                lk = self.app.link
                c.text_r("連%d 斷%d %ds" % (lk.n_conn, lk.n_disc, lk.last_up), 154, y, WARN)
            y += 14
        c.text("上下:選  左右:改  中:執行", 4, 68, FAINT)
        if n > rows:
            h = 52 * rows // n
            yy = TOP_H + 1 + (52 - h) * top // max(1, n - rows)
            c.frect(158, yy, 2, h, FAINT)
