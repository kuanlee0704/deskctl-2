"""音樂控制 (首頁與音樂頁共用)。把手勢轉成指令送給手機 App。"""
import time
from keys import *
from state import PL_PLAY, PL_PAUSE
from ui import *


class MusicCtl:
    def __init__(self, app):
        self.app = app
        self.st = app.st
        self.vol_until = 0

    def cmd(self, d):
        if not self.app.link.send(d):
            self.app.toast("手機未連線")
            return False
        return True

    def show_vol(self, now):
        self.vol_until = time.ticks_add(now, 1600)

    def vol_active(self, now):
        if self.vol_until and time.ticks_diff(now, self.vol_until) >= 0:
            self.vol_until = 0
            return True          # 剛好結束 -> 需要重畫一次把提示擦掉
        return bool(self.vol_until)

    def seek(self, sign, now):
        st = self.st
        ms = sign * st.cfg.get("seek") * 1000
        p = st.pos(now) + ms
        p = max(0, min(p, st.dur) if st.dur else p)
        st.set_pos(p, now)
        self.cmd({"c": "seek", "d": ms})

    def vol(self, d, now):
        st = self.st
        step = st.cfg.get("vstep")
        st.vol = max(0, min(st.vol_max, st.vol + d * step))
        if st.muted and d > 0:
            st.muted = False
        self.cmd({"c": "vol", "d": d * step})
        self.show_vol(now)

    def handle(self, k, ev, now, home=False):
        """處理一個按鍵事件; 回傳 True 表示已處理。
        home=True 時左/右『短按』不處理(留給首頁的快捷切換), 其餘功能與音樂頁相同。"""
        st = self.st
        app = self.app
        if k == K_CENTER:
            if ev == EV_CLICK:
                if self.cmd({"c": "pp"}):
                    st.set_pos(st.pos(now), now)
                    st.pl = PL_PAUSE if st.pl == PL_PLAY else PL_PLAY
                return True
            if ev == EV_DOUBLE:
                if self.cmd({"c": "mute"}):
                    st.muted = not st.muted
                    self.show_vol(now)
                    app.toast("已靜音" if st.muted else "取消靜音")
                return True
            return ev in (EV_PRESS, EV_RELEASE)
        if k in (K_RIGHT, K_LEFT):
            sign = 1 if k == K_RIGHT else -1
            if ev == EV_CLICK:
                if home:
                    return False
                self.seek(sign, now)
            elif ev in (EV_LONG, EV_HOLD):
                self.seek(sign, now)
            elif ev == EV_DOUBLE:
                if self.cmd({"c": "next" if sign > 0 else "prev"}):
                    app.toast("下一首 ▶▶" if sign > 0 else "◀◀ 上一首")
            return ev != EV_CLICK or not home
        if k in (K_UP, K_DOWN):
            sign = 1 if k == K_UP else -1
            if ev in (EV_CLICK, EV_LONG, EV_HOLD):
                self.vol(sign, now)
            elif ev == EV_DOUBLE:
                if k == K_UP:
                    nv = 0 if st.shuffle else 1
                    if self.cmd({"c": "shuffle", "v": nv}):
                        st.shuffle = nv
                        app.toast("隨機播放:" + ("開" if nv else "關"))
                else:
                    nv = (st.repeat + 1) % 3
                    if self.cmd({"c": "repeat", "v": nv}):
                        st.repeat = nv
                        app.toast(("循環:不循環", "循環:清單循環", "循環:單曲循環")[nv])
            return True
        return False

    def draw_vol(self, c, now):
        """音量大提示框 (只在調音量後 1.6 秒內顯示)"""
        st = self.st
        v = 0 if st.muted else st.vol
        pct = 100 * v // max(1, st.vol_max)
        c.frect(20, 30, 120, 28, PANEL)
        c.rect(20, 30, 120, 28, ACC)
        c.text("音量", 26, 33, FG)
        c.text_r("靜音" if st.muted else "%d%%" % pct, 134, 33, BAD if st.muted else FG)
        c.frect(26, 49, 108, 5, FAINT)
        c.frect(26, 49, 108 * v // max(1, st.vol_max), 5, BAD if st.muted else GOOD)
