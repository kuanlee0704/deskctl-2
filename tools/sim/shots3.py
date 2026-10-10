import sys
exec(open('test_layout.py').read().split("# ---------- 首頁")[0])
from PIL import Image
out=[]
def grab(r,name):
    r.app._last_draw=-10**6; r.app._sig=None; r.app.cur.dirty=True; r.run(80)
    out.append((name,r.lcd.png("/tmp/shots/%s.png"%name)))
r=mk({"tsecs":1}); track(r,"一首很長的歌名 Very Long Title Here","歌手"); grab(r,"home_play")
r.app.go("sw",clock.now); r.st.sw_acc=3600000+5*60000+7000; r.st.sw_run=True; r.st.sw_t=clock.now; r.st.sw_laps=[1000000,2000000,3000000,3700000]; grab(r,"sw_hours")
r=mk(); r.app.go("ab",clock.now); p=r.app.cur
for n in range(1,10):
    p.guess=[n%10,(n+1)%10,(n+2)%10,(n+3)%10]; r.click(K_CENTER)
grab(r,"ab9")
r=mk(); r.app.go("rand",clock.now); grab(r,"rand_setup"); r.click(K_CENTER); r.app.cur.dg=[0,0,0,9,9,9,2,5]; r.app.cur.res=None; r.click(K_CENTER); grab(r,"rand_res")
r=mk(); r.app.go("settings",clock.now); grab(r,"settings")
W=Image.new("RGB",(640*2,320*4),(30,30,30))
for i,(n,im) in enumerate(out): W.paste(im,((i%2)*640,(i//2)*320))
W.save("/tmp/shots/all.png")
