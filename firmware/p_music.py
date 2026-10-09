import time
from app import Page
from keys import *
from ui import *
from state import PL_PLAY, PL_PAUSE, PL_NONE, RT_BT, RT_WIRED, RT_SPK


class MusicPage(Page):
    title = "音樂"
    reset_label = "重新同步"

    def __init__(self, app):
        Page.__init__(self, app)
        self.mq_t = None
        self.mq_a = None
        self._vol_until = 0
        self._sec = -1

    # ---- 送指令給手機 ----
    def cmd(self, d):
        self.app.link.send(d)

    def on_reset(self, now):
        self.cmd({"c": "sync"})

    def show_vol(self, now):
        self._vol_until = time.ticks_add(now, 1600)

    def seek(self, sign, now):
        st = self.st
        ms = sign * self.st.cfg.get("seek") * 1000
        p = st.pos(now) + ms
        p = max(0, min(p, st.dur) if st.dur else max(0, p))
        st.set_pos(p, now)
        self.cmd({"c": "seek", "d": ms})

    def vol(self, d, now):
        st = self.st
        step = st.cfg.get("vstep")
        nv = max(0, min(st.vol_max, st.vol + d * step))
        if nv != st.vol or d:
            st.vol = nv
            if st.muted and d > 0:
                st.muted = False
        self.cmd({"c": "vol", "d": d * step})
        self.show_vol(now)

    def on_key(self, k, ev, now):
        st = self.st
        app = self.app
        self.dirty = True
        if k == K_CENTER:
            if ev == EV_CLICK:
                self.cmd({"c": "pp"})
                st.set_pos(st.pos(now), now)
                st.pl = PL_PAUSE if st.pl == PL_PLAY else PL_PLAY
                return True
            if ev == EV_DOUBLE:
                st.muted = not st.muted
                self.cmd({"c": "mute"})
                self.show_vol(now)
                app.toast("已靜音" if st.muted else "取消靜音")
                return True
            return ev in (EV_PRESS, EV_RELEASE)
        if k in (K_RIGHT, K_LEFT):
            sign = 1 if k == K_RIGHT else -1
            if ev == EV_CLICK:
                self.seek(sign, now)
            elif ev in (EV_LONG, EV_HOLD):
                self.seek(sign, now)
            elif ev == EV_DOUBLE:
                self.cmd({"c": "next" if sign > 0 else "prev"})
                app.toast("下一首 ▶▶" if sign > 0 else "◀◀ 上一首")
            return True
        if k in (K_UP, K_DOWN):
            sign = 1 if k == K_UP else -1
            if ev in (EV_CLICK, EV_LONG, EV_HOLD):
                self.vol(sign, now)
            elif ev == EV_DOUBLE:
                if k == K_UP:
                    st.shuffle = 0 if st.shuffle else 1
                    self.cmd({"c": "shuffle", "v": st.shuffle})
                    app.toast("隨機播放:" + ("開" if st.shuffle else "關"))
                else:
                    st.repeat = (st.repeat + 1) % 3
                    self.cmd({"c": "repeat", "v": st.repeat})
                    app.toast(("循環:不循環", "循環:清單循環", "循環:單曲循環")[st.repeat])
            return True
        return False

    def tick(self, now):
        st = self.st
        d = self.dirty
        if st.pl == PL_PLAY:
            sec = st.pos(now) // 1000
            if sec != self._sec:
                self._sec = sec
                d = True
        for mq in (self.mq_t, self.mq_a):
            if mq is not None and mq.animating:
                d = True
        if self._vol_until and time.ticks_diff(now, self._vol_until) >= 0:
            self._vol_until = 0
            d = True
        self.dirty = False
        return d

    def draw(self, c, now):
        st = self.st
        link = self.app.link
        if self.mq_t is None:
            self.mq_t = Marquee(c.font, 152, FG)
            self.mq_a = Marquee(c.font, 152, DIM)
        if not link.online(now):
            c.text_c("尚未連上手機 App", 80, 24, DIM)
            c.text_c("請開啟手機「桌邊控制器」", 80, 40, FAINT)
            c.text_c("並確認藍牙已開啟", 80, 53, FAINT)
            return
        # 歌名 / 歌手
        if st.has_track:
            self.mq_t.draw(c, 4, 15, st.title, now, color=FG)
            sub = st.artist if st.artist else st.app
            if st.artist and st.album:
                sub = st.artist + " · " + st.album
            self.mq_a.draw(c, 4, 28, sub, now, color=DIM)
        else:
            self.mq_t.draw(c, 4, 15, "沒有播放中的音樂", now, color=DIM)
            self.mq_a.draw(c, 4, 28, "在手機開啟音樂 App 播放", now, color=FAINT)
        # 進度條 + 時間
        pos = st.pos(now)
        x0, x1, y = 4, 156, 44
        c.frect(x0, y, x1 - x0, 3, FAINT)
        if st.dur:
            w = (x1 - x0) * pos // st.dur
            c.frect(x0, y, w, 3, ACC)
            c.frect(x0 + max(0, w - 1), y - 1, 3, 5, FG)
        c.text(fmt_ms(pos), 4, 48, FG)
        c.text_r(fmt_ms(st.dur) if st.dur else "--:--", 156, 48, DIM)
        # 輸出裝置 (中間)
        if st.route == RT_BT:
            c.icon(IC["head"], 70, 51, GOOD)
        elif st.route == RT_WIRED:
            c.icon(IC["head"], 70, 51, ACC)
        elif st.route == RT_SPK:
            c.icon(IC["phone"], 73, 50, DIM)
        # 控制列
        y = 65
        c.icon(IC["prev"], 4, y, DIM)
        c.icon(IC["pause"] if st.pl == PL_PLAY else IC["play"], 20, y, FG)
        c.icon(IC["next"], 34, y, DIM)
        c.icon(IC["shuf"], 54, y, ACC if st.shuffle else FAINT)
        c.icon(IC["rep"], 70, y, ACC if st.repeat else FAINT)
        if st.repeat == 2:
            c.text("1", 80, y - 2, ACC)
        # 音量
        c.icon(IC["mute"] if st.muted else IC["spk"], 94, y, BAD if st.muted else FG)
        bx, bw = 106, 30
        c.frect(bx, y + 3, bw, 3, FAINT)
        v = 0 if st.muted else st.vol
        c.frect(bx, y + 3, bw * v // max(1, st.vol_max), 3, BAD if st.muted else GOOD)
        pct = 100 * v // max(1, st.vol_max)
        c.text_r("%d" % pct, 157, y - 2, DIM if not st.muted else BAD)
        # 音量大提示
        if self._vol_until:
            c.frect(20, 30, 120, 28, PANEL)
            c.rect(20, 30, 120, 28, ACC)
            c.text("音量", 26, 33, FG)
            c.text_r("靜音" if st.muted else "%d%%" % pct, 134, 33, BAD if st.muted else FG)
            c.frect(26, 49, 108, 5, FAINT)
            c.frect(26, 49, 108 * v // max(1, st.vol_max), 5, BAD if st.muted else GOOD)

