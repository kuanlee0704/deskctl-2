from app import Page
from keys import *
from ui import *


def fmt(ms):
    cs = ms // 10
    s, cs = divmod(cs, 100)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return h, m, s, cs


class StopwatchPage(Page):
    title = "碼表"
    reset_label = "重設碼表"

    def on_reset(self, now):
        self.st.sw_reset()

    def tick(self, now):
        d = self.dirty or self.st.sw_run
        self.dirty = False
        return d

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
            else:
                self.app.toast("計時中才能記圈")
            return True
        if k == K_DOWN and ev == EV_CLICK:
            if st.sw_run:
                self.app.toast("先暫停才能重設")
            else:
                st.sw_reset()
                self.app.toast("已重設")
            return True
        if k == K_DOWN and ev == EV_DOUBLE:
            st.sw_reset()
            self.app.toast("已重設")
            return True
        return False

    def draw(self, c, now):
        st = self.st
        h, m, s, cs = fmt(st.sw_elapsed(now))
        col = WARN if st.sw_run else FG
        if h:
            t = "%d:%02d:%02d" % (h, m, s)
            c.text_big(t, 6, 17, col, 2, 7)
            c.text(".%02d" % cs, 6 + 7 * 2 * len(t) // 1 + 2, 24, DIM)
        else:
            c.text_big("%02d" % m, 6, 15, col, 3, 7)
            c.text_big(":", 48, 15, col, 3, 5)
            c.text_big("%02d" % s, 63, 15, col, 3, 7)
            c.text(".%02d" % cs, 110, 35, ACC if st.sw_run else DIM)
        c.text("運行" if st.sw_run else ("暫停" if st.sw_acc else "待命"), 110, 17,
               WARN if st.sw_run else DIM)
        laps = st.sw_laps
        n = len(laps)
        if n == 0:
            c.text("中:開始/暫停 上:記圈", 6, 52, DIM)
            c.text("下:重設(暫停時)", 6, 65, DIM)
        else:
            for i in range(max(0, n - 2), n):
                prev = laps[i - 1] if i else 0
                _, lm, ls, lc = fmt(laps[i])
                _, dm, ds, dc = fmt(laps[i] - prev)
                y = 52 + (i - max(0, n - 2)) * 13
                c.text("#%d" % (i + 1), 6, y, DIM)
                c.text("%02d:%02d.%02d" % (lm, ls, lc), 30, y, FG)
                c.text("+%02d:%02d.%02d" % (dm, ds, dc), 96, y, ACC)
