"""手機訊息頁: 顯示手機收到的通知 (LINE、簡訊…), 手機 App 需開啟「通知存取」與「手機訊息」。
列表: 上下選 中央看內容; 內容: 上下捲動 中央返回; 方向鍵長按 5 秒(重設)=清除此則/全部。"""
from app import Page
from keys import *
from ui import *
import time

ROWS = 4
LINES = 4


def age_str(ms):
    s = ms // 1000
    if s < 60:
        return "剛剛"
    if s < 3600:
        return "%d分" % (s // 60)
    return "%d時" % (s // 3600)


def wrap(c, s, w):
    out = []
    cur = ""
    for ch in s:
        if ch == "\n":
            out.append(cur)
            cur = ""
        elif c.text_w(cur + ch) > w:
            out.append(cur)
            cur = ch
        else:
            cur += ch
    if cur or not out:
        out.append(cur)
    return out


class MsgPage(Page):
    title = "手機訊息"
    reset_label = "清除訊息"

    def __init__(self, app):
        Page.__init__(self, app)
        self.sel = 0
        self.detail = False
        self.scroll = 0
        self._lines = None
        self._age_t = 0

    def enter(self, now):
        Page.enter(self, now)
        self.detail = False
        for x in self.st.msgs:
            x[5] = False                      # 打開頁面即視為已讀
        if self.sel >= len(self.st.msgs):
            self.sel = 0

    def get_title(self):
        n = len(self.st.msgs)
        return "手機訊息 %d/%d" % (self.sel + 1, n) if n else "手機訊息"

    def on_reset(self, now):
        msgs = self.st.msgs
        if self.detail and msgs:
            del msgs[self.sel]
            self.detail = False
            self.sel = max(0, min(self.sel, len(msgs) - 1))
            self.app.toast("已刪除", 800)
        else:
            del msgs[:]
            self.sel = 0
            self.app.toast("已清除全部", 800)

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        for x in self.st.msgs:                # 新訊息在此頁到達 -> 直接視為已讀
            x[5] = False
        if time.ticks_diff(now, self._age_t) > 20000:
            self._age_t = now
            d = True                          # 「幾分鐘前」定時更新
        return d

    def on_key(self, k, ev, now):
        msgs = self.st.msgs
        n = len(msgs)
        self.dirty = True
        if self.detail:
            if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
                self.scroll = max(0, self.scroll + (1 if k == K_DOWN else -1))
                return True
            if k == K_CENTER and ev == EV_CLICK:
                self.detail = False
                return True
        else:
            if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
                if n:
                    self.sel = (self.sel + (1 if k == K_DOWN else -1)) % n
                return True
            if k == K_CENTER and ev == EV_CLICK and n:
                self.detail = True
                self.scroll = 0
                self._lines = None
                return True
        if k in (K_UP, K_DOWN, K_CENTER):
            return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE)
        return False

    def draw(self, c, now):
        msgs = self.st.msgs
        n = len(msgs)
        if not n:
            c.text("沒有手機訊息", 6, 20, DIM)
            c.text("手機 App 需開通知存取", 6, 38, FAINT)
            c.text("並開啟「手機訊息」", 6, 52, FAINT)
            return
        if self.sel >= n:
            self.sel = n - 1
        if self.detail:
            self.draw_detail(c, now, msgs[self.sel])
            return
        top = min(max(self.sel - 1, 0), max(0, n - ROWS))
        y = TOP_H + 1
        for i in range(top, min(n, top + ROWS)):
            m = msgs[i]
            if i == self.sel:
                c.frect(2, y - 1, 154, 14, PANEL)
            if m[5]:
                c.frect(5, y + 4, 4, 4, PINK)
            ap = clip_text(c, m[1], 40)
            c.text(ap, 12, y, ACC)
            x = 12 + c.text_w(ap) + 4
            c.text(clip_text(c, m[2] or m[3], 156 - 24 - x), x, y, FG if i == self.sel else DIM)
            c.text_r(age_str(time.ticks_diff(now, m[4])), 154, y, FAINT)
            y += 14
        c.text("上下:選  中央:看內容", 4, 68, FAINT)
        if n > ROWS:
            h = 52 * ROWS // n
            yy = TOP_H + 1 + (52 - h) * top // max(1, n - ROWS)
            c.frect(158, yy, 2, h, FAINT)

    def draw_detail(self, c, now, m):
        c.text(clip_text(c, m[1], 90), 4, TOP_H + 1, ACC)
        c.text_r(age_str(time.ticks_diff(now, m[4])), 154, TOP_H + 1, FAINT)
        if self._lines is None:
            body = (m[2] + "\n" if m[2] else "") + m[3]
            self._lines = wrap(c, body, 148)
        ls = self._lines
        self.scroll = min(self.scroll, max(0, len(ls) - LINES))
        y = TOP_H + 14
        for i in range(self.scroll, min(len(ls), self.scroll + LINES)):
            c.text(ls[i], 4, y, FG)
            y += 12
        if len(ls) > LINES:
            h = max(4, 48 * LINES // len(ls))
            yy = TOP_H + 14 + (48 - h) * self.scroll // max(1, len(ls) - LINES)
            c.frect(158, yy, 2, h, FAINT)
