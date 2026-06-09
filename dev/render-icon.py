from PIL import Image
from pathlib import Path

from beacon_icon import render_app_icon

SIZE = 1024
OUT_PNG = Path(__file__).resolve().parent.parent / "desktop" / "src" / "main" / "resources" / "icon.png"
OUT_ICO = Path(__file__).resolve().parent.parent / "desktop" / "src" / "main" / "resources" / "icon.ico"


def main():
    icon = render_app_icon(SIZE)

    icon.resize((512, 512), Image.LANCZOS).save(OUT_PNG, "PNG")
    print(f"wrote {OUT_PNG}")

    ico_sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
    icon.save(OUT_ICO, format="ICO", sizes=ico_sizes)
    print(f"wrote {OUT_ICO}")


if __name__ == "__main__":
    main()
