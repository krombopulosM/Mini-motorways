"""Renders the launcher icon (a winding road between two little buildings) at each density."""
import os, sys
from PIL import Image, ImageDraw

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

def render(px):
    S = 4 * px  # supersample
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = S * 0.22
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=(243, 231, 209, 255))
    # water corner
    d.ellipse([S * 0.62, S * 0.62, S * 1.35, S * 1.35], fill=(156, 203, 224, 255))
    # winding road
    w = int(S * 0.11)
    pts = [(S * 0.25, S * 0.72), (S * 0.25, S * 0.50), (S * 0.50, S * 0.50), (S * 0.50, S * 0.30), (S * 0.74, S * 0.30)]
    d.line(pts, fill=(85, 88, 92, 255), width=w, joint="curve")
    for p in (pts[0], pts[-1]):
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=(85, 88, 92, 255))
    # house (red) and destination (red, bigger)
    hs = S * 0.14
    d.rounded_rectangle([S * 0.25 - hs / 2, S * 0.78, S * 0.25 + hs / 2, S * 0.78 + hs], radius=hs * 0.25, fill=(229, 83, 75, 255))
    ds = S * 0.2
    d.rounded_rectangle([S * 0.78, S * 0.30 - ds / 2, S * 0.78 + ds * 0.8, S * 0.30 + ds / 2], radius=ds * 0.2, fill=(229, 83, 75, 255))
    # a car
    d.rounded_rectangle([S * 0.43, S * 0.38, S * 0.57, S * 0.44], radius=S * 0.02, fill=(63, 142, 219, 255))
    # mask to rounded square
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=255)
    im.putalpha(Image.composite(im.getchannel("A"), mask, mask))
    return im.resize((px, px), Image.LANCZOS)

res = sys.argv[1]
for name, px in SIZES.items():
    out = os.path.join(res, "mipmap-" + name)
    os.makedirs(out, exist_ok=True)
    render(px).save(os.path.join(out, "ic_launcher.png"))
