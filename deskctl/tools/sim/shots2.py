import sys, os
from harness import *
from keys import *
import p_manual
OUT = "/tmp/claude-0/-home-claude/822f07ef-fb73-5474-b687-d813d6e3decc/scratchpad/shots"
r = Rig()
r.msg(k="time", t=1791449340, z=480); r.run(100)
r.hold(K_CENTER, 800); r.shot("menu", OUT)
r.click(K_DOWN); r.click(K_DOWN); r.click(K_RIGHT); r.shot("menu_star", OUT)
r.click(K_DOWN); r.click(K_CENTER); r.shot("games", OUT)
r.click(K_CENTER); r.run(1500); r.shot("snake", OUT)
for i in range(6): r.press(K_DOWN, 60); r.run(300)
r.shot("snake2", OUT)
r.double(K_CENTER); print("after dbl:", r.app.cur_id)
r.click(K_DOWN); r.click(K_CENTER); r.click(K_UP); r.click(K_RIGHT); r.click(K_UP); r.click(K_UP); r.shot("ab", OUT)
r.click(K_CENTER); r.click(K_CENTER); r.shot("ab2", OUT)
r.double(K_CENTER); r.click(K_DOWN); r.click(K_CENTER); r.click(K_UP); r.run(900); r.shot("dice", OUT)
r.double(K_CENTER)
# manual
r.app.go("manual"); 
c = r.canvas
for i in range(len(p_manual.PAGES)):
    r.app.cur.i = i
    r.shot("man%02d" % i, OUT)
# wrap check
for name, lines in [p for p in p_manual.PAGES if p[1]]:
    assert len(lines) <= 5, name
    for ln in lines:
        assert c.text_w(ln) <= 152, (name, ln, c.text_w(ln))
r.app.go("settings"); r.shot("settings", OUT)
r.click(K_DOWN); r.click(K_DOWN); r.click(K_RIGHT); r.shot("settings2", OUT)
# 超長按
r.app.go("home"); r.hold(K_CENTER, 3200)
