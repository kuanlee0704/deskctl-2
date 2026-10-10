"""頁面登記表。要新增功能區: 寫一個頁面檔, 然後在這裡加一行即可。
欄位: (id, 顯示名稱, 模組名, 類別名, 群組)
群組: "main" = 出現在目錄;  "game" = 出現在遊戲清單;  "hidden" = 只能被其他頁面開啟"""
PAGES = (
    ("home", "首頁", "p_home", "HomePage", "main"),
    ("sw", "碼表", "p_stopwatch", "StopwatchPage", "main"),
    ("music", "音樂", "p_music", "MusicPage", "main"),
    ("games", "遊戲", "p_games", "GamesPage", "main"),
    ("manual", "說明書", "p_manual", "ManualPage", "main"),
    ("settings", "快捷設定", "p_settings", "SettingsPage", "main"),
    # --- 遊戲 ---
    ("snake", "貪吃蛇", "g_snake", "SnakePage", "game"),
    ("ab", "1A2B 猜數字", "g_ab", "ABPage", "game"),
    ("dice", "骰子猜大小", "g_dice", "DicePage", "game"),
    ("rand", "隨機取數", "g_rand", "RandPage", "game"),
    # --- 想新增遊戲/功能就在這裡加一行 ---
)

MAIN = tuple(p for p in PAGES if p[4] == "main")
GAMES = tuple(p for p in PAGES if p[4] == "game")


def info(pid):
    for p in PAGES:
        if p[0] == pid:
            return p
    return None


def name_of(pid):
    p = info(pid)
    return p[1] if p else pid
