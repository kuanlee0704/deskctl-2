import sys, os
from harness import *
from keys import *
OUT = "/tmp/claude-0/-home-claude/822f07ef-fb73-5474-b687-d813d6e3decc/scratchpad/shots"

r = Rig()
r.msg(k="time", t=1791449340, z=480)       # 2026-10-08 16:49 +8
r.msg(k="batt", p=83, ch=1)
r.msg(k="track", t="晴天", a="周杰倫", b="葉惠美", d=269000, app="YouTube Music")
r.msg(k="play", s=1, p=83000, v=100)
r.msg(k="vol", v=9, m=15, mu=0)
r.msg(k="route", r="bt", n="Buds")
r.msg(k="mode", sh=1, rp=1)
r.link.inbox.append({"k": "ping"})
r.run(300)
r.shot("home", OUT)
r.click(K_RIGHT); r.shot("sw0", OUT)
r.click(K_CENTER); r.run(2300); r.click(K_UP); r.run(1200); r.click(K_UP); r.run(400)
r.shot("sw_run", OUT)
r.triple(K_CENTER)
r.shot("after_triple", OUT)
print("cur after triple:", r.app.cur_id)
r.app.go("music"); r.run(100); r.shot("music", OUT)
r.click(K_UP); r.run(300); r.shot("music_vol", OUT)
r.run(2500)
r.msg(k="track", t="Bohemian Rhapsody - Remastered 2011 (Official Video)", a="Queen", b="A Night at the Opera", d=355000, app="Spotify")
r.run(2000); r.shot("music_en", OUT)
r.double(K_CENTER); r.shot("music_mute", OUT)
r.link.is_online = False; r.run(100); r.shot("music_offline", OUT)
r.link.is_online = True
# menu
r.hold(K_CENTER, 800); r.shot("menu", OUT)
print("cur:", r.app.cur_id)
