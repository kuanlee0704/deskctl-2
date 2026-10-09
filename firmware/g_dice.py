import random
import time
from app import Page
from keys import *
from ui import *

_PIPS = {
    1: ((1, 1),), 2: ((0, 0), (2, 2)), 3: ((0, 0), (1, 1), (2, 2)),
    4: ((0, 0), (2, 0), (0, 2), (2, 2)), 5: ((0, 0), (2, 0), (1, 1), (0, 2), (2, 2)),
    6: ((0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)),
}


class DicePage(Page):
    title = "骰子猜大小"
    reset_label = "重設籌碼"

    def __init__(self, app):
        Page.__init__(self, app)
        self.chips = 100
        self.bet = 10
        self.dice = [1, 1, 1]
        self.msg = "上:押大  下:押小"
        self.msg_col = DIM
        self.roll_until = 0
        self.pending = None

    def on_reset(self, now):
        self.chips = 100
        self.bet = 10
        self.msg = "已重設籌碼"
        self.msg_col = DIM

    def get_title(self):
        return "猜大小 $%d" % self.chips

    def tick(self, now):
        d = self.dirty
        self.dirty = False
        if self.roll_until:
            if time.ticks_diff(now, self.roll_until) >= 0:
                self.roll_until = 0
                self.settle()
            else:
                self.dice = [random.randint(1, 6) for _ in range(3)]
            return True
        return d

    def settle(self):
        side, bet = self.pending
        tot = sum(self.dice)
        triple = self.dice[0] == self.dice[1] == self.dice[2]
        big = tot >= 11
        if triple:
            self.chips -= bet
            self.msg, self.msg_col = "%d 點 豹子通殺 -%d" % (tot, bet), BAD
        elif big == (side == "big"):
            self.chips += bet
            self.msg, self.msg_col = "%d 點 %s 贏 +%d" % (tot, "大" if big else "小", bet), GOOD
        else:
            self.chips -= bet
            self.msg, self.msg_col = "%d 點 %s 輸 -%d" % (tot, "大" if big else "小", bet), BAD
        if self.chips <= 0:
            self.chips = 0
            self.msg, self.msg_col = "破產了 中央:重來", WARN
        self.bet = max(10, min(self.bet, self.chips)) if self.chips else 10

    def on_key(self, k, ev, now):
        self.dirty = True
        if k == K_CENTER:
            if ev == EV_DOUBLE:
                self.app.go("games", now)
                return True
            if ev == EV_CLICK and self.chips == 0:
                self.chips = 100
                self.bet = 10
                self.msg, self.msg_col = "新的一局!", DIM
                return True
            return ev in (EV_PRESS, EV_RELEASE, EV_CLICK)
        if self.roll_until or self.chips == 0:
            return True
        if k in (K_UP, K_DOWN) and ev == EV_CLICK:
            self.pending = ("big" if k == K_UP else "small", self.bet)
            self.roll_until = time.ticks_add(now, 700)
            self.msg, self.msg_col = "搖骰中…", DIM
        elif k == K_LEFT and ev == EV_CLICK:
            self.bet = max(10, self.bet - 10)
        elif k == K_RIGHT and ev == EV_CLICK:
            self.bet = min(self.chips, self.bet + 10)
        else:
            return ev in (EV_PRESS, EV_RELEASE, EV_DOUBLE, EV_TRIPLE, EV_HOLD, EV_LONG)
        return True

    def draw(self, c, now):
        for i, v in enumerate(self.dice):
            x = 8 + i * 28
            c.frect(x, 18, 24, 24, FG)
            for px, py in _PIPS[v]:
                c.frect(x + 4 + px * 7, 22 + py * 7, 4, 4, BG)
        c.text("下注 %d" % self.bet, 100, 18, WARN)
        c.text("左右:調整", 100, 31, FAINT)
        c.text("大 11~17", 100, 44, DIM)
        c.text("小 4~10", 100, 57, DIM)
        c.text(self.msg, 6, 50, self.msg_col)
        c.text("豹子通殺", 6, 65, FAINT)
