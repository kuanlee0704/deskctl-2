"""五向按鍵手勢引擎。
事件: PRESS(按下瞬間) CLICK DOUBLE TRIPLE LONG(長按,觸發一次) HOLD(長按不放,重複)
      RELEASE(長按後放開) VLONG(超長按 5 秒)
"""
import time
from config import *

K_LEFT, K_UP, K_CENTER, K_DOWN, K_RIGHT = 0, 1, 2, 3, 4
KEY_NAMES = ("左", "上", "中", "下", "右")
EV_PRESS, EV_CLICK, EV_DOUBLE, EV_TRIPLE, EV_LONG, EV_HOLD, EV_RELEASE, EV_VLONG = range(8)

# 各鍵最多接受幾連按 (中央要留給 三連按=切換上一頁)
_DEFAULT_MAXC = (2, 2, 3, 2, 2)


class Keys:
    def __init__(self, pins=None):
        if pins is None:
            from machine import Pin
            self._pins = [Pin(p, Pin.IN, Pin.PULL_UP) for p in
                          (PIN_LEFT, PIN_UP, PIN_CENTER, PIN_DOWN, PIN_RIGHT)]
        else:
            self._pins = pins
        n = 5
        self.stable = [False] * n
        self.raw = [False] * n
        self.raw_t = [0] * n
        self.t_down = [0] * n
        self.t_up = [0] * n
        self.clicks = [0] * n
        self.long_fired = [False] * n
        self.vlong_fired = [False] * n
        self.hold_next = [0] * n
        self.maxc = list(_DEFAULT_MAXC)
        self.fast = ()
        self._out = []
        self._rawbuf = [False] * 5

    def read_raw(self):
        r = self._rawbuf
        i = 0
        for p in self._pins:
            r[i] = not p.value()
            i += 1
        return r

    def set_fast(self, keys):
        """fast 內的鍵不等連按判定: CLICK 於放開時立即送出 (給遊戲用)"""
        self.fast = tuple(keys)
        for k in range(5):
            self.maxc[k] = 1 if k in self.fast else _DEFAULT_MAXC[k]

    def held_ms(self, k, now):
        if self.stable[k]:
            return time.ticks_diff(now, self.t_down[k])
        return 0

    def any_down(self):
        return any(self.stable)

    @staticmethod
    def _cl(n):
        return EV_CLICK if n == 1 else (EV_DOUBLE if n == 2 else EV_TRIPLE)

    def poll(self, now, raw=None):
        if raw is None:
            raw = self.read_raw()
        out = self._out
        del out[:]
        td = time.ticks_diff
        for k in range(5):
            # 去彈跳
            if raw[k] != self.raw[k]:
                self.raw[k] = raw[k]
                self.raw_t[k] = now
            if raw[k] != self.stable[k] and td(now, self.raw_t[k]) >= DEBOUNCE_MS:
                self.stable[k] = raw[k]
                if raw[k]:                                  # 按下
                    self.t_down[k] = now
                    self.long_fired[k] = False
                    self.vlong_fired[k] = False
                    out.append((k, EV_PRESS))
                else:                                       # 放開
                    if self.long_fired[k]:
                        self.clicks[k] = 0
                        out.append((k, EV_RELEASE))
                    else:
                        self.clicks[k] += 1
                        self.t_up[k] = now
                        if self.clicks[k] >= self.maxc[k]:
                            out.append((k, self._cl(self.clicks[k])))
                            self.clicks[k] = 0
            # 連按逾時 -> 送出
            if self.clicks[k] and not self.stable[k] and td(now, self.t_up[k]) >= DBL_MS:
                out.append((k, self._cl(self.clicks[k])))
                self.clicks[k] = 0
            # 按住中
            if self.stable[k]:
                d = td(now, self.t_down[k])
                if d >= LONG_MS and not self.long_fired[k]:
                    if self.clicks[k]:
                        out.append((k, self._cl(self.clicks[k])))
                        self.clicks[k] = 0
                    self.long_fired[k] = True
                    self.hold_next[k] = time.ticks_add(now, HOLD_MS)
                    out.append((k, EV_LONG))
                elif self.long_fired[k] and td(now, self.hold_next[k]) >= 0:
                    self.hold_next[k] = time.ticks_add(now, HOLD_MS)
                    out.append((k, EV_HOLD))
                if d >= VLONG_MS and not self.vlong_fired[k]:
                    self.vlong_fired[k] = True
                    out.append((k, EV_VLONG))
        return out
