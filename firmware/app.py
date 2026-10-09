"""應用程式核心: 頁面管理、全域手勢、疊加層。"""
import time
import registry
from keys import *
from config import *
import ui
from ui import *


class Page:
    id = ""
    title = ""
    fast = ()                # 這些方向鍵不等連按判定 (遊戲用)
    reset_label = "重設此頁"

    def __init__(self, app):
        self.app = app
        self.st = app.st
        self.dirty = True

    def enter(self, now):
        self.dirty = True

    def leave(self):
        pass

    def on_key(self, k, ev, now):
        return False

    def on_reset(self, now):
        pass

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        return d

    def draw(self, c, now):
        pass

    def get_title(self):
        return self.title


class MenuPage(Page):
    """目錄: 上下選, 中央進入, 右鍵加入/移出快捷切換(★), 左鍵返回"""
    id = "menu"
    title = "目錄"
    reset_label = ""

    def __init__(self, app):
        Page.__init__(self, app)
        self.sel = 0
        self.items = registry.MAIN
        self.src = "home"

    def open(self, src, now):
        self.src = src
        self.sel = 0
        for i, p in enumerate(self.items):
            if p[0] == src:
                self.sel = i
        self.dirty = True

    def on_key(self, k, ev, now):
        n = len(self.items)
        if k in (K_UP, K_DOWN) and ev in (EV_CLICK, EV_HOLD, EV_LONG):
            self.sel = (self.sel + (1 if k == K_DOWN else -1)) % n
        elif k == K_CENTER and ev == EV_CLICK:
            self.app.go(self.items[self.sel][0])
            return True
        elif k == K_LEFT and ev == EV_CLICK:
            self.app.go(self.src)
            return True
        elif k == K_RIGHT and ev == EV_CLICK:
            pid = self.items[self.sel][0]
            ring = list(self.app.st.cfg.get("ring"))
            if pid in ring:
                if len(ring) > 1:
                    ring.remove(pid)
                    self.app.toast("已移出快捷切換")
            else:
                ring.append(pid)
                self.app.toast("已加入快捷切換 ★")
            order = [p[0] for p in registry.MAIN]
            ring.sort(key=lambda x: order.index(x) if x in order else 99)
            self.app.st.cfg.set("ring", ring)
        else:
            return True if ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE) else False
        self.dirty = True
        return True

    def draw(self, c, now):
        n = len(self.items)
        rows = 4
        top = min(max(self.sel - 1, 0), max(0, n - rows))
        ring = self.app.st.cfg.get("ring")
        y = TOP_H + 1
        for i in range(top, min(n, top + rows)):
            pid, name = self.items[i][0], self.items[i][1]
            if i == self.sel:
                c.frect(2, y - 1, 156, 14, PANEL)
                c.text(">", 5, y, ACC)
            c.text(name, 16, y, FG if i == self.sel else DIM)
            if pid in ring:
                c.text("★", 140, y, WARN)
            if pid == self.src:
                c.text("目前", 100, y, FAINT)
            y += 14
        c.text("中:進入  右:★快捷  左:返回", 4, 68, FAINT)
        # 捲動指示
        if n > rows:
            h = 52 * rows // n
            yy = TOP_H + 1 + (52 - h) * top // max(1, n - rows)
            c.frect(158, yy, 2, h, FAINT)


class App:
    def __init__(self, canvas, lcd, keys, link, st, now, reboot=None):
        self.c = canvas
        self.lcd = lcd
        self.keys = keys
        self.link = link
        self.st = st
        self.insts = {}
        self.cur = None
        self.cur_id = ""
        self.prev_id = ""
        self.menu_src = "home"
        self._toast = None
        self._toast_until = 0
        self._last_draw = 0
        self._sig = None
        self.idle_t = now
        self.sleeping = False
        self._swallow = -1
        self.rebooting = False
        self._reboot = reboot
        self.menu = MenuPage(self)
        self.insts["menu"] = self.menu
        self.go("home", now=now)

    # ---------- 頁面 ----------
    def page(self, pid):
        p = self.insts.get(pid)
        if p is None:
            info = registry.info(pid)
            if info is None:
                return None
            mod = __import__(info[2])
            p = getattr(mod, info[3])(self)
            p.id = pid
            if not p.title:
                p.title = info[1]
            self.insts[pid] = p
        return p

    def go(self, pid, now=None):
        if now is None:
            now = time.ticks_ms()
        p = self.page(pid)
        if p is None:
            return
        if self.cur is not None:
            self.cur.leave()
            if self.cur_id != "menu" and self.cur_id != pid:
                self.prev_id = self.cur_id
        self.cur = p
        self.cur_id = pid
        self.keys.set_fast(p.fast)
        p.enter(now)
        self._sig = None

    def ring_step(self, d):
        ring = [r for r in self.st.cfg.get("ring") if registry.info(r)]
        if not ring:
            return
        if self.cur_id in ring:
            i = (ring.index(self.cur_id) + d) % len(ring)
        else:
            i = 0
        self.go(ring[i])

    def toast(self, text, ms=1300):
        self._toast = text
        self._toast_until = time.ticks_add(time.ticks_ms(), ms)
        self._sig = None

    # ---------- 按鍵 ----------
    def handle(self, k, ev, now):
        self.idle_t = now
        if ev == EV_VLONG:
            if k == K_CENTER:
                self.do_reboot()
            elif self.cur.reset_label:
                self.cur.on_reset(now)
                self.toast(self.cur.reset_label + " ✓")
            return
        if k == K_CENTER:
            if ev == EV_LONG:
                if self.cur_id == "menu":
                    self.go(self.menu.src, now)
                else:
                    self.menu.open(self.cur_id, now)
                    self.go("menu", now)
                return
            if ev == EV_TRIPLE:
                if self.prev_id and self.prev_id != self.cur_id:
                    self.go(self.prev_id, now)
                return
        if self.cur.on_key(k, ev, now):
            return
        if ev == EV_CLICK and k in (K_LEFT, K_RIGHT):
            self.ring_step(1 if k == K_RIGHT else -1)

    def do_reboot(self):
        self.rebooting = True
        self.c.fill(BG)
        self.c.text_c("重啟中…", 80, 34, FG)
        self.lcd.show(self.c.buf)
        self.st.cfg.save()
        time.sleep_ms(300)
        if self._reboot:
            self._reboot()

    # ---------- 主迴圈的一步 ----------
    def step(self, now):
        evs = self.keys.poll(now)
        for k, ev in evs:
            if self.sleeping:
                if ev == EV_PRESS:
                    self.wake(now)
                    self._swallow = k
                continue
            if self._swallow == k:
                if ev == EV_RELEASE or ev in (EV_CLICK, EV_DOUBLE, EV_TRIPLE):
                    self._swallow = -1
                continue
            self.handle(k, ev, now)
            if self.rebooting:
                return
        # 手機訊息
        for m in self.link.poll(now):
            self.st.apply(m, now)
            self.cur.dirty = True
            if m.get("k") == "ping":
                self.link.send({"c": "pong"})
        self.link.pump()
        self.idle_check(now)
        self.render(now)

    def wake(self, now):
        self.sleeping = False
        self.idle_t = now
        self.lcd.backlight(True)
        self._sig = None

    def idle_check(self, now):
        idle = self.st.cfg.get("idle")
        if idle and not self.sleeping and not self.keys.any_down():
            if time.ticks_diff(now, self.idle_t) > idle * 1000:
                self.sleeping = True
                self.c.fill(1)
                self.lcd.show(self.c.buf)
                self.lcd.backlight(False)

    def render(self, now):
        if self.sleeping:
            return
        if time.ticks_diff(now, self._last_draw) < FPS_MS:
            return
        st, link = self.st, self.link
        minute = st.local(now)[4] if st.t_synced else -1
        sig = (link.online(now), link.connected, st.sw_run, minute, st.t_synced,
               st.batt, self._toast is not None)
        need = self.cur.tick(now)
        if sig != self._sig:
            self._sig = sig
            need = True
        holding = False
        for k in range(5):
            if self.keys.held_ms(k, now) >= SHOW_VLONG_MS:
                holding = True
        if self._toast is not None and time.ticks_diff(now, self._toast_until) >= 0:
            self._toast = None
            need = True
        if not (need or holding or self._toast is not None):
            return
        self._last_draw = now
        self.draw(now, holding)
        self.lcd.show(self.c.buf)

    def draw(self, now, holding=False):
        c = self.c
        c.fill(BG)
        try:
            self.cur.draw(c, now)
        finally:
            ui.draw_topbar(c, self.cur.get_title(), self.st, self.link, now,
                           self.st.cfg.get("h24"))
        if holding:
            self.draw_hold(c, now)
        elif self._toast is not None:
            w = c.text_w(self._toast) + 12
            x = (160 - w) // 2
            c.frect(x, 62, w, 16, PANEL)
            c.rect(x, 62, w, 16, ACC)
            c.text(self._toast, x + 6, 64, FG)

    def draw_hold(self, c, now):
        for k in range(5):
            h = self.keys.held_ms(k, now)
            if h >= SHOW_VLONG_MS:
                if k == K_CENTER:
                    label = "繼續按住:重啟裝置"
                    col = BAD
                elif self.cur.reset_label:
                    label = "繼續按住:" + self.cur.reset_label
                    col = WARN
                else:
                    return
                frac = min(1.0, (h - SHOW_VLONG_MS) / (VLONG_MS - SHOW_VLONG_MS))
                c.frect(4, 56, 152, 22, PANEL)
                c.rect(4, 56, 152, 22, col)
                c.text_c(label, 80, 58, FG)
                c.rect(10, 72, 140, 4, FAINT)
                c.frect(10, 72, int(140 * frac), 4, col)
                return
