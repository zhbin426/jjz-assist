#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""进京证助手 · 预置主题墙预览（v1.4）。
模型：液面单色实色 + 波浪水面；水面以上 = 中性半透明黑纱（跟壁纸走）；字体纯白带阴影。
预览用一张绿色渐变模拟壁纸，方便看半透明区域的透底效果。
"""
import colorsys, math
from PIL import Image, ImageDraw, ImageFont

DENSITY = 3.0
W_DP, H_DP = 80, 160
FILL_ALPHA = 235
VEIL = FILL_ALPHA // 8   # v1.7：水上区更透
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
FONT_R = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"

THEMES = [
    ("证件红", 0xB3261E), ("玉子黄", 0xFFBF51), ("朱颜酡", 0xF4A487),
    ("酪黄",   0xF2D791), ("佛手黄", 0xE9C30A), ("十样锦", 0xFFAFA8),
    ("雾霾粉", 0xF49BB7), ("珊瑚橘", 0xF47A5C), ("薄荷青", 0x65D2C5),
    ("奢金",   0xE3C79F), ("舒服红", 0xFF5757),
]


def hx(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def rich(c):
    r, g, b = [x / 255 for x in c]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return tuple(int(x * 255) for x in colorsys.hsv_to_rgb(h, min(s * 1.55, 1), min(v * 1.10, 1)))


def blend(c, t, r):
    return tuple(int(a + (b - a) * r) for a, b in zip(c, t))


def wallpaper(w, h):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        c = tuple(int(a + (b - a) * t) for a, b in zip((74, 111, 91), (52, 84, 70)))
        d.line([(0, y), (w, y)], fill=c)
    return im


def wave_ys(w, top_y, amp, phase):
    k = math.pi * 2 * 1.25 / w
    return [top_y + math.sin(x * k + phase * math.pi * 2) * amp for x in range(w + 1)]


def render(name, liquid, pct=30):
    liquid = hx(liquid)
    w, h = int(W_DP * DENSITY), int(H_DP * DENSITY)
    wall = wallpaper(w, h)
    card = wall.convert("RGBA")
    d = ImageDraw.Draw(card)
    # 黑纱
    veil = Image.new("RGBA", (w, h), (0, 0, 0, VEIL))
    card = Image.alpha_composite(card, veil)
    # 液面波浪
    liq = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(liq)
    fill = rich(liquid)
    liquid_h = max(1, int(h * pct / 100))
    top_y = h - liquid_h
    amp = clamp(h * 0.022, 2, 10)
    back = wave_ys(w, top_y - amp * 0.9, amp * 0.8, 0.5)
    bc = blend(fill, (255, 255, 255), 0.18) + (int(FILL_ALPHA * 0.45),)
    for x in range(w):
        ld.line([(x, back[x]), (x, h)], fill=bc)
    front = wave_ys(w, top_y, amp, 0.0)
    c1, c2 = blend(fill, (255, 255, 255), 0.08), blend(fill, (0, 0, 0), 0.18)
    for x in range(w):
        y0 = front[x]
        span = max(1, h - y0)
        for y in range(int(y0), h):
            t = (y - y0) / span
            ld.point((x, y), fill=tuple(int(a + (b - a) * t) for a, b in zip(c1, c2)) + (FILL_ALPHA,))
    card = Image.alpha_composite(card, liq)
    # 圆角
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], int(min(w, h) * 0.22), fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(card, (0, 0), mask)
    d = ImageDraw.Draw(out)

    # 排版（dp 域同公式）
    hh = H_DP
    tPad = clamp(hh * 0.035, 3, 6); tH = clamp(hh * 0.105, 11, 16)
    pH = clamp(hh * 0.26, 20, 40); dH = clamp(hh * 0.125, 11, 17)
    eH = clamp(hh * 0.115, 10, 15); qH = eH
    qPad = clamp(hh * 0.055, 5, 9); gap = max(hh * 0.018, 1.5)
    quotaTop = hh - qPad - qH; dateTop = quotaTop - gap - eH
    topLimit = tPad + tH + gap
    surface = hh * (1 - pct / 100)
    daysCap = dateTop - dH - gap
    daysFloor = topLimit + pH + gap
    daysTop = max(min(max(surface + hh * 0.025, daysFloor), max(daysCap, topLimit)), topLimit)
    pctMax = max(daysTop - gap - pH, topLimit)
    pctTop = clamp(surface - pH * 1.12, topLimit, pctMax)

    def px(v):
        return v * DENSITY

    def line(text, top_dp, box_dp, rw, bold):
        f = ImageFont.truetype(FONT if bold else FONT_R, int(px(box_dp) * 0.80))
        while f.getlength(text) > w * rw and f.size > 9:
            f = ImageFont.truetype(f.path, f.size - 1)
        bb = f.getbbox(text)
        tw, th = bb[2] - bb[0], bb[3] - bb[1]
        x = (w - tw) / 2 - bb[0]
        y = px(top_dp + box_dp / 2) - th / 2 - bb[1]
        d.text((x + 1, y + 2), text, font=f, fill=(0, 0, 0, 128))
        d.text((x, y), text, font=f, fill=(255, 255, 255, 255))

    line("进京证", tPad, tH, 0.86, False)

    def num_line(value, top_dp, box_dp):
        sval = f"{value:.1f}"
        ip, _, dp_ = sval.partition(".")
        dec = "." + dp_
        fbig = ImageFont.truetype(FONT, int(px(pH) * 0.80))
        fsmall = ImageFont.truetype(FONT, max(9, int(px(dH) * 0.80 * 0.85)))
        wi = fbig.getlength(ip)
        wd = fsmall.getlength(dec)
        x = (w - wi - wd) / 2
        ab, db_ = fbig.getmetrics()
        as_, ds_ = fsmall.getmetrics()
        cy = px(top_dp + box_dp / 2)
        baseline = cy + (ab - db_) / 2
        d.text((x + 1, baseline - ab + 2), ip, font=fbig, fill=(0, 0, 0, 128))
        d.text((x, baseline - ab), ip, font=fbig, fill=(255, 255, 255, 255))
        d.text((x + wi + 1, baseline - as_ + 2), dec, font=fsmall, fill=(0, 0, 0, 128))
        d.text((x + wi, baseline - as_), dec, font=fsmall, fill=(255, 255, 255, 255))

    num_line(pct * 12 / 100, daysTop, dH)
    line("至 12/28(周一)", dateTop, eH, 0.90, False)
    line("年度余 9 次", quotaTop, qH, 0.90, False)
    return out


def main():
    cols = 6
    cw, ch = int(W_DP * DENSITY), int(H_DP * DENSITY)
    pad, label_h = 22, 62
    rows = (len(THEMES) + cols - 1) // cols
    W = pad * (cols + 1) + cw * cols
    H = pad + rows * (ch + label_h + pad)
    canvas = Image.new("RGB", (W, H), (24, 26, 30))
    d = ImageDraw.Draw(canvas)
    ft = ImageFont.truetype(FONT, 26)
    fs = ImageFont.truetype(FONT_R, 20)
    for i, (name, liq) in enumerate(THEMES):
        r, c = divmod(i, cols)
        x = pad + c * (cw + pad)
        y = pad + r * (ch + label_h + pad)
        card = render(name, liq)
        canvas.paste(card, (x, y), card)
        d.text((x, y + ch + 6), name, font=ft, fill=(255, 255, 255))
        d.text((x, y + ch + 36), f"液面 #{liq:06X} · 水上半透明跟壁纸", font=fs, fill=(170, 176, 186))
    p = "/root/userdata/workspace/进京证助手-主题预览-v1.7.png"
    canvas.save(p)
    print("MEDIA:" + p)


if __name__ == "__main__":
    main()
