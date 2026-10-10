# 背光腳位測試: 在 Thonny 打開這個檔案按 Run (板子上不用存)。
# 每個腳位會被拉低 4 秒, 請看螢幕背光有沒有變暗/熄滅, 並記下是哪一個腳位。
import time
from machine import Pin
for n in (11, 0, 1, 12):
    try:
        p = Pin(n, Pin.OUT, value=1)
        time.sleep(1)
        print("GPIO%d -> LOW 4s (看背光有沒有變化)" % n)
        p.value(0)
        time.sleep(4)
        p.value(1)
        print("GPIO%d -> HIGH" % n)
        time.sleep(1)
        p.init(Pin.IN)
    except Exception as e:
        print("GPIO%d 失敗:" % n, e)
print("完成")
