"""CPython 版 framebuf 替身 (只實作韌體用到的功能), 供桌面模擬器使用。"""
import numpy as np

MONO_VLSB, RGB565, GS4_HMSB, MONO_HLSB, MONO_HMSB, GS8 = 0, 1, 2, 3, 4, 6


class FrameBuffer:
    def __init__(self, buf, w, h, fmt, stride=None):
        self.buf, self.w, self.h, self.fmt = buf, w, h, fmt
        self.stride = stride or w
        if fmt == RGB565:
            self.a = np.frombuffer(buf, dtype="<u2")[: self.stride * h].reshape(h, self.stride)
        elif fmt == MONO_HLSB:
            self.bpr = (self.stride + 7) // 8
            self.a = np.frombuffer(buf, dtype=np.uint8)[: self.bpr * h].reshape(h, self.bpr)
        else:
            raise NotImplementedError(fmt)

    def pixel(self, x, y, c=None):
        if not (0 <= x < self.w and 0 <= y < self.h):
            return None if c is None else None
        if self.fmt == RGB565:
            if c is None:
                return int(self.a[y, x])
            self.a[y, x] = c
        else:
            if c is None:
                return (int(self.a[y, x >> 3]) >> (7 - (x & 7))) & 1
            m = 0x80 >> (x & 7)
            if c:
                self.a[y, x >> 3] |= m
            else:
                self.a[y, x >> 3] &= ~m & 0xFF

    def fill(self, c):
        self.a[:, : self.w] = c

    def fill_rect(self, x, y, w, h, c):
        x0, y0 = max(x, 0), max(y, 0)
        x1, y1 = min(x + w, self.w), min(y + h, self.h)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = c

    def hline(self, x, y, w, c):
        self.fill_rect(x, y, w, 1, c)

    def vline(self, x, y, h, c):
        self.fill_rect(x, y, 1, h, c)

    def rect(self, x, y, w, h, c):
        self.hline(x, y, w, c)
        self.hline(x, y + h - 1, w, c)
        self.vline(x, y, h, c)
        self.vline(x + w - 1, y, h, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.pixel(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def blit(self, src, x, y, key=-1, palette=None):
        for sy in range(src.h):
            dy = y + sy
            if not 0 <= dy < self.h:
                continue
            for sx in range(src.w):
                dx = x + sx
                if not 0 <= dx < self.w:
                    continue
                col = src.pixel(sx, sy)
                if palette is not None:
                    col = palette.pixel(col, 0)
                if col != key:
                    self.a[dy, dx] = col
