from app import Page
from keys import *
from ui import *


def lapfmt(ms):
    """圈時間: 1小時內 mm:ss.cc, 超過 h:mm:ss"""
    h, m, s, cs = fmt(ms)
    if h:
        return "%d:%02d:%02d" % (h % 100, m, s)
    return "%02d:%02d.%02d" % (m, s, cs)


def fmt(ms):
    cs = ms // 10
    s, cs = divmod(cs, 100)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return h, m, s, cs


class StopwatchPage(Page):
    title = "碼表"
    reset_label = "重設碼表"

    def __init__(self, app):
        Page.__init__(self, app)
        self.top = 0              # 圈數清單第一列
        self.follow = True        # True: 自動顯示最新兩圈
        self._n = 0

    def on_reset(self, now):
        self.st.sw_reset()
        self.top = 0
        self.follow = True

    def tick(self, now):
        d = self.dirty or self.st.sw_run
        self.dirty = False
        return d

    def scroll(self, d):
        n = len(self.st.sw_laps)
        mx = max(0, n - 2)
        self.top = min(mx, max(0, self.top + d))
        self.follow = self.top >= mx

    def on_key(self, k, ev, now):
        st = self.st
        self.dirty = True
        if k == K_CENTER and ev == EV_CLICK:
            st.sw_toggle(now)
            self.app.toast("計時中" if st.sw_run else "已暫停")
            return True
        if k == K_UP and ev == EV_CLICK:
            if st.sw_lap(now):
                self.app.toast("記圈 #%d" % len(st.sw_laps))
                self.follow = True
            else:
                self.app.toast("計時中才能記圈")
            return True
        if k == K_DOWN and ev == EV_CLICK:
            if st.sw_run:
                self.app.toast("先暫停才能重設")
            else:
                st.sw_reset()
                self.top = 0
                self.follow = True
                self.app.toast("已重設")
            return True
        if k == K_DOWN and ev == EV_DOUBLE:
            st.sw_reset()
            self.top = 0
            self.follow = True
            self.app.toast("已重設")
            return True
        if k in (K_UP, K_DOWN) and ev in (EV_LONG, EV_HOLD):      # 長按上下: 捲動圈數清單
            if len(st.sw_laps) > 2:
                self.scroll(-1 if k == K_UP else 1)
            return True
        if k in (K_UP, K_DOWN):
            return ev in (EV_PRESS, EV_RELEASE, EV_TRIPLE)
        return False

    def draw(self, c, now):
        st = self.st
        h, m, s, cs = fmt(st.sw_elapsed(now))
        col = WARN if st.sw_run else FG
        status = "運行" if st.sw_run else ("暫停" if st.sw_acc else "待命")
        scol = WARN if st.sw_run else DIM
        if h:
            # 超過 1 小時: 縮成 2 倍字, 小數與狀態各放自己的位置, 不互相重疊
            t = "%d:%02d:%02d" % (h % 100, m, s)
            c.text_big(t, 4, 15, col, 2, 6)
            x = 4 + 12 * len(t)
            c.text(".%02d" % cs, x + 2, 27, ACC if st.sw_run else DIM)
            c.text(status, 128, 15, scol)
            if h >= 100:
                c.text("+100h", 122, 27, DIM)
        else:
            c.text_big("%02d" % m, 6, 15, col, 3, 7)
            c.text_big(":", 48, 15, col, 3, 5)
            c.text_big("%02d" % s, 63, 15, col, 3, 7)
            c.text(".%02d" % cs, 110, 35, ACC if st.sw_run else DIM)
            c.text(status, 110, 17, scol)
        laps = st.sw_laps
        n = len(laps)
        if n == 0:
            c.text("中:開始/暫停 上:記圈", 6, 52, DIM)
            c.text("下:重設(暫停時)", 6, 65, DIM)
            return
        if self.follow:
            self.top = max(0, n - 2)
        self.top = min(self.top, max(0, n - 2))
        for r in range(2):
            i = self.top + r
            if i >= n:
                break
            prev = laps[i - 1] if i else 0
            y = 52 + r * 13
            c.text("#%d" % (i + 1), 4, y, DIM)
            c.text(lapfmt(laps[i]), 34, y, FG)
            c.text("+" + lapfmt(laps[i] - prev), 96, y, ACC)
        if n > 2:
            bh = max(6, 26 * 2 // n)
            by = 52 + (26 - bh) * self.top // (n - 2)
            c.frect(158, by, 2, bh, FAINT)
