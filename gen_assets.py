import json, math, random, os, subprocess
import numpy as np
from PIL import Image
random.seed(7)
R = "src/main/resources"
A = f"{R}/assets/rakhit"

def save(img, path): img.save(path)

# ---------- textures ----------
def gold_tex():
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = 215 - y * 3 + random.randint(-12, 12)
            im.putpixel((x, y), (min(255, base + 30), min(255, base), max(0, base - 120), 255))
    return im
save(gold_tex(), f"{A}/textures/item/crown_gold.png")

gem = Image.new("RGBA", (16, 16), (170, 10, 30, 255))
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 5, y - 5)
        c = max(0, 255 - int(d * 14))
        gem.putpixel((x, y), (min(255, 150 + c // 3), 10 + c // 5, 25 + c // 6, 255))
save(gem, f"{A}/textures/item/crown_gem.png")

# whip 32x32: оплетённая рукоять, медная гарда, дугой изогнутый хлыст с узлом и красным кончиком
w = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
def px(x, y, c):
    if 0 <= x < 32 and 0 <= y < 32: w.putpixel((x, y), c)
leather = [(52, 30, 14, 255), (84, 52, 26, 255), (122, 80, 42, 255), (160, 110, 62, 255)]
for i in range(11):                     # рукоять от (3,28) к (13,18)
    x, y = 3 + i, 28 - i
    for dx, dy in ((0, 0), (1, 0), (0, -1)):
        px(x + dx, y + dy, leather[(i + dx) % 2])
    if i % 3 == 0:
        px(x + 1, y - 1, (200, 160, 60, 255))      # латунные кольца
for i in range(3):                      # гарда
    px(12 + i, 20 - i - 1, (214, 170, 40, 255)); px(13 + i, 20 - i, (150, 110, 20, 255))
pts = []
for t in range(0, 41):                  # хлыст: плавная дуга вверх-вправо с волной
    u = t / 40.0
    x = 14 + u * 16
    y = 17 - u * 15 - math.sin(u * math.pi * 2.2) * 3.0
    pts.append((int(round(x)), int(round(y))))
for i, (x, y) in enumerate(pts):
    px(x, y, leather[2 + (i // 2) % 2] if i < 30 else leather[1])
    px(x, y + 1, leather[0])
px(pts[10][0], pts[10][1] - 1, (170, 20, 20, 255))  # красный узелок
px(pts[-1][0], pts[-1][1], (230, 40, 40, 255)); px(pts[-1][0] - 1, pts[-1][1], (170, 20, 20, 255))
save(w, f"{A}/textures/item/whip.png")

# bank teller block
b = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        edge = x in (0, 15) or y in (0, 15)
        if edge: col = (90, 70, 0, 255)
        else:
            n = random.randint(-10, 10)
            col = (212 + n, 175 + n, 55 + n // 2, 255)
        b.putpixel((x, y), col)
for y in range(16):
    for x in range(16):
        if abs(x - 7.5) + abs(y - 7.5) <= 5:
            b.putpixel((x, y), (40, 220, 215, 255) if abs(x - 7.5) + abs(y - 7.5) <= 4 else (20, 20, 20, 255))
save(b, f"{A}/textures/block/bank_teller.png")

# crown armour layer (64x32): head UV only
c = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
G1, G2, G3, RD = (250, 215, 70, 255), (214, 170, 40, 255), (160, 115, 20, 255), (200, 20, 40, 255)
def face(x0, y0, gem_col=False):
    for dx in range(8):
        for dy in range(4):
            X, Y = x0 + dx, y0 + dy
            if dy == 0 and dx % 2 == 1: continue       # spikes
            col = G1 if dy < 2 else (G2 if dy == 2 else G3)
            c.putpixel((X, Y), col)
    if gem_col:
        c.putpixel((x0 + 3, y0 + 2), RD); c.putpixel((x0 + 4, y0 + 2), RD)
        c.putpixel((x0 + 3, y0 + 1), RD); c.putpixel((x0 + 4, y0 + 1), RD)
for fx in (0, 8, 16, 24): face(fx, 8, gem_col=(fx == 8))
for x in range(8, 16):
    for y in range(0, 8):
        c.putpixel((x, y), G1 if (x + y) % 3 else G2)
for (x, y) in ((11, 3), (12, 3), (11, 4), (12, 4)): c.putpixel((x, y), RD)
save(c, f"{A}/textures/models/armor/crown_layer_1.png")

# ---------- models ----------
def box(x1, y1, z1, x2, y2, z2, tex):
    uv = {"uv": [0, 0, 16, 16], "texture": tex}
    return {"from": [x1, y1, z1], "to": [x2, y2, z2],
            "faces": {k: dict(uv) for k in ("north", "east", "south", "west", "up", "down")}}
els = [
    box(2, 1, 2, 14, 5, 3, "#0"), box(2, 1, 13, 14, 5, 14, "#0"),
    box(2, 1, 3, 3, 5, 13, "#0"), box(13, 1, 3, 14, 5, 13, "#0"),
]
for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
    els.append(box(x, 5, z, x + 2, 10, z + 2, "#0"))
for z in (2, 12):
    els.append(box(7, 5, z, 9, 11, z + 2, "#0"))
for x in (2, 12):
    els.append(box(x, 5, 7, x + 2, 11, 9, "#0"))
els.append(box(7, 2, 1.4, 9, 4, 2, "#1"))
els.append(box(7, 2, 14, 9, 4, 14.6, "#1"))
for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
    els.append(box(x + .5, 10, z + .5, x + 1.5, 11, z + 1.5, "#1"))
crown_model = {
    "textures": {"0": "rakhit:item/crown_gold", "1": "rakhit:item/crown_gem", "particle": "rakhit:item/crown_gold"},
    "elements": els,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 8, 0], "scale": [1.1, 1.1, 1.1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    },
}
json.dump(crown_model, open(f"{A}/models/item/crown.json", "w"), indent=1)
json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "rakhit:item/whip"}},
          open(f"{A}/models/item/whip.json", "w"), indent=1)
json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "rakhit:block/bank_teller"}},
          open(f"{A}/models/block/bank_teller.json", "w"), indent=1)
json.dump({"parent": "rakhit:block/bank_teller"}, open(f"{A}/models/item/bank_teller.json", "w"), indent=1)
json.dump({"variants": {"": {"model": "rakhit:block/bank_teller"}}}, open(f"{A}/blockstates/bank_teller.json", "w"), indent=1)

# ---------- lang ----------
ru = {
    "item.rakhit.crown": "Корона Короля Рахита III",
    "item.rakhit.whip": "Королевский хлыст",
    "entity.rakhit.npc": "Подданный Короля",
    "block.rakhit.bank_teller": "Касса Королевского банка",
    "subtitles.rakhit.whip_crack": "Щёлкает хлыст",
}
en = {
    "item.rakhit.crown": "Crown of King Rakhit III",
    "item.rakhit.whip": "Royal Whip",
    "entity.rakhit.npc": "Subject of the King",
    "block.rakhit.bank_teller": "Royal Bank Teller",
    "subtitles.rakhit.whip_crack": "Whip cracks",
}
json.dump(ru, open(f"{A}/lang/ru_ru.json", "w"), indent=1, ensure_ascii=False)
json.dump(en, open(f"{A}/lang/en_us.json", "w"), indent=1)

# ---------- recipes ----------
json.dump({"type": "minecraft:crafting_shaped", "category": "equipment",
           "key": {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:stick"}},
           "pattern": ["  L", " LL", "S  "], "result": {"id": "rakhit:whip", "count": 1}},
          open(f"{R}/data/rakhit/recipe/whip.json", "w"), indent=1)

# ---------- sound ----------
json.dump({"whip_crack": {"sounds": [{"name": "rakhit:whip_crack", "stream": False}], "subtitle": "subtitles.rakhit.whip_crack"}},
          open(f"{A}/sounds.json", "w"), indent=1)
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", os.environ.get("WHIP_MP3", "crack_the_whip.mp3"),
                "-c:a", "libvorbis", "-ac", "1", "-ar", "44100", f"{A}/sounds/whip_crack.ogg"], check=False)

# ---------- skins for the kingdom NPCs (64x64 player layout) ----------
os.makedirs(f"{A}/textures/entity", exist_ok=True)
BOXES = {  # name: (u, v, w, h, d)
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
    "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
    "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
}
def paint(img, part, fn):
    u0, v0, w, h, d = BOXES[part]
    faces = {"top": (u0 + d, v0, w, d), "bottom": (u0 + d + w, v0, w, d), "right": (u0, v0 + d, d, h),
             "front": (u0 + d, v0 + d, w, h), "left": (u0 + d + w, v0 + d, d, h), "back": (u0 + 2 * d + w, v0 + d, w, h)}
    for name, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = fn(name, x, y, fw, fh)
                if c is not None: img.putpixel((fx + x, fy + y), c)

def solid(c): return lambda f, x, y, w, h: c
def rnd_shade(c, amt=10):
    return (max(0, min(255, c[0] + random.randint(-amt, amt))), max(0, min(255, c[1] + random.randint(-amt, amt))),
            max(0, min(255, c[2] + random.randint(-amt, amt))), 255)

def face_fn(skin, eye=(30, 30, 40, 255), mouth=(150, 70, 70, 255), extra=None):
    def fn(f, x, y, w, h):
        c = rnd_shade(skin, 5)
        if f == "front":
            if y == 3 and x in (1, 2, 5, 6): c = (255, 255, 255, 255)
            if y == 3 and x in (2, 5): c = eye
            if y == 5 and x in (3, 4): c = mouth
            if extra: c = extra(x, y, c) or c
        return c
    return fn

def hair_fn(col, long=False, fringe=True):
    def fn(f, x, y, w, h):
        if f == "top": return rnd_shade(col, 8)
        if f == "bottom": return None
        if f == "front": return rnd_shade(col, 8) if (y < 2 or (fringe and y < 3 and x % 3 != 1)) else None
        if f in ("left", "right", "back"):
            return rnd_shade(col, 8) if (y < 5 or (long and y < 8)) else None
        return None
    return fn

def citizen(i):
    r = random.Random(100 + i)
    skins = [(235, 190, 150), (220, 170, 125), (190, 135, 95), (150, 100, 70), (245, 205, 170), (205, 150, 110), (175, 120, 85), (230, 180, 140)]
    shirts = [(160, 60, 50), (60, 110, 160), (80, 140, 80), (190, 150, 60), (120, 80, 140), (200, 200, 190), (160, 100, 60), (70, 130, 130)]
    pants = [(70, 55, 40), (50, 60, 80), (90, 80, 60), (60, 60, 60)]
    hairs = [(60, 40, 25), (30, 25, 25), (150, 100, 40), (190, 160, 90), (110, 50, 30), (210, 210, 205), (90, 60, 40), (40, 40, 60)]
    skin = skins[i % 8]; shirt = shirts[(i * 3 + 1) % 8]; pant = pants[i % 4]; hair = hairs[(i * 5 + 2) % 8]
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    paint(im, "head", face_fn(skin))
    paint(im, "hat", hair_fn(hair, long=(i % 2 == 1)))
    paint(im, "body", lambda f, x, y, w, h: rnd_shade(shirt, 8))
    paint(im, "jacket", lambda f, x, y, w, h: ((90, 60, 35, 255) if (y == 6 and f in ("front", "back", "left", "right")) else None))
    for a_ in ("rarm", "larm"): paint(im, a_, lambda f, x, y, w, h: rnd_shade(shirt, 8) if y < 8 else rnd_shade(skin, 5))
    for l_ in ("rleg", "lleg"): paint(im, l_, lambda f, x, y, w, h: rnd_shade(pant, 8) if y < 10 else (50, 35, 25, 255))
    if i % 3 == 0:   # фартук
        paint(im, "jacket", lambda f, x, y, w, h: ((235, 235, 225, 255) if (f == "front" and y > 3) else None))
    im.save(f"{A}/textures/entity/citizen_{i}.png")
for i in range(8): citizen(i)

def guard():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (225, 175, 135)
    def mustache(x, y, c): return (60, 40, 25, 255) if (y == 4 and x in (2, 3, 4, 5)) else None
    paint(im, "head", face_fn(skin, eye=(20, 20, 30, 255), mouth=(120, 60, 60, 255), extra=mustache))
    RED, DK, GOLD, BLK = (170, 25, 30, 255), (110, 15, 20, 255), (240, 200, 60, 255), (25, 25, 30, 255)
    # шапка: чёрный кивер с золотым околышем и красным султаном
    def hat(f, x, y, w, h):
        if f == "top": return BLK if not (3 <= x <= 4 and 3 <= y <= 4) else RED
        if f == "bottom": return None
        if y == 0 or (y == 1 and f == "front"): return GOLD if f == "front" and x in (3, 4) else BLK
        if y < 3: return BLK
        return None
    paint(im, "hat", hat)
    paint(im, "body", lambda f, x, y, w, h: RED if not (f == "front" and x in (3, 4) and y % 3 == 1) else GOLD)
    def jacket(f, x, y, w, h):
        if y == 9: return BLK                                     # ремень
        if y == 9 and f == "front" and x in (3, 4): return GOLD
        if f in ("front", "back") and (x == 0 or x == w - 1) and y < 2: return GOLD   # погоны-галуны
        if y < 8: return DK if (f == "front" and x in (3, 4)) else RED
        return None
    paint(im, "jacket", jacket)
    for a_ in ("rarm", "larm"): paint(im, a_, lambda f, x, y, w, h: RED if y < 9 else (240, 240, 240, 255))
    for a_ in ("rsleeve", "lsleeve"): paint(im, a_, lambda f, x, y, w, h: GOLD if y in (0, 1) else None)
    for l_ in ("rleg", "lleg"): paint(im, l_, lambda f, x, y, w, h: (30, 30, 40, 255) if y < 9 else (15, 15, 15, 255))
    for l_ in ("rpants", "lpants"): paint(im, l_, lambda f, x, y, w, h: GOLD if y == 4 and f in ("front", "back") and x == 0 else None)
    im.save(f"{A}/textures/entity/guard.png")
guard()

def king():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (235, 190, 150)
    GOLD, PUR, DPUR, WH, RDG = (250, 215, 70, 255), (110, 20, 120, 255), (75, 10, 85, 255), (245, 245, 245, 255), (200, 20, 40, 255)
    def beard(x, y, c): return (225, 225, 225, 255) if (y >= 5 and 1 <= x <= 6) or (y == 4 and x in (1, 6)) else None
    paint(im, "head", face_fn(skin, eye=(40, 40, 120, 255), mouth=(150, 70, 70, 255), extra=beard))
    def crown(f, x, y, w, h):
        if f == "top": return GOLD
        if f == "bottom": return None
        if y == 0: return GOLD if x % 2 == 0 else None           # зубцы
        if y == 1: return GOLD
        if y == 2: return RDG if (f == "front" and x in (3, 4)) else GOLD
        if f in ("left", "right", "back") and y < 8 and y > 4: return (230, 230, 230, 255)   # седые волосы
        return None
    paint(im, "hat", crown)
    paint(im, "body", lambda f, x, y, w, h: PUR)
    def robe(f, x, y, w, h):
        if f == "front" and x in (3, 4): return WH if y % 2 == 0 else (30, 30, 30, 255)   # горностай по центру
        if y == 11: return WH
        return DPUR if (x + y) % 4 == 0 else PUR
    paint(im, "jacket", robe)
    for a_ in ("rarm", "larm"): paint(im, a_, lambda f, x, y, w, h: PUR if y < 9 else rnd_shade(skin, 5))
    for a_ in ("rsleeve", "lsleeve"): paint(im, a_, lambda f, x, y, w, h: WH if y >= 8 else (GOLD if y == 0 else None))
    for l_ in ("rleg", "lleg"): paint(im, l_, lambda f, x, y, w, h: PUR)
    for l_ in ("rpants", "lpants"): paint(im, l_, lambda f, x, y, w, h: WH if y >= 10 else DPUR)
    im.save(f"{A}/textures/entity/king.png")
king()

def hama():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    OR, WH, PK = (225, 150, 60, 255), (250, 240, 225, 255), (240, 150, 150, 255)
    def head(f, x, y, w, h):
        c = rnd_shade(OR[:3], 6)
        if f == "front":
            if y >= 4: c = WH                                    # белая мордочка
            if y == 3 and x in (1, 2, 5, 6): c = (255, 255, 255, 255)
            if y == 3 and x in (2, 5): c = (15, 10, 10, 255)
            if y == 4 and x in (3, 4): c = PK                    # носик
            if y == 5 and x in (0, 7): c = PK                    # щёки
            if y == 1 and x in (2, 3, 4, 5): c = (255, 255, 255, 255) if x in (2, 5) else c   # бровь-вызов
        return c
    paint(im, "head", head)
    def ears(f, x, y, w, h):
        if f == "top": return OR if x in (0, 1, 6, 7) and y < 2 else None
        if f in ("front", "back", "left", "right") and y < 2 and (x in (0, 1) or x in (w - 2, w - 1)): return PK if f == "front" else OR
        if f == "front" and y == 2 and (x == 0 or x == w - 1): return None
        if f in ("front",) and y in (6, 7) and x in (0, 7): return WH     # объёмные щёки
        return None
    paint(im, "hat", ears)
    paint(im, "body", lambda f, x, y, w, h: WH if f == "front" and 2 <= x <= 5 else OR)
    paint(im, "jacket", lambda f, x, y, w, h: (200, 30, 30, 255) if y == 0 and f in ("front", "left", "right", "back") else None)   # красная бандана-шарф
    for a_ in ("rarm", "larm"): paint(im, a_, lambda f, x, y, w, h: OR if y < 9 else WH)
    for l_ in ("rleg", "lleg"): paint(im, l_, lambda f, x, y, w, h: OR if y < 9 else WH)
    im.save(f"{A}/textures/entity/hama.png")
hama()

def stinker():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (150, 175, 100)
    def face(x, y, c):
        if y == 5 and x in (2, 3, 4, 5): return (70, 90, 40, 255)
        if y == 1 and x in (1, 5): return (90, 110, 50, 255)
    paint(im, "head", face_fn(skin, eye=(200, 200, 60, 255), mouth=(60, 70, 30, 255), extra=face))
    paint(im, "hat", hair_fn((70, 60, 40), long=True, fringe=True))
    RAG = (95, 80, 55, 255)
    paint(im, "body", lambda f, x, y, w, h: rnd_shade(RAG, 15))
    paint(im, "jacket", lambda f, x, y, w, h: (60, 120, 40, 255) if random.random() < 0.18 else None)   # зелёные пятна
    for a_ in ("rarm", "larm"): paint(im, a_, lambda f, x, y, w, h: rnd_shade(RAG, 12) if y < 6 else rnd_shade(skin, 8))
    for l_ in ("rleg", "lleg"): paint(im, l_, lambda f, x, y, w, h: rnd_shade((70, 60, 45), 12) if y < 9 else (40, 35, 25, 255))
    im.save(f"{A}/textures/entity/stinker.png")
stinker()
print("assets ok")
