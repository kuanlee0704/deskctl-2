from app import Page
from ui import *
from state import WEEK


class HomePage(Page):
    title = "首頁"
    reset_label = "重新同步"

    def __init__(self, app):
        Page.__init__(self, app)
        self.mq = None
        self._sec = -1

    def on_reset(self, now):
        self.app.link.send({"c": "sync"})

    def tick(self, now):
        st = self.st
        sec = (now // 1000) if not st.t_synced else st.local(now)[5]
        d = self.dirty or sec != self._sec
        if self.mq is not None and self.mq.animating:
            d = True
        self._sec = sec
        self.dirty = False
        return d

    def draw(self, c, now):
        st = self.st
        cfg = st.cfg
        link = self.app.link
        if self.mq is None:
            self.mq = Marquee(c.font, 84, DIM)
        if st.t_synced:
            y, m, d, hh, mm, ss, wd = st.local(now)
            if not cfg.get("h24"):
                ap = "上午" if hh < 12 else "下午"
                hh = hh % 12 or 12
            else:
                ap = ""
            hm = "%02d:%02d" % (hh, mm)
            c.text_big(hm[:2], 6, 15, FG, 3, 7)
            c.text_big(":", 48, 15, FG if ss % 2 == 0 else FAINT, 3, 5)
            c.text_big(hm[3:], 63, 15, FG, 3, 7)
            c.text("%02d" % ss, 112, 35, ACC)
            if ap:
                c.text(ap, 112, 17, DIM)
            c.text("%d/%02d/%02d %s" % (y, m, d, WEEK[wd]), 6, 52, FG)
        else:
            c.text_big("--:--", 6, 15, FAINT, 3, 7)
            c.text("等待手機校時", 6, 52, DIM)
        # 手機電量
        if st.batt >= 0:
            col = GOOD if st.batt > 30 else (WARN if st.batt > 15 else BAD)
            t = "%d%%" % st.batt
            x = 156 - c.text_w(t)
            c.text(t, x, 52, col)
            x -= 13
            c.icon(IC["bat"], x, 54, col)
            c.frect(x + 1, 55, max(1, 5 * st.batt // 100), 2, col)
            if st.charging:
                c.icon(IC["bolt"], x - 7, 53, WARN)
        else:
            c.text_r("手機 --%", 156, 52, FAINT)
        # 藍牙 + 目前歌曲
        online = link.online(now)
        if online:
            txt, col = "已連線", GOOD
        elif link.connected:
            txt, col = "App 未回應", WARN
        else:
            txt, col = "等待連線", DIM
        c.icon(IC["bt"], 6, 67, col)
        c.text(txt, 16, 66, col)
        if st.has_track and online:
            self.mq.draw(c, 72, 66, "♪ " + st.title, now, color=DIM if st.pl != 1 else ACC)
        else:
            self.mq.text = None
