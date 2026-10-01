#!/usr/bin/env python3
"""Verify a patched APK retains code/IDs and has readable Catppuccin themes.

Usage: python3 test/verify_catppuccin.py original.apk patched.apk original-decoded patched-decoded
Decode both APKs with Apktool before running this integration check.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

original, patched, original_decoded, decoded = map(Path, sys.argv[1:])

def resource_ids(directory):
    return {(e.attrib["type"], e.attrib["name"]): e.attrib["id"]
            for e in ET.parse(directory / "res/values/public.xml").getroot()}

before_ids = resource_ids(original_decoded)
after_ids = resource_ids(decoded)
for key, value in before_ids.items():
    assert after_ids.get(key) == value, f"Resource ID changed: {key}"
with zipfile.ZipFile(original) as before, zipfile.ZipFile(patched) as after:
    dex_files = [name for name in before.namelist() if name.endswith('.dex')]
    assert dex_files, 'No app bytecode found'
    for name in dex_files:
        assert before.read(name) == after.read(name), f'App bytecode changed: {name}'

styles = {e.attrib['name']: {i.attrib['name']: i.text for i in e}
          for e in ET.parse(decoded / 'res/values/styles.xml').getroot()}
colors = {e.attrib['name']: e.text for e in ET.parse(decoded / 'res/values/colors.xml').getroot()}

def resolve_color(value):
    while value.startswith('@color/'):
        value = colors[value.removeprefix('@color/')]
    return '#' + value.lower()[-6:]

for name, base, text, accent in [
    ('LightTheme', '#eff1f5', '#4c4f69', '#8839ef'),
    ('MaterialLightTheme', '#eff1f5', '#4c4f69', '#8839ef'),
    ('DarkTheme', '#24273a', '#cad3f5', '#c6a0f6'),
    ('MaterialDarkTheme', '#24273a', '#cad3f5', '#c6a0f6'),
]:
    def color(key):
        return resolve_color(styles[name][key])
    assert color('ContentBackground') == base, name
    assert color('PrimaryTextColor') == text, name
    assert color('colorSecondary') == accent, name
    assert 'Catppuccin' in styles[name]['snackbarTextViewStyle'], name
assert styles['Catppuccin.SnackbarText']['android:textColor'] == '?PrimaryTextColor'
assert resolve_color(styles['MaterialLightTheme.TealA700']['colorSecondary']) == '#8839ef'
assert resolve_color(styles['MaterialDarkTheme.TealA700']['colorSecondary']) == '#c6a0f6'
assert resolve_color(styles['LightTheme.TealA700']['colorSecondary']) == '#8839ef'
assert resolve_color(styles['DarkTheme.TealA700']['colorSecondary']) == '#c6a0f6'
labels = {e.attrib['name']: e.text for e in ET.parse(decoded / 'res/values/strings.xml').getroot()}
assert labels['theme_material_light'] == 'Catppuccin Latte (Material)'
assert labels['theme_material_dark'] == 'Catppuccin Macchiato (Material)'
for name in ('pref_about.xml', 'pref_about_v2.xml'):
    xml = (decoded / 'res/xml' / name).read_text()
    assert 'This app uses code from Patcheddit. To learn more, visit https://reddit.com/r/patcheddit' in xml
print(f'PASS: {len(dex_files)} DEX files unchanged; all four theme slots, accent variants, snackbar text, labels and attribution verified.')

# Regression: Material bottom navigation loads textColorSecondary by resource ID.
for name, items in styles.items():
    if name.startswith(('LightTheme', 'DarkTheme', 'MaterialLightTheme', 'MaterialDarkTheme')) and 'Catppuccin' in items.get('snackbarTextViewStyle', ''):
        for attr in ('android:textColorPrimary', 'android:textColorSecondary'):
            value = items[attr]
            assert value.startswith('@color/'), f'{name}: {attr} must be a resource reference'
            key = ('color', value.removeprefix('@color/'))
            assert int(after_ids[key], 16) != 0, f'{name}: {attr} has no resource ID'
print('PASS: Material navigation text colors have nonzero color resource IDs.')
