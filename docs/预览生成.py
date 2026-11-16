#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
进京证助手 · 组件排版预览生成（v2.3）
与 PassWidgetProvider / WidgetRenderer 完全同一套盒子公式（dp 域计算），
用于装机前核对：四行字位置、是否重叠、天数钉住点、液面与饱和度。
用法：python3 进京证助手-预览生成.py
产物：进京证助手-排版预览-v2.3.png
"""
import colorsys
from PIL import Image, ImageDraw, ImageFont

DENSITY = 3.5
W_DP, H_DP = 80, 160          # 1×2 竖向组件的典型尺寸
FILL = (0xD6, 0x3A, 0x2F)     # 默认红
FILL_ALPHA = 235
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
FONT_R = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def rich(c):
    """饱和度 x1.55、亮度 +10%"""
    r, g, b = [x / 255 for x in c]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return colorsys.hsv_to_rgb(h, min(s * 1.55, 1), min(v * 1.10, 1))


def blend(c, toward, ratio):
    return tuple(int(a + (b - a) * ratio) for a, b in zip(c, toward))


def layout(h, pct, expired=False, filled=True):
    tPad = clamp(h * 0.035, 3, 6)
    tH = clamp(h * 0.105, 11, 16)
    pH = clamp(h * 0.26, 20, 40)
    dH = clamp(h * 0.125, 11, 17)
    eH = clamp(h * 0.115, 10, 15)
    qH = eH
    qPad = clamp(h * 0.055, 5, 9)
    gap = max(h * 0.018, 1.5)
    quotaTop = h - qPad - qH
    dateTop = quotaTop - gap - eH
    topLimit = tPad + tH + gap
    surface = h * 0.42 if expired else h * (1 - pct / 100)
    daysCap = dateTop - dH - gap
    daysFloor = topLimit + pH + gap
    daysTop = max(min(max(surface + h * 0.025, daysFloor), max(daysCap, topLimit)), topLimit)
    anchor = daysTop if filled else dateTop
    pctMax = max(anchor - gap - pH, topLimit)
    pctTop = clamp(surface - pH * 1.12, topLimit, pctMax)
    return dict(tPad=tPad, tH=tH, pH=pH, dH=dH, eH=eH, qH=qH, gap=gap,
                pctTop=pctTop, daysTop=daysTop, dateTop=dateTop, quotaTop=quotaTop,
                surface=surface, daysCap=daysCap, pinned=abs(daysTop - daysCap) < 1e-6)


def fit_font(text, max_px, start_px, path):
    size = start_px
    while size > 8:
        f = ImageFont.truetype(path, int(size))
        if f.getlength(text) <= max_px:
            return f
        size -= 1
    return ImageFont.truetype(path, 8)


def render_card(pct, expired=False):
    w, h = int(W_DP * DENSITY), int(H_DP * DENSITY)
    L = layout(H_DP, pct, expired)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fill = tuple(int(x * 255) for x in rich(FILL))
    radius = int(min(w, h) * 0.22)
    card_alpha = FILL_ALPHA // 4

    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius, fill=255)

    body = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    bd.rectangle([0, 0, w, h], fill=fill + (card_alpha,))

    filled = (not expired) and pct > 0
    if filled:
        liq_h = max(1, int(h * pct / 100))
        top_y = h - liq_h
        c1 = blend(fill, (255, 255, 255), 0.08)
        c2 = blend(fill, (0, 0, 0), 0.18)
        for y in range(top_y, h):
            t = 0 if liq_h <= 1 else (y - top_y) / (liq_h - 1)
            col = tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))
            bd.line([(0, y), (w, y)], fill=col + (FILL_ALPHA,))
        line_h = max(2, int(h * 0.010))
        wl = blend(fill, (255, 255, 255), 0.60)
        bd.rectangle([0, top_y, w, top_y + line_h], fill=wl + (int(FILL_ALPHA * 0.85),))
        for i in range(int(h * 0.10)):
            a = int(26 * (1 - i / max(1, h * 0.10)))
            bd.line([(0, top_y + line_h + i), (w, top_y + line_h + i)], fill=(255, 255, 255, a))
    for y in range(int(h * 0.45)):
        a = int(10 * (1 - y / (h * 0.45)))
        bd.line([(0, y), (w, y)], fill=(255, 255, 255, a))

    img.paste(body, (0, 0), mask)
    d = ImageDraw.Draw(img)

    def px(v):
        return v * DENSITY

    def line(text, top_dp, box_dp, ratio_w, alpha, bold=True, ls=0):
        f = fit_font(text, W_DP * ratio_w * DENSITY, px(box_dp) * 0.80, FONT if bold else FONT_R)
        bb = f.getbbox(text)
        tw, th = bb[2] - bb[0], bb[3] - bb[1]
        x = (w - tw) / 2 - bb[0]
        cy = px(top_dp + box_dp / 2)
        y = cy - th / 2 - bb[1]
        d.text((x + 1, y + 2), text, font=f, fill=(0, 0, 0, 90))
        d.text((x, y), text, font=f, fill=(255, 255, 255, alpha))

    line("进京证", L['tPad'], L['tH'], 0.86, 215, bold=False)
    big = "已过期" if expired else ("未设置" if pct is None else f"{pct:.1f}%")
    line(big, L['pctTop'], L['pH'], 0.94, 255)
    if filled:
        line(f"剩 {pct * 12 / 100:.1f} 天", L['daysTop'], L['dH'], 0.92, 238, bold=False)
    line("至 12/28(周一)", L['dateTop'], L['eH'], 0.90, 215, bold=False)
    line("年度余 9 次", L['quotaTop'], L['qH'], 0.90, 180, bold=False)
    return img, L


def main():
    states = [(100, False), (60, False), (30, False), (12, False), (5, False), (0, True)]
    cards, infos = [], []
    for pct, exp in states:
        img, L = render_card(None if exp else pct, exp)
        cards.append(img)
        infos.append((pct, exp, L))
    pad, cw, ch = 26, int(W_DP * DENSITY), int(H_DP * DENSITY)
    W = pad * (len(cards) + 1) + cw * len(cards)
    H = ch + pad * 3 + 60
    out = Image.new("RGB", (W, H), (24, 26, 30))
    d = ImageDraw.Draw(out)
    f_t = ImageFont.truetype(FONT, 30)
    f_s = ImageFont.truetype(FONT_R, 24)
    for i, c in enumerate(cards):
        x = pad + i * (cw + pad)
        out.paste(c, (x, pad), c)
        pct, exp, L = infos[i]
        label = "已过期" if exp else f"{pct}%"
        d.text((x, pad + ch + 14), label, font=f_t, fill=(255, 255, 255))
        pin = "天数已钉住" if (L['pinned'] and not exp) else ""
        d.text((x, pad + ch + 52), f"水面 {L['surface']:.0f}dp  天数顶 {L['daysTop']:.0f}/{L['daysCap']:.0f} {pin}",
               font=f_s, fill=(190, 195, 205))
    p = "/root/userdata/workspace/进京证助手-排版预览-v2.3.png"
    out.save(p)
    print("MEDIA:" + p)


if __name__ == "__main__":
    main()
