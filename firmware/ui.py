"""共用 UI: 色彩、圖示、上方狀態列、跑馬燈。"""
import time
from gfx import rgb, Icon, Canvas
from state import civil

BG = rgb(6, 9, 15)
PANEL = rgb(22, 30, 46)
BAR = rgb(14, 20, 32)
FG = rgb(238, 241, 248)
DIM = rgb(125, 135, 155)
FAINT = rgb(58, 68, 90)
ACC = rgb(70, 190, 255)
GOOD = rgb(80, 220, 130)
WARN = rgb(255, 190, 60)
BAD = rgb(255, 90, 90)
PINK = rgb(255, 120, 190)

TOP_H = 13          # 上方狀態列高度; 內容區從 y=13 開始, 高 67

IC = {
    "play": Icon(["#.......", "###.....", "#####...", "#######.", "########", "#######.",
                  "#####...", "###.....", "#......."]),
    "pause": Icon(["###..###"] * 9),
    "next": Icon(["#......##", "##.....##", "###....##", "####...##", "#####..##",
                  "####...##", "###....##", "##.....##", "#......##"]),
    "prev": Icon(["##......#", "##.....##", "##....###", "##...####", "##..#####",
                  "##...####", "##....###", "##.....##", "##......#"]),
    "stopw": Icon(["...###...", "....#....", "..#####..", ".#..#..#.", "#...##..#",
                   "#...#.#.#", "#.......#", ".#.....#.", "..#####.."]),
    "bt": Icon(["...#...", "...##..", "#..#.#.", ".#.#.#.", "..###..", "...#...",
                "..###..", ".#.#.#.", "#..#.#.", "...##..", "...#..."]),
    "phone": Icon([".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#",
                   "#.....#", "#.....#", "#..#..#", ".#####."]),
    "head": Icon(["...#####...", "..#.....#..", ".#.......#.", ".#.......#.", "###.....###",
                  "###.....###", "###.....###", "###.....###", ".#.......#."]),
    "spk": Icon(["....#...", "...##..#", "####..#.", "####.#.#", "####.#.#",
                 "####..#.", "...##..#", "....#..."]),
    "mute": Icon(["....#...", "...##...", "####....", "####.#.#", "####..#.", "####.#.#",
                  "...##...", "....#..."]),
    "shuf": Icon(["........##.", "#......#.#.", ".#....#..#.", "..#..#...##", "...##..#...",
                  "..#.#.#....", ".#..#..##..", "#......#.#.", "........##."]),
    "rep": Icon([".#########.", "#.........#", "#........##", "#.......###", "#..........",
                 "#..........", "#.##.......", "##.#......#", ".#########."]),
    "bat": Icon(["#######..", "#.....##.", "#.....##.", "#######.."]),
    "up": Icon(["...#...", "..###..", ".#####.", "#######"]),
    "down": Icon(["#######", ".#####.", "..###..", "...#..."]),
    "bolt": Icon(["..##", ".##.", "####", ".##.", "##.."]),
}


def clock_str(st, now, h24=True, secs=False):
    if not st.t_synced:
        return "--:--"
    y, m, d, hh, mm, ss, wd = st.local(now)
    if not h24:
        hh = hh % 12 or 12
    return ("%02d:%02d:%02d" % (hh, mm, ss)) if secs else ("%02d:%02d" % (hh, mm))


def fmt_ms(ms):
    s = ms // 1000
    return "%d:%02d" % (s // 60, s % 60)


def draw_topbar(c, title, st, link, now, h24=True, secs=False):
    c.frect(0, 0, 160, TOP_H - 1, BAR)
    c.hline(0, TOP_H - 1, 160, FAINT)
    c.text(title, 4, 0, ACC)
    x = 158
    t = clock_str(st, now, h24, secs)
    x -= c.text_w(t)
    c.text(t, x, 0, FG if st.t_synced else DIM)
    x -= 4
    # 藍牙狀態
    online = link.online(now)
    col = GOOD if online else (WARN if link.connected else FAINT)
    x -= 7
    c.icon(IC["bt"], x, 1, col)
    x -= 3
    if st.sw_run:
        x -= 9
        c.icon(IC["stopw"], x, 1, WARN)
        x -= 3
    return x


class Marquee:
    """文字太長時左右來回捲動, 內容區自己的小畫布以免蓋到旁邊。"""

    def __init__(self, font, w, color, h=12):
        self.cv = Canvas(w, h, font)
        self.w = w
        self.h = h
        self.color = color
        self.text = None
        self.tw = 0
        self.t0 = 0

    def draw(self, parent, x, y, text, now, bg=BG, color=None):
        if text != self.text:
            self.text = text
            self.tw = self.cv.text_w(text)
            self.t0 = now
        cv = self.cv
        cv.frect(0, 0, self.w, self.h, bg)
        col = color or self.color
        if self.tw <= self.w:
            cv.text(text, 0, 0, col)
        else:
            # 停 1.2 秒 -> 一路往左捲, 滑出去後從右邊接回來, 持續循環
            gap = 28
            cyc = self.tw + gap
            t = time.ticks_diff(now, self.t0) % (1200 + cyc * 35)
            off = 0 if t < 1200 else (t - 1200) // 35
            cv.text(text, -off, 0, col)
            cv.text(text, cyc - off, 0, col)
        parent.blit(cv, x, y)

    @property
    def animating(self):
        return self.tw > self.w


def clip_text(c, s, maxw):
    """超過寬度就截斷加 …"""
    if c.text_w(s) <= maxw:
        return s
    while s and c.text_w(s + "…") > maxw:
        s = s[:-1]
    return s + "…"
