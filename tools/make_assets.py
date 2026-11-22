"""Generates the mod icon and the repository banner."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
RES = ROOT / "src" / "main" / "resources"
DOCS.mkdir(exist_ok=True)
RES.mkdir(parents=True, exist_ok=True)

FONT = "/usr/share/fonts/TTF/DejaVuSans.ttf"
FONT_B = "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"

BG = (16, 21, 26)
PANEL = (24, 30, 36)
PANEL_2 = (30, 38, 45)
CYAN = (51, 214, 255)
GOLD = (255, 196, 0)
TEXT = (236, 241, 245)
MUTED = (138, 150, 160)
GREEN = (124, 179, 66)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def rounded(draw: ImageDraw.ImageDraw, box, radius: int, fill, outline=None, width: int = 1) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def rack_grid(draw: ImageDraw.ImageDraw, x: int, y: int, cell: int, gap: int, cols: int, rows: int,
              accent_cell: tuple[int, int] | None = None) -> None:
    for row in range(rows):
        for col in range(cols):
            cx = x + col * (cell + gap)
            cy = y + row * (cell + gap)
            fill = PANEL_2
            outline = (52, 64, 74)
            if accent_cell == (row, col):
                fill = (24, 74, 88)
                outline = CYAN
            rounded(draw, (cx, cy, cx + cell, cy + cell), 6, fill, outline, 3 if accent_cell == (row, col) else 2)
            # "items" on the rack
            for line in range(2):
                draw.rounded_rectangle(
                    (cx + 8, cy + 10 + line * 14, cx + cell - 8, cy + 15 + line * 14),
                    radius=2,
                    fill=(70, 84, 96) if accent_cell != (row, col) else (120, 210, 230),
                )


def build_icon() -> None:
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    rounded(draw, (2, 2, size - 3, size - 3), 22, BG, (46, 58, 68), 2)
    rack_grid(draw, 20, 22, 22, 6, 3, 3, accent_cell=(1, 1))
    # magnifier
    draw.ellipse((60, 58, 108, 106), outline=CYAN, width=6)
    draw.ellipse((66, 64, 102, 100), fill=(18, 26, 32, 90))
    draw.line((100, 98, 116, 114), fill=CYAN, width=7)
    img.save(RES / "icon.png")

    icon192 = img.resize((192, 192), Image.LANCZOS)
    icon192.save(DOCS / "icon.png")


def _glow_background(width: int, height: int, center: tuple[int, int], radius: int, color: tuple[int, int, int]) -> Image.Image:
    base = Image.new("RGB", (width, height), BG)
    glow = Image.new("RGB", (width, height), BG)
    gdraw = ImageDraw.Draw(glow)
    for step in range(28, 0, -1):
        cx, cy = center
        rx = radius * step // 28
        ry = int(rx * 0.62)
        gdraw.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=color)
    return Image.blend(base, glow.filter(ImageFilter.GaussianBlur(48)), 0.85)


def build_banner() -> None:
    width, height = 1280, 360
    img = _glow_background(width, height, (1010, 190), 520, (20, 66, 82))
    draw = ImageDraw.Draw(img)

    draw.rectangle((0, 0, 8, height), fill=CYAN)
    draw.text((64, 74), "RACK FINDER", font=font(72, True), fill=TEXT)
    draw.text((66, 166), "MineColonies rack search  ·  Forge 1.20.1",
              font=font(28), fill=MUTED)

    draw.rounded_rectangle((66, 218, 250, 258), radius=20, fill=(20, 52, 62), outline=(38, 96, 112), width=2)
    draw.text((86, 226), "no server mod", font=font(19, True), fill=CYAN)
    draw.rounded_rectangle((266, 218, 430, 258), radius=20, fill=(42, 35, 18), outline=(96, 80, 30), width=2)
    draw.text((286, 226), "through walls", font=font(19, True), fill=GOLD)

    rack_grid(draw, 764, 60, 50, 10, 4, 3, accent_cell=(1, 2))

    ax, ay = 764 + 2 * 60, 60 + 1 * 60
    for offset in range(7):
        alpha_box = (ax - 5 - offset, ay - 5 - offset, ax + 55 + offset, ay + 55 + offset)
        draw.rectangle(alpha_box, outline=(51, 214, 255), width=1)

    draw.text((764, 302), "item  →  every rack  →  coordinates + distance", font=font(21), fill=MUTED)
    img.save(DOCS / "banner.png")


if __name__ == "__main__":
    build_icon()
    build_banner()
    print("assets written:", sorted(p.name for p in DOCS.iterdir()))
