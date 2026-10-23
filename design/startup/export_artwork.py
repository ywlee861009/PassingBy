"""Export the single square launch illustration without changing its composition.

Run: rtk proxy python3 design/startup/export_artwork.py
Requires Pillow. The full-resolution source PNG stays in design/startup/.
"""

from pathlib import Path

from PIL import Image


def main():
    artwork = Path(__file__).resolve().parent
    resources = artwork.parents[1] / "feature/journey/src/main/res"
    destination = resources / "drawable-nodpi/launch_journey.webp"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(artwork / "journey-square.png") as source:
        if source.width != source.height:
            raise ValueError("Launch artwork must have a 1:1 aspect ratio")
        source.convert("RGB").save(destination, "WEBP", quality=90, method=6)
    print(f"{destination.relative_to(artwork.parents[1])}: {destination.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
