from pathlib import Path

from PIL import Image, ImageDraw


OUTPUT_DIR = Path(__file__).resolve().parents[1] / "docs" / "assets" / "wallet"
CARD_GRAY = "#6F7378"
LINE_BLUE = "#B9DCEB"
PALE_BLUE = "#E7F2F7"
WHITE = "#FFFFFF"


def generate_logo() -> None:
    image = Image.new("RGB", (660, 660), CARD_GRAY)
    draw = ImageDraw.Draw(image)

    # Front view of an original streamlined train. The 15% outer margin keeps
    # the artwork inside Google Wallet's circular logo mask.
    draw.rounded_rectangle((170, 105, 490, 530), radius=150, fill=WHITE)
    draw.polygon([(170, 345), (120, 475), (205, 510), (250, 410)], fill=WHITE)
    draw.polygon([(490, 345), (540, 475), (455, 510), (410, 410)], fill=WHITE)
    draw.rounded_rectangle((220, 185, 440, 330), radius=52, fill=LINE_BLUE)
    draw.line((330, 185, 330, 330), fill=CARD_GRAY, width=12)
    draw.ellipse((220, 385, 270, 435), fill=LINE_BLUE)
    draw.ellipse((390, 385, 440, 435), fill=LINE_BLUE)
    draw.line((235, 475, 425, 475), fill=LINE_BLUE, width=18)
    draw.line((245, 545, 205, 590), fill=WHITE, width=20)
    draw.line((415, 545, 455, 590), fill=WHITE, width=20)

    image.save(OUTPUT_DIR / "train-logo.png", optimize=True)


def generate_hero() -> None:
    image = Image.new("RGBA", (1032, 812), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Quiet landscape lines inspired by travel, without copying railway marks
    # or vehicle liveries.
    draw.line(
        [(45, 365), (170, 255), (285, 350), (425, 210), (575, 355), (735, 245), (987, 375)],
        fill=(185, 220, 235, 150),
        width=12,
        joint="curve",
    )
    draw.ellipse((770, 95, 890, 215), outline=(231, 242, 247, 180), width=12)

    # Original side-profile high-speed train illustration.
    body = [
        (90, 410),
        (690, 410),
        (805, 425),
        (948, 515),
        (975, 555),
        (945, 585),
        (160, 585),
        (75, 535),
    ]
    draw.polygon(body, fill=(255, 255, 255, 225))
    draw.line(body + [body[0]], fill=(231, 242, 247, 255), width=9, joint="curve")
    draw.polygon(
        [(715, 435), (800, 450), (910, 515), (775, 505)],
        fill=(185, 220, 235, 235),
    )

    for x in range(145, 685, 90):
        draw.rounded_rectangle((x, 455, x + 58, 505), radius=12, fill=(185, 220, 235, 235))

    draw.line((105, 535, 945, 535), fill=(185, 220, 235, 255), width=14)
    draw.ellipse((225, 550, 315, 640), fill=CARD_GRAY, outline=WHITE, width=12)
    draw.ellipse((700, 550, 790, 640), fill=CARD_GRAY, outline=WHITE, width=12)

    # Rails and motion lines keep the composition airy around the train.
    draw.line((55, 675, 975, 675), fill=(231, 242, 247, 220), width=14)
    draw.line((125, 720, 910, 720), fill=(185, 220, 235, 190), width=9)
    draw.line((65, 620, 175, 620), fill=(231, 242, 247, 180), width=8)
    draw.line((25, 650, 145, 650), fill=(231, 242, 247, 130), width=8)

    image.save(OUTPUT_DIR / "train-hero.png", optimize=True)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    generate_logo()
    generate_hero()


if __name__ == "__main__":
    main()
