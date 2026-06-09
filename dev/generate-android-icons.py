from PIL import Image
from pathlib import Path

from beacon_icon import render_background, render_foreground

SIZE = 1024
REPO_DIR = Path(__file__).resolve().parent.parent
MIPMAP_DIR = REPO_DIR / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi"
MIPMAP_DIR.mkdir(parents=True, exist_ok=True)

OUT_BG = MIPMAP_DIR / "ic_launcher_background.png"
OUT_FG = MIPMAP_DIR / "ic_launcher_foreground.png"

# Adaptive foreground must keep its content inside the safe zone or the launcher
# mask clips it. Scale the lighthouse to 65% and centre it on a transparent layer.
SAFE_SCALE = 0.65


def main():
    s = SIZE

    render_background(s).resize((512, 512), Image.LANCZOS).save(OUT_BG, "PNG")
    print(f"wrote {OUT_BG}")

    fg = render_foreground(s)
    scaled = int(s * SAFE_SCALE)
    fg_safe = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    fg_safe.alpha_composite(fg.resize((scaled, scaled), Image.LANCZOS), ((s - scaled) // 2, (s - scaled) // 2))
    fg_safe.resize((512, 512), Image.LANCZOS).save(OUT_FG, "PNG")
    print(f"wrote {OUT_FG}")


if __name__ == "__main__":
    main()
