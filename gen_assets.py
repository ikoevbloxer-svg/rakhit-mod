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

# whip 16x16
w = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
dark, mid, lite, red = (60, 35, 18, 255), (110, 70, 35, 255), (160, 110, 60, 255), (170, 20, 20, 255)
handle = [(1, 14), (2, 13), (3, 12), (4, 11)]
for i, p in enumerate(handle):
    w.putpixel(p, dark if i % 2 == 0 else mid)
w.putpixel((0, 14), dark); w.putpixel((1, 15), dark)
lash = [(5, 10), (6, 9), (7, 8), (8, 8), (9, 7), (10, 6), (11, 5), (11, 4), (12, 3), (13, 3), (13, 2), (14, 1)]
for i, p in enumerate(lash):
    w.putpixel(p, lite if i % 2 else mid)
    if i < len(lash) - 3 and i % 3 == 0:
        q = (p[0], p[1] + 1)
        if 0 <= q[1] < 16: w.putpixel(q, dark)
w.putpixel((14, 1), red); w.putpixel((15, 0), red)
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
    "block.rakhit.bank_teller": "Касса Королевского банка",
    "subtitles.rakhit.whip_crack": "Щёлкает хлыст",
}
en = {
    "item.rakhit.crown": "Crown of King Rakhit III",
    "item.rakhit.whip": "Royal Whip",
    "block.rakhit.bank_teller": "Royal Bank Teller",
    "subtitles.rakhit.whip_crack": "Whip cracks",
}
json.dump(ru, open(f"{A}/lang/ru_ru.json", "w"), indent=1, ensure_ascii=False)
json.dump(en, open(f"{A}/lang/en_us.json", "w"), indent=1)

# ---------- recipes ----------
json.dump({"type": "minecraft:crafting_shaped", "category": "equipment",
           "key": {"G": {"item": "minecraft:gold_block"}, "N": {"item": "minecraft:nether_star"}, "D": {"item": "minecraft:diamond_block"}},
           "pattern": ["GNG", "GDG"], "result": {"id": "rakhit:crown", "count": 1}},
          open(f"{R}/data/rakhit/recipe/crown.json", "w"), indent=1)
json.dump({"type": "minecraft:crafting_shaped", "category": "equipment",
           "key": {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:stick"}},
           "pattern": ["  L", " LL", "S  "], "result": {"id": "rakhit:whip", "count": 1}},
          open(f"{R}/data/rakhit/recipe/whip.json", "w"), indent=1)

# ---------- sound ----------
json.dump({"whip_crack": {"sounds": [{"name": "rakhit:whip_crack", "stream": False}], "subtitle": "subtitles.rakhit.whip_crack"}},
          open(f"{A}/sounds.json", "w"), indent=1)
sr = 44100
t = np.arange(int(sr * 0.5)) / sr
rng = np.random.default_rng(3)
noise = rng.standard_normal(t.size)
swoosh = np.convolve(noise, np.ones(40) / 40, mode="same") * np.clip(t / 0.12, 0, 1) * (t < 0.14)
crack_t = np.clip(t - 0.14, 0, None)
hp = np.diff(noise, prepend=0)
crack = hp * np.exp(-crack_t / 0.018) * (t >= 0.14)
chirp = np.sin(2 * np.pi * (2500 + 6000 * np.exp(-crack_t / 0.01)) * crack_t) * np.exp(-crack_t / 0.02) * (t >= 0.14)
sig = 0.35 * swoosh + 0.9 * crack + 0.5 * chirp
sig = sig / np.max(np.abs(sig)) * 0.9
pcm = (sig * 32767).astype(np.int16)
import wave
with wave.open("/tmp/whip.wav", "wb") as f:
    f.setnchannels(1); f.setsampwidth(2); f.setframerate(sr); f.writeframes(pcm.tobytes())
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", "/tmp/whip.wav", "-c:a", "libvorbis", "-ac", "1", "-ar", "44100",
                f"{A}/sounds/whip_crack.ogg"], check=True)
print("assets ok")
