"""Genera el icono de USBX: vectores Android, PNG por densidad, icono de Play Store (512 px) y splash.

Uso:  python3 tools/icon/generate_icons.py app/src/main /tmp/preview   (requiere cairosvg y pillow)
"""
import sys, os
import cairosvg
from PIL import Image, ImageDraw

RES = sys.argv[1]   # app/src/main
OUT = sys.argv[2]   # preview dir

C1, C2 = "#00B3A1", "#004F5C"     # degradado de fondo
FG_SCALE = 0.88  # margen para que el símbolo no roce el borde en ninguna máscara (círculo, squircle…)
GROUP_OPEN = f'    <group android:scaleX="{FG_SCALE}" android:scaleY="{FG_SCALE}" android:pivotX="54" android:pivotY="54">\n'

INNER1, INNER2 = "#00A896", "#00525F"
SLOT = "#0A6C74"

def path_rrect(x0, y0, x1, y1, r):
    return (f"M{x0+r},{y0}H{x1-r}A{r},{r} 0,0 1 {x1},{y0+r}V{y1-r}A{r},{r} 0,0 1 {x1-r},{y1}"
            f"H{x0+r}A{r},{r} 0,0 1 {x0},{y1-r}V{y0+r}A{r},{r} 0,0 1 {x0+r},{y0}Z")

# Conector USB clásico (rectángulo metálico con dos orificios): se reconoce al instante.
CONNECTOR = path_rrect(45, 23, 63, 39, 2.5)
SLOT_P = path_rrect(48.5, 27.5, 52.5, 31.5, 0.8) + path_rrect(55.5, 27.5, 59.5, 31.5, 0.8)
BODY = path_rrect(39, 37, 69, 84, 8)
SHADOW = path_rrect(40, 40, 70, 87, 8)
# Triángulo de play con esquinas redondeadas (centro óptico ligeramente a la derecha)
PLAY = "M49.5,55.0C49.5,53.4 51.2,52.4 52.6,53.2L62.6,59.2C64.0,60.0 64.0,62.0 62.6,62.8L52.6,68.8C51.2,69.6 49.5,68.6 49.5,67.0Z"

# ---------- SVG maestro (para PNG) ----------
def svg_full(include_bg=True, include_fg=True):
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">',
             '<defs>',
             f'<linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{C1}"/><stop offset="1" stop-color="{C2}"/></linearGradient>',
             '<radialGradient id="hl" cx="22" cy="18" r="70" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>',
             f'<linearGradient id="pl" x1="49" y1="52" x2="64" y2="70" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{INNER1}"/><stop offset="1" stop-color="{INNER2}"/></linearGradient>',
             '</defs>']
    if include_bg:
        parts += ['<rect width="108" height="108" fill="url(#bg)"/>', '<rect width="108" height="108" fill="url(#hl)"/>']
    if include_fg:
        parts += [f'<g transform="translate(54 54) scale({FG_SCALE}) translate(-54 -54)">',
                  f'<path d="{SHADOW}" fill="#00242B" fill-opacity="0.22"/>',
                  f'<path d="{CONNECTOR}" fill="#FFFFFF" fill-opacity="0.92"/>',
                  f'<path d="{SLOT_P}" fill="{SLOT}"/>',
                  f'<path d="{BODY}" fill="#FFFFFF"/>',
                  f'<path d="{PLAY}" fill="url(#pl)"/>', '</g>']
    parts.append('</svg>')
    return "".join(parts)

def render(svg, size):
    from io import BytesIO
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
    return Image.open(BytesIO(png)).convert("RGBA")

def legacy(size, round_shape):
    # Icono clásico: zona central de 76/108 del icono adaptativo, recortada con su forma.
    big = render(svg_full(), size * 108 // 76 + 1)
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

densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in densities.items():
    d = os.path.join(RES, "res", f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    legacy(px, False).save(os.path.join(d, "ic_launcher.png"), optimize=True)
    legacy(px, True).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)

# Play Store: 512×512, cuadrado completo (Google aplica la máscara).
render(svg_full(), 512).convert("RGB").save(os.path.join(RES, "ic_launcher-playstore.png"), optimize=True)

# Previsualizaciones
os.makedirs(OUT, exist_ok=True)
prev = Image.new("RGBA", (5 * 220, 260), (245, 247, 247, 255))
for i, (name, px) in enumerate(densities.items()):
    prev.paste(legacy(px, False), (20 + i * 220 + (192 - px) // 2, 20 + (192 - px) // 2), legacy(px, False))
prev.save(os.path.join(OUT, "densidades.png"))
legacy(192, True).save(os.path.join(OUT, "redondo.png"))

# ---------- Vectores Android ----------
VEC_HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n')

def grad_path(d, kind, attrs, stops, extra=""):
    stop_xml = "".join(f'<item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'    <path android:pathData="{d}"{extra}>\n'
            f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="{kind}" {attrs}>{stop_xml}</gradient>\n'
            f'        </aapt:attr>\n    </path>\n')

bg = VEC_HEAD + "    <!-- Fondo: degradado turquesa → petróleo con brillo suave arriba a la izquierda. -->\n"
bg += grad_path("M0,0h108v108h-108z", "linear", 'android:startX="0" android:startY="0" android:endX="108" android:endY="108"', [(0, C1), (1, C2)])
bg += grad_path("M0,0h108v108h-108z", "radial", 'android:centerX="22" android:centerY="18" android:gradientRadius="70"', [(0, "#38FFFFFF"), (1, "#00FFFFFF")])
bg += "</vector>\n"

fg_body = (f'    <path android:pathData="{SHADOW}" android:fillColor="#3800242B" />\n'
           f'    <path android:pathData="{CONNECTOR}" android:fillColor="#EBFFFFFF" />\n'
           f'    <path android:pathData="{SLOT_P}" android:fillColor="{SLOT}" />\n'
           f'    <path android:pathData="{BODY}" android:fillColor="#FFFFFFFF" />\n')
play = grad_path(PLAY, "linear", 'android:startX="49" android:startY="52" android:endX="64" android:endY="70"', [(0, INNER1), (1, INNER2)])
fg = (VEC_HEAD + "    <!-- Pendrive USB con botón de reproducir: memoria + multimedia. -->\n" + GROUP_OPEN
      + fg_body + play + "    </group>\n</vector>\n")

mono = (VEC_HEAD + "    <!-- Silueta para iconos temáticos (Android 13+): el play se recorta del cuerpo. -->\n"
        + GROUP_OPEN
        + f'    <path android:pathData="{CONNECTOR} {SLOT_P}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" />\n'
        f'    <path android:pathData="{BODY} {PLAY}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" />\n'
        "    </group>\n</vector>\n")

# Splash (Android 12+): el símbolo con el play animado (escala + aparición).
SPLASH_HEAD = VEC_HEAD.replace('xmlns:aapt="http://schemas.android.com/aapt"\n', 'xmlns:aapt="http://schemas.android.com/aapt"\n    xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="VectorRaster"\n')
splash_static = (SPLASH_HEAD.replace('<vector', '<!-- 240 dp es el tamaño que exige la pantalla de inicio de Android 12+ (icono con máscara de 160 dp). -->\n<vector', 1).replace('android:width="108dp" android:height="108dp"', 'android:width="240dp" android:height="240dp"')
                 .replace('android:viewportWidth="108" android:viewportHeight="108"', 'android:viewportWidth="162" android:viewportHeight="162"')
                 + '    <group android:translateX="27" android:translateY="27">\n'
                 + fg_body.replace("    <path", "        <path")
                 + '        <group android:name="play" android:pivotX="56" android:pivotY="61">\n'
                 + play.replace("\n    ", "\n            ").replace("    <path", "            <path", 1)
                 + '        </group>\n    </group>\n</vector>\n')

with open(os.path.join(RES, "res/drawable/ic_launcher_background.xml"), "w") as f: f.write(bg)
with open(os.path.join(RES, "res/drawable/ic_launcher_foreground.xml"), "w") as f: f.write(fg)
with open(os.path.join(RES, "res/drawable/ic_launcher_monochrome.xml"), "w") as f: f.write(mono)
with open(os.path.join(RES, "res/drawable/splash_icon_static.xml"), "w") as f: f.write(splash_static)
print("ok")
