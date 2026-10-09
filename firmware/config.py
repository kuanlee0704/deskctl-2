# ===== 硬體腳位 =====
# 擴充板(Display-Keypad)是旋轉 180° 疊在核心板上: 擴充板第 n 腳 -> 核心板第 33-n 腳
#   LKEY(3)->核心30=GPIO9   UPKEY(4)->核心29=GPIO8   CENTER(5)->核心28=GPIO4
#   DWKEY(6)->核心27=GPIO5  RKEY(23)->核心10=GPIO13
#   BL(9)->核心24=GPIO11  CS(10)->核心23=GPIO7  DC(11)->核心22=GPIO6
#   RES(12)->核心21=GPIO10  SDA(13)->核心20=GPIO3  SCL(14)->核心19=GPIO2
PIN_LEFT = 9
PIN_UP = 8
PIN_CENTER = 4
PIN_DOWN = 5
PIN_RIGHT = 13

PIN_SCK = 2
PIN_MOSI = 3
PIN_DC = 6
PIN_CS = 7
PIN_RST = 10
PIN_BL = 11

# ===== 螢幕 (橫向 160x80) =====
LCD_W = 160
LCD_H = 80
X_OFF = 1           # 0.96" 80x160 IPS 的顯示區偏移 (橫向時 x=1, y=26)
Y_OFF = 26
SPI_BAUD = 40_000_000
# 橫向的 4 種 MADCTL 組合, 校正畫面可切換
ORIENTS = (0x60, 0xA0, 0x20, 0xE0)

# ===== 按鍵時間 (ms) =====
DEBOUNCE_MS = 25
LONG_MS = 600       # 長按門檻
DBL_MS = 260        # 連按判定間隔
HOLD_MS = 220       # 長按不放的重複間隔
VLONG_MS = 5000     # 超長按 (重啟 / 重設)
SHOW_VLONG_MS = 1800  # 超過這個時間開始顯示倒數

# ===== 藍牙 =====
BLE_NAME = "DeskCtl"
ONLINE_TIMEOUT_MS = 15000   # 超過這時間沒收到手機 App 的訊息 -> 視為離線

# ===== 其他 =====
FONT_FILE = "font12.bin"
SETTINGS_FILE = "settings.json"
FPS_MS = 40         # 最快 25 fps
