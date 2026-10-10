"""手機狀態頁: 手機傳來的電量/溫度/網路/藍牙/記憶體/背景運作狀態。上下切換 3 個分頁。"""
from app import Page
from keys import *
from ui import *
import time

NAMES = ("電源", "網路與背景", "記憶體")
NET = ("無網路", "Wi-Fi", "行動網路", "其他")
BUCKET = {10: "活躍", 20: "常用", 30: "偶爾", 40: "很少", 45: "受限", 50: "從未"}
THERMAL = ("正常", "輕微", "中等", "嚴重", "危急", "緊急", "關機")
PLUG = {0: "未充電", 1: "AC 充電", 2: "USB 充電", 4: "無線充電"}


def _bars(level):
    if level < 0:
        return ""
    return "▂▄▆█"[:max(1, min(4, level))] if level > 0 else "▁"


class SysPage(Page):
    title = "手機狀態"
    reset_label = "重新同步"

    def __init__(self, app):
        Page.__init__(self, app)
        self.sub = 0

    def on_reset(self, now):
        self.app.link.send({"c": "sync"})

    def get_title(self):
        return "手機 %s %d/3" % (NAMES[self.sub], self.sub + 1)

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        return d

    def on_key(self, k, ev, now):
        self.dirty = True
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            self.sub = (self.sub + (1 if k == K_DOWN else -1)) % 3
            return True
        if k in (K_UP, K_DOWN, K_CENTER):
            return ev in (EV_PRESS, EV_RELEASE, EV_CLICK, EV_DOUBLE, EV_TRIPLE)
        return False

    def lines(self, now):
        st = self.st
        s = st.sys
        if not s:
            return None
        g = s.get
        if self.sub == 0:
            batt = st.batt
            return (
                ("電量", ("%d%%" % batt if batt >= 0 else "--") + (" 充電中" if st.charging else "")),
                ("充電", PLUG.get(g("pl", 0), "未充電")),
                ("溫度", "%.1f°C" % (g("t", 0) / 10)),
                ("電壓", "%.2fV" % (g("v", 0) / 1000)),
                ("熱狀態", THERMAL[min(g("th", 0), 6)]),
            )
        if self.sub == 1:
            nt = g("nt", 0)
            net = NET[nt] if nt < 4 else "?"
            if nt == 1:
                net += " " + _bars(g("wf", -1))
            return (
                ("網路", net),
                ("藍牙", "開" if g("bt", 0) else "關"),
                ("螢幕", "亮" if g("sc", 1) else "滅"),
                ("省電/Doze", ("開" if g("ps", 0) else "關") + "/" + ("是" if g("ds", 0) else "否")),
                ("背景", BUCKET.get(g("bk", 10), "?") + (" 受限" if g("br", 0) else "")),
            )
        return (
            ("RAM 已用", "%d%%" % g("ra", 0)),
            ("RAM 剩餘", "%.1fGB" % (g("rf", 0) / 1024)),
            ("App 記憶體", "%dMB" % g("ap", 0)),
            ("App 快取", "%dMB" % g("ca", 0)),
            ("儲存剩餘", "%.1fGB" % (g("sf", 0) / 10)),
        )

    def draw(self, c, now):
        rows = self.lines(now)
        if rows is None:
            c.text("等待手機傳送狀態…", 6, 24, DIM)
            c.text("(手機 App 需已連線)", 6, 40, FAINT)
            return
        age = time.ticks_diff(now, self.st.sys_t) // 1000
        col = FG if age < 60 else WARN
        y = TOP_H + 1
        for name, val in rows:
            c.text(name, 6, y, DIM)
            c.text_r(val, 154, y, col)
            y += 12
