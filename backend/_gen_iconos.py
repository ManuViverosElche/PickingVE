"""D-195: genera iconos adaptativos Android + favicon web desde el logo corporativo."""
from PIL import Image, ImageDraw, ImageFilter
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

LOGO = r"C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\Documentacion\Logos\VIVEROS ELCHE-LOGO-trans-borde.png"
RES = r"C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\app\src\main\res"
WEB = r"C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\web\manager"

VERDE_OSCURO = (12, 58, 63)    # #0C3A3F
VERDE_MEDIO = (20, 85, 92)     # #14555C

logo = Image.open(LOGO).convert("RGBA")

# ---- Fondo verde corporativo con degradado radial sutil ----
def crear_fondo(size):
    img = Image.new("RGBA", (size, size), VERDE_OSCURO)
    draw = ImageDraw.Draw(img)
    cx = cy = size // 2
    max_r = size * 0.71
    for i in range(0, size):
        for_step = max(1, size // 100)
        pass
    # Degradado simple vertical
    for py in range(size):
        ratio = py / size
        r = int(VERDE_OSCURO[0] + (VERDE_MEDIO[0] - VERDE_OSCURO[0]) * ratio)
        g = int(VERDE_OSCURO[1] + (VERDE_MEDIO[1] - VERDE_OSCURO[1]) * ratio)
        b = int(VERDE_OSCURO[2] + (VERDE_MEDIO[2] - VERDE_OSCURO[2]) * ratio)
        draw.line([(0, py), (size, py)], fill=(r, g, b))
    return img

# ---- Foreground adaptive-icon: logo en zona segura (66% del grid de 108dp) ----
FG_SIZE = 432
fg = Image.new("RGBA", (FG_SIZE, FG_SIZE), (0, 0, 0, 0))
safe_zone = int(FG_SIZE * 0.60)  # 66dp / 108dp â‰ˆ 61%, usamos 60%
logo_fg = logo.copy()
logo_fg.thumbnail((safe_zone, safe_zone), Image.Resampling.LANCZOS)
fx = (FG_SIZE - logo_fg.width) // 2
fy = (FG_SIZE - logo_fg.height) // 2
fg.paste(logo_fg, (fx, fy), logo_fg)

# ---- Background adaptive-icon: degradado verde ----
bg_adaptive = crear_fondo(432)
os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
bg_adaptive.save(os.path.join(RES, "drawable", "ic_launcher_bg_viveros.png"))
fg.save(os.path.join(RES, "drawable", "ic_launcher_fg_viveros.png"))

# ---- Legacy launcher icons (PNG redondos/cuadrados por densidad) ----
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
for density, size in DENSITIES.items():
    ddir = os.path.join(RES, density)
    os.makedirs(ddir, exist_ok=True)
    # Fondo con degradado
    bg = crear_fondo(size)
    # Logo centrado al 70% (un poco mas grande en legacy para visibilidad)
    lg = logo.copy()
    lg.thumbnail((int(size * 0.72), int(size * 0.72)), Image.Resampling.LANCZOS)
    lx = (size - lg.width) // 2
    ly = (size - lg.height) // 2
    bg.paste(lg, (lx, ly), lg)
    bg.save(os.path.join(ddir, "ic_launcher.png"))
    # Redondo: recorte circular
    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.ellipse([0, 0, size - 1, size - 1], fill=255)
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_img.paste(bg, (0, 0), mask)
    round_img.save(os.path.join(ddir, "ic_launcher_round.png"))
    print(f"{density}: {size}x{size} OK")

# ---- Web: logo horizontal para header del panel ----
logo_h = Image.open(r"C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\Documentacion\Logos\VIVEROS ELCHE-LOGO_sin_palmera-trans.png")
# Escalar a altura 40px manteniendo proporcion
ratio_h = 40 / logo_h.height
logo_h_w = int(logo_h.width * ratio_h)
logo_h_small = logo_h.resize((logo_h_w, 40), Image.Resampling.LANCZOS)
logo_h_small.save(os.path.join(WEB, "viveros_logo_header.png"))
print("viveros_logo_header.png OK")

# ---- Web: favicon 32x32 ----
fav = crear_fondo(32)
lg_fav = logo.copy()
lg_fav.thumbnail((24, 24), Image.Resampling.LANCZOS)
fav.paste(lg_fav, ((32 - lg_fav.width) // 2, (32 - lg_fav.height) // 2), lg_fav)
fav.save(os.path.join(WEB, "favicon.ico"), sizes=[(32, 32)])
fav.save(os.path.join(WEB, "favicon-32.png"))
print("favicon OK")

print("TODOS LOS RECURSOS GENERADOS")
