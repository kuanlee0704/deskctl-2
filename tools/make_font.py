#!/usr/bin/env python3
"""產生 12px 點陣字型 font12.bin (來源: Noto Sans CJK TC, SIL OFL)。
格式: 'F12\0' | count:u16 | codes:u16[count](遞增) | adv:u8[count] | bitmaps: 24B*count
每字 12 列, 每列 2 bytes(高位在左, 只用前 12 bit)。
用法: python3 make_font.py <NotoSansCJK-Regular.ttc> <輸出 font12.bin>
"""
import sys, struct
from PIL import Image, ImageDraw, ImageFont

TTC, OUT = sys.argv[1], sys.argv[2]
TC_INDEX = 3
TH = 85          # 灰階二值化門檻
BASE = 10        # 基線列
font = ImageFont.truetype(TTC, 12, index=TC_INDEX)

def codes():
    s = set(range(0x20, 0x7F))
    s |= set(range(0xA1, 0x100))
    s |= set(range(0x2010, 0x2027)) | set(range(0x2030, 0x203C)) | {0x20AC, 0x2103, 0x2190, 0x2191, 0x2192, 0x2193}
    s |= set(range(0x2460, 0x2470)) | set(range(0x25A0, 0x25D0)) | set(range(0x2605, 0x2607)) | {0x266A, 0x266B}
    s |= set(range(0x3000, 0x3040)) | set(range(0x3041, 0x30FF)) | set(range(0x3105, 0x3130))
    s |= set(range(0xFF01, 0xFF5F)) | {0xFFE5}
    for hi in range(0xA1, 0xFA):                      # Big5 一二級字 + 符號
        for lo in list(range(0x40, 0x7F)) + list(range(0xA1, 0xFF)):
            try:
                c = bytes([hi, lo]).decode("big5")
                if len(c) == 1: s.add(ord(c))
            except Exception:
                pass
    for hi in range(0xB0, 0xD8):                      # GB2312 一級字(常用簡體)
        for lo in range(0xA1, 0xFF):
            try:
                s.add(ord(bytes([hi, lo]).decode("gb2312")))
            except Exception:
                pass
    return sorted(c for c in s if c < 0x10000)

def render(ch):
    im = Image.new("L", (24, 20), 0)
    d = ImageDraw.Draw(im)
    d.text((2, BASE + 3), ch, font=font, fill=255, anchor="ls")
    # 取 x 2..14, y 3..15
    box = im.crop((2, 3, 14, 15)).point(lambda v: 255 if v >= TH else 0)
    return box

# .notdef 位圖(找不到字時字型給的框) 用來過濾
notdef = render("\U0010FFFF").tobytes()
cps, advs, bits = [], [], []
for cp in codes():
    ch = chr(cp)
    im = render(ch)
    raw = im.tobytes()
    if raw == notdef and cp != 0x20:
        continue
    px = im.load()
    rows = bytearray()
    maxx = 0
    for y in range(12):
        v = 0
        for x in range(12):
            if px[x, y]:
                v |= 0x8000 >> x
                maxx = max(maxx, x + 1)
        rows += struct.pack(">H", v)
    if cp < 0x2E80 and not (0x3000 <= cp < 0x3100):    # 拉丁/符號: 比例寬度
        adv = 4 if cp == 0x20 else min(12, max(maxx, 1) + 1)
        if 0x20 < cp < 0x7F and cp not in (0x49, 0x69, 0x6C, 0x2E, 0x2C, 0x3A, 0x3B, 0x21, 0x7C, 0x27):
            adv = max(adv, 6)
    else:
        adv = 12
    cps.append(cp); advs.append(adv); bits.append(bytes(rows))

with open(OUT, "wb") as f:
    f.write(b"F12\0" + struct.pack("<H", len(cps)))
    f.write(struct.pack("<%dH" % len(cps), *cps))
    f.write(bytes(advs))
    for b in bits: f.write(b)
print("glyphs:", len(cps), "bytes:", 6 + len(cps) * 27)
