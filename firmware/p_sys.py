"""手機狀態頁 (4 個分頁, 上下切換): 電源 / 連線 / 背景 / 資源。
每頁只放最重要的幾項, 用顏色表示好壞: 綠=正常 黃=注意 紅=有問題 灰=資料過期。"""
from app import Page
from keys import *
from ui import *
import time

NAMES = ("電源", "連線", "背景", "資源")
N = 4
BUCKET = {10: "活躍", 20: "常用", 30: "偶爾", 40: "很少", 45: "受限", 50: "從未"}
THERMAL = ("正常", "微熱", "偏熱", "過熱", "危險", "危險", "危險")
PLUG = {0: "", 1: "AC", 2: "USB", 4: "無線"}
STALE_S = 60


def _bucket_name(b):
    for k in (10, 20, 30, 40, 45, 50):
        if b <= k:
            return BUCKET[k]
    return "從未"


def _bucket_col(b):
    return GOOD if b <= 20 else (WARN if b <= 30 else BAD)


class SysPage(Page):
    title = "手機狀態"
    reset_label = "重新同步"

    def __init__(self, app):
        Page.__init__(self, app)
        self.sub = 0

    def on_reset(self, now):
        self.app.link.send({"c": "sync"})

    def get_title(self):
        return "手機·%s %d/%d" % (NAMES[self.sub], self.sub + 1, N)

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        return d

    def on_key(self, k, ev, now):
        self.dirty = True
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            self.sub = (self.sub + (1 if k == K_DOWN else -1)) % N
            return True
        if k in (K_UP, K_DOWN, K_CENTER):
            return ev in (EV_PRESS, EV_RELEASE, EV_CLICK, EV_DOUBLE, EV_TRIPLE)
        return False

    # ---------- 小元件 ----------
    def _row(self, c, y, name, val, col):
        c.text(name, 6, y, DIM)
        c.text_r(val, 154, y, col)

    def _bar(self, c, y, pct, col, h=7):
        pct = max(0, min(100, pct))
        c.frect(6, y, 148, h, FAINT)
        w = 148 * pct // 100
        if pct > 0:
            c.frect(6, y, max(2, w), h, col)

    def _sig(self, c, x, y, level, col):
        """訊號強度 4 格 (level 0~4), 由左至右漸高"""
        for i in range(4):
            h = 3 + i * 2
            c.frect(x + i * 4, y + 9 - h, 3, h, col if i < level else FAINT)

    def _tile(self, c, x, y, label, val, col):
        c.frect(x, y, 74, 26, PANEL)
        c.text(label, x + 4, y + 1, DIM)
        c.text(val, x + 4, y + 13, col)

    # ---------- 各分頁 ----------
    def pg_power(self, c, g, ok):
        st = self.st
        b = st.batt
        col = DIM if not ok else (GOOD if b > 40 else (WARN if b > 20 else BAD))
        if b < 0:
            c.text_big("--", 6, 15, DIM, 2)
        else:
            c.text_big("%d%%" % b, 6, 15, col, 2)
        if st.charging:
            c.icon(IC["bolt"], 100, 17, GOOD if ok else DIM)
            c.text_r(PLUG.get(g("pl", 0), "") + "充電", 154, 15, GOOD if ok else DIM)
        else:
            c.text_r("使用電池", 154, 15, DIM)
        self._bar(c, 42, max(b, 0), col, 8)
        t = g("t", 0) / 10
        th = min(g("th", 0), 6)
        tc = (GOOD if t < 38 else (WARN if t < 43 else BAD)) if ok else DIM
        if th >= 3:
            tc = BAD
        c.text("溫度", 6, 56, DIM)
        c.text("%.1f°C" % t, 34, 56, tc)
        c.text_r("%.2fV" % (g("v", 0) / 1000), 154, 56, FG if ok else DIM)
        if th >= 2:
            c.text("手機" + THERMAL[th], 6, 68, BAD if th >= 3 else WARN)

    def pg_conn(self, c, g, ok):
        nt = g("nt", 0)
        names = ("無網路", "Wi-Fi", "行動網路", "其他網路")
        ncol = (BAD if nt == 0 else GOOD) if ok else DIM
        self._tile(c, 4, 15, "網路", names[nt] if nt < 4 else "?", ncol)
        if nt == 1:
            self._sig(c, 58, 17, g("wf", 0), ncol)
        bt = g("bt", 0)
        self._tile(c, 82, 15, "手機藍牙", "開啟" if bt else "關閉", (GOOD if bt else BAD) if ok else DIM)
        sc = g("sc", 1)
        self._tile(c, 4, 44, "手機螢幕", "亮著" if sc else "已熄滅", (FG if sc else DIM))
        link = self.app.link
        on = link.online(time.ticks_ms())
        self._tile(c, 82, 44, "與控制器", "已連線" if on else "斷線", GOOD if on else BAD)

    def pg_bg(self, c, g, ok):
        br = g("br", 0)
        bk = g("bk", 10)
        ps = g("ps", 0)
        ds = g("ds", 0)
        bad = br or bk > 30
        warn = ps or ds or bk > 20
        if not ok:
            vt, vc = "資料過期", DIM
        elif bad:
            vt, vc = "背景可能被限制", BAD
        elif warn:
            vt, vc = "背景需留意", WARN
        else:
            vt, vc = "背景運作正常", GOOD
        c.frect(4, 14, 152, 13, PANEL)
        c.text_c(vt, 80, 14, vc)
        self._row(c, 29, "省電模式", "開" if ps else "關", DIM if not ok else (WARN if ps else GOOD))
        self._row(c, 42, "Doze 休眠", "是" if ds else "否", DIM if not ok else (WARN if ds else GOOD))
        self._row(c, 55, "背景限制", "是" if br else "否", DIM if not ok else (BAD if br else GOOD))
        self._row(c, 68, "App 待機分組", _bucket_name(bk), DIM if not ok else _bucket_col(bk))

    def pg_res(self, c, g, ok):
        ra = g("ra", 0)
        rc = DIM if not ok else (BAD if ra >= 90 else (WARN if ra >= 80 else GOOD))
        c.text("RAM 已用", 6, 14, DIM)
        c.text_r("%d%%  剩%.1fG" % (ra, g("rf", 0) / 1024), 154, 14, rc)
        self._bar(c, 28, ra, rc, 6)
        self._row(c, 38, "App 記憶體", "%dMB" % g("ap", 0), FG if ok else DIM)
        self._row(c, 51, "App 快取", "%dMB" % g("ca", 0), FG if ok else DIM)
        sf = g("sf", 0) / 10
        self._row(c, 64, "儲存剩餘", "%.1fGB" % sf, DIM if not ok else (BAD if sf < 2 else (WARN if sf < 5 else FG)))

    def draw(self, c, now):
        st = self.st
        s = st.sys
        if not s:
            c.text("等待手機傳送狀態…", 6, 24, DIM)
            c.text("(手機 App 需已連線)", 6, 40, FAINT)
            return
        ok = time.ticks_diff(now, st.sys_t) // 1000 < STALE_S
        g = s.get
        (self.pg_power, self.pg_conn, self.pg_bg, self.pg_res)[self.sub](c, g, ok)
