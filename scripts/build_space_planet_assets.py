"""Bake lit planetary globes from credited maps; Pillow/numpy are offline build tools only.

Usage: python build_space_planet_assets.py --sources /path/to/downloaded/maps
The shipped app decodes these bounded WebP images and never downloads imagery at runtime.
"""
from pathlib import Path
import argparse
import hashlib
import json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'experience/spaceship/src/main/assets/space/planets'
SIZE = 1024

def sample(image, u, v):
    array = np.asarray(image.convert('RGB'), dtype=np.float32) / 255
    height, width = array.shape[:2]
    xx = (u % 1) * (width - 1)
    yy = np.clip(v, 0, 1) * (height - 1)
    x0, y0 = xx.astype(np.int32), yy.astype(np.int32)
    x1, y1 = (x0 + 1) % width, np.minimum(y0 + 1, height - 1)
    fx, fy = (xx - x0)[..., None], (yy - y0)[..., None]
    return ((array[y0, x0] * (1 - fx) + array[y0, x1] * fx) * (1 - fy)
            + (array[y1, x0] * (1 - fx) + array[y1, x1] * fx) * fy)

def invented_map(seed, icy=False, lava=False):
    """Unresolved exoplanets have explicitly interpretive surfaces, never NASA photo labels."""
    rng = np.random.default_rng(seed)
    texture = np.zeros((512, 1024), dtype=np.float32)
    for size, strength in [(8, .45), (20, .25), (50, .16), (140, .09), (350, .05)]:
        noise = rng.random((max(2, size // 2), size), dtype=np.float32)
        noise[:, -1] = noise[:, 0]
        layer = Image.fromarray((noise * 255).astype(np.uint8)).resize((1024, 512), Image.Resampling.BICUBIC)
        texture += np.asarray(layer, dtype=np.float32) / 255 * strength
    dark, light = (np.array([57, 74, 85]), np.array([207, 219, 220])) if icy else (np.array([48, 43, 41]), np.array([165, 151, 132]))
    if lava:
        dark, light = np.array([32, 19, 17]), np.array([124, 58, 32])
    mix = np.clip((texture - .2) * 1.6, 0, 1)[..., None]
    rgb = dark * (1 - mix) + light * mix
    if lava:
        fissures = np.exp(-np.square((texture - .48) / .009))[..., None]
        rgb = rgb * (1 - fissures * .8) + np.array([255, 157, 53]) * fissures * .8
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))

def globe(image, longitude=0, latitude=0, clouds=None, atmosphere=False, gas=False, lava=False):
    grid = (np.arange(SIZE, dtype=np.float32) + .5 - SIZE / 2) / (SIZE * .485)
    nx, ny = np.meshgrid(grid, -grid)
    rr = nx * nx + ny * ny
    nz = np.sqrt(np.maximum(0, 1 - rr))
    tilt = np.deg2rad(latitude)
    ty = ny * np.cos(tilt) + nz * np.sin(tilt)
    tz = nz * np.cos(tilt) - ny * np.sin(tilt)
    lon = np.arctan2(nx, tz) + np.deg2rad(longitude)
    lat = np.arcsin(np.clip(ty, -1, 1))
    u, v = lon / (2 * np.pi) + .5, .5 - lat / np.pi
    rgb = sample(image, u, v)
    cloud = None
    if clouds is not None:
        cloud = sample(clouds, u + .012, v)[..., :1]
        coverage = np.clip((cloud - .12) * 1.1, 0, .94)
        rgb = rgb * (1 - coverage) + np.array([.96, .98, 1]) * coverage
    light = np.array([-.4, .24, .884], dtype=np.float32)
    light /= np.linalg.norm(light)
    lambert = np.maximum(0, nx * light[0] + ny * light[1] + nz * light[2])
    illumination = .012 + .988 * lambert
    if gas:
        illumination *= .55 + .45 * np.sqrt(nz)
    linear = np.power(rgb, 2.2) * illumination[..., None]
    if lava:
        heat = np.maximum(0, rgb[..., 0] - rgb[..., 1] * 1.3)
        linear += np.stack([heat * .14, heat * .028, heat * .004], axis=-1)
    if atmosphere:
        rim = np.power(1 - nz, 3) * (.05 + .12 * lambert)
        linear += rim[..., None] * np.array([.05, .22, .65])
    result = np.power(np.clip(linear, 0, 1), 1 / 2.2)
    alpha = np.clip((1 - np.sqrt(rr)) * SIZE * .485 + .5, 0, 1)
    result = np.concatenate([result, alpha[..., None]], axis=-1)
    result[rr > 1.005] = 0
    return Image.fromarray((result * 255).astype(np.uint8), 'RGBA')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sources', type=Path, required=True)
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    def source(name):
        return Image.open(next(args.sources.glob(name + '.*')))
    maps = {
        'earth': (source('earth'), dict(longitude=22, latitude=12, clouds=source('clouds'), atmosphere=True)),
        'moon': (source('moon'), dict(longitude=0, latitude=4)),
        'mercury': (source('mercury'), dict(longitude=30)),
        'venus': (source('venus_atmosphere'), dict(longitude=15, atmosphere=True, gas=True)),
        'mars': (source('mars'), dict(longitude=-35, latitude=15)),
        'jupiter': (source('jupiter'), dict(longitude=-48, latitude=-4, gas=True)),
        'saturn': (source('saturn'), dict(longitude=20, gas=True)),
        'uranus': (source('uranus'), dict(gas=True)),
        'neptune': (source('neptune'), dict(longitude=30, gas=True)),
        'pluto': (source('pluto'), dict(longitude=0, latitude=20)),
    }
    # The NASA figure contains a caption and axes; crop only its equirectangular map interior.
    if maps['jupiter'][0].size == (4000, 2400):
        maps['jupiter'] = (maps['jupiter'][0].crop((201, 401, 3799, 2199)), maps['jupiter'][1])
    for i, name in enumerate(['proxima_b', 'trappist_e', 'trappist_f', 'trappist_g', 'lhs1140_b', 'toi700_e', 'toi700_d', 'cancri_e']):
        icy = name in ['trappist_f', 'trappist_g', 'lhs1140_b']
        lava = name == 'cancri_e'
        maps[name] = (invented_map(50 + i, icy, lava), dict(longitude=17 + i * 21, lava=lava))
    manifest = []
    for name, (image, options) in maps.items():
        path = OUTPUT / (name + '.webp')
        globe(image, **options).save(path, 'WEBP', quality=94, method=6, exact=True)
        manifest.append(dict(file=path.name, pixels=[SIZE, SIZE], sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        print(name, path.stat().st_size)
    (OUTPUT.parent / 'planet-assets.json').write_text(json.dumps(manifest, indent=2) + '\n')
    coast_source = args.sources / 'KSC-20200715-PH-JBS01_0019.jpg'
    if coast_source.exists():
        coast = Image.open(coast_source).convert('RGBA')
        rgba = np.array(coast)
        yy = np.arange(coast.height, dtype=np.float32) / coast.height
        alpha = np.clip(yy / .23, 0, 1)
        rgba[:, :, 3] = ((alpha * alpha * (3 - 2 * alpha))[:, None] * 255).astype(np.uint8)
        Image.fromarray(rgba).save(OUTPUT.parent / 'launch-coast.webp', 'WEBP', quality=92, method=6, exact=True)


if __name__ == '__main__':
    main()
