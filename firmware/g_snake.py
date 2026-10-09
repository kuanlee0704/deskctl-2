import time
import random
from app import Page
from keys import *
from ui import *

COLS, ROWS, CS = 40, 16, 4
OY = 14


class SnakePage(Page):
    title = "貪吃蛇"
    fast = (K_LEFT, K_UP, K_DOWN, K_RIGHT)
    reset_label = "重新開始"

    def __init__(self, app):
        Page.__init__(self, app)
        self.new()

    def new(self):
        self.body = [(20, 8), (19, 8), (18, 8)]
        self.dir = (1, 0)
        self.turn = []
        self.over = False
        self.paused = False
        self.score = 0
        self.t = 0
        self.food = self.place()

    def place(self):
        occ = set(self.body)
        while True:
            f = (random.randrange(COLS), random.randrange(ROWS))
            if f not in occ:
                return f

    def enter(self, now):
        self.t = now
        self.dirty = True

    def on_reset(self, now):
        self.new()
        self.t = now

    def get_title(self):
        return "貪吃蛇 %d" % self.score

    def on_key(self, k, ev, now):
        if k == K_CENTER:
            if ev == EV_CLICK:
                if self.over:
                    self.new()
                    self.t = now
                else:
                    self.paused = not self.paused
                self.dirty = True
                return True
            if ev == EV_DOUBLE:
                self.app.go("games", now)
                return True
            return ev in (EV_PRESS, EV_RELEASE)
        if ev == EV_PRESS and not self.over and not self.paused:
            d = {K_LEFT: (-1, 0), K_RIGHT: (1, 0), K_UP: (0, -1), K_DOWN: (0, 1)}.get(k)
            if d and len(self.turn) < 2:
                last = self.turn[-1] if self.turn else self.dir
                if (d[0] + last[0], d[1] + last[1]) != (0, 0) and d != last:
                    self.turn.append(d)
            return True
        return ev in (EV_PRESS, EV_CLICK, EV_RELEASE, EV_LONG, EV_HOLD)

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        if self.over or self.paused:
            return d
        interval = max(55, 170 - self.score * 5)
        if time.ticks_diff(now, self.t) >= interval:
            self.t = now
            self.step()
            return True
        return d

    def step(self):
        if self.turn:
            self.dir = self.turn.pop(0)
        hx, hy = self.body[0]
        nx = (hx + self.dir[0]) % COLS
        ny = (hy + self.dir[1]) % ROWS
        if (nx, ny) in self.body[:-1]:
            self.over = True
            cfg = self.st.cfg
            if self.score > cfg.get("hi"):
                cfg.set("hi", self.score)
            return
        self.body.insert(0, (nx, ny))
        if (nx, ny) == self.food:
            self.score += 1
            self.food = self.place()
        else:
            self.body.pop()

    def draw(self, c, now):
        c.rect(0, OY - 1, 160, ROWS * CS + 2, FAINT)
        fx, fy = self.food
        c.frect(fx * CS, OY + fy * CS, CS, CS, BAD)
        for i, (x, y) in enumerate(self.body):
            c.frect(x * CS, OY + y * CS, CS, CS, FG if i == 0 else ACC)
            if i:
                c.pixel(x * CS, OY + y * CS, BG)
        if self.over or self.paused:
            c.frect(30, 30, 100, 30, PANEL)
            c.rect(30, 30, 100, 30, WARN if self.over else ACC)
            if self.over:
                c.text_c("遊戲結束 %d 分" % self.score, 80, 33, FG)
                c.text_c("中央:重來", 80, 46, DIM)
            else:
                c.text_c("暫停", 80, 33, FG)
                c.text_c("中央:繼續 雙擊:離開", 80, 46, DIM)
