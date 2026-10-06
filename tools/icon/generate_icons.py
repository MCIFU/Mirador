"""Genera el icono de USBX: vectores Android, PNG por densidad, icono de Play Store (512 px) y splash.

Diseño: pendrive USB en diagonal con un botón de reproducir, sobre degradado turquesa → petróleo.
El conjunto (cuerpo + conector) está centrado en el lienzo de 108×108 del icono adaptativo.

Uso:  python3 tools/icon/generate_icons.py app/src/main /tmp/preview   (requiere cairosvg y pillow)
"""
import math
import os
import sys
from io import BytesIO

import cairosvg
from PIL import Image, ImageDraw

RES, OUT = sys.argv[1], sys.argv[2]

C1, C2 = "#00B3A1", "#004F5C"          # degradado de fondo
PLAY1, PLAY2 = "#00A896", "#00525F"    # degradado del botón de reproducir
HOLES = "#0A6C74"
ANGLE = -35                            # inclinación del pendrive (grados)
CX = CY = 54.0


def rot(x, y, angle=ANGLE, cx=CX, cy=CY):
    a = math.radians(angle)
    dx, dy = x - cx, y - cy
    return cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def rrect(x0, y0, x1, y1, r, angle=ANGLE):
    """Rectángulo redondeado girado alrededor del centro del lienzo (arcos circulares: basta girar los extremos)."""
    pts = [(x0 + r, y0), (x1 - r, y0), (x1, y0 + r), (x1, y1 - r), (x1 - r, y1), (x0 + r, y1), (x0, y1 - r), (x0, y0 + r)]
    p = [rot(*q, angle=angle) for q in pts]
    arc = lambda q: f"A{f(r)},{f(r)} 0 0 1 {f(q[0])},{f(q[1])}"
    return (f"M{f(p[0][0])},{f(p[0][1])}L{f(p[1][0])},{f(p[1][1])}{arc(p[2])}L{f(p[3][0])},{f(p[3][1])}{arc(p[4])}"
            f"L{f(p[5][0])},{f(p[5][1])}{arc(p[6])}L{f(p[7][0])},{f(p[7][1])}{arc(p[0])}Z")


def play(cx, cy, size, r):
    """Triángulo de reproducir con esquinas redondeadas, centrado por su centroide (centro óptico)."""
    w = size * math.sqrt(3) / 2
    x0 = cx - w / 3
    pts = [(x0, cy - size / 2), (x0 + w, cy), (x0, cy + size / 2)]

    def toward(a, b, t):
        length = math.dist(a, b)
        return a[0] + (b[0] - a[0]) * t / length, a[1] + (b[1] - a[1]) * t / length

    d = ""
    for i in range(3):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % 3]
        a, b = toward(p1, p0, r), toward(p1, p2, r)
        d += ("M" if i == 0 else "L") + f"{f(a[0])},{f(a[1])}Q{f(p1[0])},{f(p1[1])} {f(b[0])},{f(b[1])}"
    return d + "Z"


# Geometría sin girar: cuerpo 24×34 y conector 14×11 → caja vertical 32..76 (centro 54).
BODY = rrect(42, 42, 66, 76, 7)
CONNECTOR = rrect(47, 32, 61, 43, 2.2)
HOLE_PATHS = rrect(50, 35.5, 53, 38.5, 0.6) + rrect(55, 35.5, 58, 38.5, 0.6)
# El botón va recto (no girado) en el centro del cuerpo ya girado.
BODY_CENTER = rot(54, 59)
PLAY = play(BODY_CENTER[0] + 0.3, BODY_CENTER[1], 15.5, 2.4)
PLAY_GRAD = (BODY_CENTER[0] - 8, BODY_CENTER[1] - 9, BODY_CENTER[0] + 8, BODY_CENTER[1] + 9)


# ---------- SVG maestro (PNG) ----------
def svg(include_bg=True, include_fg=True):
    gx0, gy0, gx1, gy1 = PLAY_GRAD
    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108"><defs>',
        f'<linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{C1}"/><stop offset="1" stop-color="{C2}"/></linearGradient>',
        '<radialGradient id="hl" cx="22" cy="18" r="70" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>',
        f'<linearGradient id="pl" x1="{f(gx0)}" y1="{f(gy0)}" x2="{f(gx1)}" y2="{f(gy1)}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{PLAY1}"/><stop offset="1" stop-color="{PLAY2}"/></linearGradient>',
        "</defs>",
    ]
    if include_bg:
        parts += ['<rect width="108" height="108" fill="url(#bg)"/>', '<rect width="108" height="108" fill="url(#hl)"/>']
    if include_fg:
        parts += [
            f'<path d="{BODY}" fill="#00242B" fill-opacity="0.22" transform="translate(1.2 2.6)"/>',
            f'<path d="{CONNECTOR}" fill="#E8FFFC"/>',
            f'<path d="{HOLE_PATHS}" fill="{HOLES}"/>',
            f'<path d="{BODY}" fill="#FFFFFF"/>',
            f'<path d="{PLAY}" fill="url(#pl)"/>',
        ]
    parts.append("</svg>")
    return "".join(parts)


def render(s, size):
    png = cairosvg.svg2png(bytestring=s.encode(), output_width=size, output_height=size)
    return Image.open(BytesIO(png)).convert("RGBA")


def legacy(size, round_shape):
    """Icono clásico: zona visible (72/108) del adaptativo, recortada con su forma."""
    big = render(svg(), size * 108 // 72 + 1)
    off = (big.width - size) // 2
    img = big.crop((off, off, off + size, off + size))
    mask = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(mask)
    if round_shape:
        d.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * 0.22), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in DENSITIES.items():
    folder = os.path.join(RES, "res", f"mipmap-{name}")
    os.makedirs(folder, exist_ok=True)
    legacy(px, False).save(os.path.join(folder, "ic_launcher.png"), optimize=True)
    legacy(px, True).save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)
render(svg(), 512).convert("RGB").save(os.path.join(RES, "ic_launcher-playstore.png"), optimize=True)

os.makedirs(OUT, exist_ok=True)
sheet = Image.new("RGBA", (5 * 220, 240), (245, 247, 247, 255))
for i, px in enumerate(DENSITIES.values()):
    ic = legacy(px, False)
    sheet.paste(ic, (20 + i * 220 + (192 - px) // 2, 20 + (192 - px) // 2), ic)
sheet.save(os.path.join(OUT, "densidades.png"))

# ---------- Vectores Android ----------
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '{comment}<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"{extra}\n'
        '    android:width="{size}dp" android:height="{size}dp"\n'
        '    android:viewportWidth="{vp}" android:viewportHeight="{vp}">\n')


def head(comment="", size=108, vp=108, extra=""):
    return HEAD.format(comment=f"<!-- {comment} -->\n" if comment else "", size=size, vp=vp, extra=extra)


def gradient_path(d, kind, attrs, stops, indent="    "):
    items = "".join(f'<item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'{indent}<path android:pathData="{d}">\n'
            f'{indent}    <aapt:attr name="android:fillColor">\n'
            f'{indent}        <gradient android:type="{kind}" {attrs}>{items}</gradient>\n'
            f'{indent}    </aapt:attr>\n{indent}</path>\n')


gx0, gy0, gx1, gy1 = PLAY_GRAD
play_xml = lambda indent: gradient_path(
    PLAY, "linear", f'android:startX="{f(gx0)}" android:startY="{f(gy0)}" android:endX="{f(gx1)}" android:endY="{f(gy1)}"',
    [(0, PLAY1), (1, PLAY2)], indent)


def symbol(indent="    "):
    return (f'{indent}<group android:translateX="1.2" android:translateY="2.6">\n'
            f'{indent}    <path android:pathData="{BODY}" android:fillColor="#3800242B" />\n{indent}</group>\n'
            f'{indent}<path android:pathData="{CONNECTOR}" android:fillColor="#FFE8FFFC" />\n'
            f'{indent}<path android:pathData="{HOLE_PATHS}" android:fillColor="{HOLES}" />\n'
            f'{indent}<path android:pathData="{BODY}" android:fillColor="#FFFFFFFF" />\n')


background = (head("Fondo: degradado turquesa → petróleo con brillo suave arriba a la izquierda.")
              + gradient_path("M0,0h108v108h-108z", "linear", 'android:startX="0" android:startY="0" android:endX="108" android:endY="108"', [(0, C1), (1, C2)])
              + gradient_path("M0,0h108v108h-108z", "radial", 'android:centerX="22" android:centerY="18" android:gradientRadius="70"', [(0, "#38FFFFFF"), (1, "#00FFFFFF")])
              + "</vector>\n")
foreground = head("Pendrive USB en diagonal con botón de reproducir, centrado en el lienzo.") + symbol() + play_xml("    ") + "</vector>\n"
monochrome = (head("Silueta para iconos temáticos (Android 13+): orificios y botón recortados.")
              + f'    <path android:pathData="{CONNECTOR} {HOLE_PATHS}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" />\n'
              + f'    <path android:pathData="{BODY} {PLAY}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" />\n'
              + "</vector>\n")
# Splash 240 dp (máscara de 160 dp): lienzo de 162 con el símbolo de 108 centrado.
splash = (head("240 dp es el tamaño que exige la pantalla de inicio de Android 12+ (icono con máscara de 160 dp).",
               size=240, vp=162, extra='\n    xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="VectorRaster"')
          + '    <group android:translateX="27" android:translateY="27">\n'
          + symbol("        ")
          + f'        <group android:name="play" android:pivotX="{f(BODY_CENTER[0])}" android:pivotY="{f(BODY_CENTER[1])}">\n'
          + play_xml("            ")
          + "        </group>\n    </group>\n</vector>\n")

drawables = os.path.join(RES, "res", "drawable")
for name, content in [("ic_launcher_background", background), ("ic_launcher_foreground", foreground),
                      ("ic_launcher_monochrome", monochrome), ("splash_icon_static", splash)]:
    with open(os.path.join(drawables, f"{name}.xml"), "w") as fh:
        fh.write(content)
print("ok")
