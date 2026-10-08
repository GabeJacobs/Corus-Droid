#!/usr/bin/env python3
"""Verify exported Blue artwork uses Default's exact pixel silhouette."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageOps

RES = Path(__file__).resolve().parents[1] / 'app/src/main/res'
BLUE = '#5E91F0'
DENSITIES = ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']


def white_on_blue(default):
    mask = ImageOps.invert(default.convert('L'))
    return Image.composite(Image.new('RGB', default.size, 'white'),
                           Image.new('RGB', default.size, BLUE), mask)


def load(path, mode='RGB'):
    with Image.open(path) as image:
        return image.convert(mode)


class AppIconArtworkTest(unittest.TestCase):
    def assert_pixels_equal(self, actual, expected):
        self.assertEqual(actual.size, expected.size)
        self.assertIsNone(ImageChops.difference(actual, expected).getbbox(),
                          'Blue must match the Default silhouette pixel for pixel')

    def test_picker_uses_exact_default_silhouette(self):
        folder = RES / 'drawable-nodpi'
        default = load(folder / 'app_icon_default_preview.png')
        blue = load(folder / 'app_icon_blue_preview.png')
        self.assert_pixels_equal(blue, white_on_blue(default))

    def test_legacy_icons_use_exact_default_silhouette(self):
        for density in DENSITIES:
            with self.subTest(density=density):
                folder = RES / f'mipmap-{density}'
                default = load(folder / 'ic_launcher.png')
                blue = load(folder / 'ic_launcher_blue.png')
                self.assert_pixels_equal(blue, white_on_blue(default))
                round_blue = load(folder / 'ic_launcher_blue_round.png')
                self.assert_pixels_equal(round_blue, blue)

    def test_adaptive_foregrounds_use_exact_default_silhouette(self):
        for density in DENSITIES:
            with self.subTest(density=density):
                folder = RES / f'mipmap-{density}'
                default = load(folder / 'ic_launcher_foreground.png')
                blue = load(folder / 'ic_launcher_blue_foreground.png', 'RGBA')
                self.assert_pixels_equal(blue.getchannel('A'), ImageOps.invert(default.convert('L')))

    def test_adaptive_and_themed_resources_use_matching_layers(self):
        key = '{http://schemas.android.com/apk/res/android}drawable'
        for name in ['ic_launcher_blue', 'ic_launcher_blue_round']:
            root = ET.parse(RES / f'mipmap-anydpi-v26/{name}.xml').getroot()
            self.assertEqual(root.find('foreground').attrib[key], '@mipmap/ic_launcher_blue_foreground')
            self.assertEqual(root.find('monochrome').attrib[key], '@mipmap/ic_launcher_monochrome')


if __name__ == '__main__':
    unittest.main()
