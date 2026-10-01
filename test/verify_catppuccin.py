#!/usr/bin/env python3
"""Verify distinct theme registration and restoration after applying over 0.1.x.
Usage: verify_catppuccin.py stock.apk patched.apk stock-decoded patched-decoded
Decode the patched APK including DEX to inspect the scoped code hooks.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as E
_, _, stock, patched = map(Path, sys.argv[1:])
def items(path):
 return {e.get('name'): (e.get('parent'), {i.get('name'):i.text for i in e}) for e in E.parse(path).getroot() if e.tag=='style'}
for directory in (stock/'res').glob('values*'):
 p=directory/'styles.xml'
 if not p.exists():continue
 original=items(p); actual=items(patched/'res'/directory.name/'styles.xml')
 for name,value in original.items():
  assert actual[name]==value, f'Original theme/widget changed: {directory.name}/{name}'
styles=items(patched/'res/values/styles.xml')
for flavor in ['Latte','Macchiato']:
 assert styles['Catppuccin.'+flavor][0]=='@style/Material'+('LightTheme' if flavor=='Latte' else 'DarkTheme')
 for role in ['Card','FullCard','MiniCard']:
  assert 'catppuccin' in styles[f'Catppuccin.{flavor}.{role}'][1]['cardBackgroundColor']
public={(e.get('type'),e.get('name')):e.get('id') for e in E.parse(patched/'res/values/public.xml').getroot()}
for e in E.parse(stock/'res/values/public.xml').getroot():
 assert public[e.get('type'),e.get('name')]==e.get('id')
assert public['style','Catppuccin.Latte']=='0x7f141000'
assert public['style','Catppuccin.Macchiato']=='0x7f141001'
for e in E.parse(patched/'res/values/arrays.xml').getroot():
 if e.get('name','').startswith('pref_theme_values'):
  values=[i.text for i in e];assert values.count('17')==values.count('18')==1
restore=E.parse(Path(__file__).resolve().parents[1]/'patches/src/main/resources/catppuccin/restore.xml').getroot()
def semantic(e):return (e.tag,dict(e.attrib),(e.text or '').strip(),[semantic(c) for c in e])
for file in restore:
 if file.get('mode')=='document':
  assert semantic(E.parse(stock/file.get('path')).getroot())==semantic(E.parse(patched/file.get('path')).getroot()),file.get('path')
utils=next(patched.glob('smali*/he/f0.smali')).read_text()
assert 'Catppuccin.Latte' in utils and 'Catppuccin.Macchiato' in utils
assert '0x7f141000' in utils and '0x7f141001' in utils
menu=next(patched.glob('smali*/com/rubenmayayo/reddit/ui/compose/FormatActivity.smali')).read_text()
assert '-0x395f0a' in menu and '-0x77c611' in menu
assert 'setIconTintList' in menu
print('PASS: original styles/widgets/layouts and resource IDs preserved; distinct Catppuccin slots and scoped send tint registered.')

assert 'catppuccinViewerTheme' in utils and 'catppuccinViewerControls' in utils
for activity in ('ImageActivity', 'MediaImageActivity', 'HDImageActivity', 'GalleryActivity'):
    code = next(patched.glob(f'smali*/com/rubenmayayo/reddit/ui/activities/{activity}.smali')).read_text()
    assert '->catppuccinViewerTheme' in code and '->catppuccinViewer(' in code, activity
    create = code.split('.method protected onCreate(')[1].split('.end method')[0]
    # The old hook before return was bypassed by branches targeting that return.
    assert create.index('setContentView') < create.index('->catppuccinViewer(') < create.index('Lbutterknife/ButterKnife;->bind'), activity
print('PASS: image viewers call scoped palette helpers; original viewer resources remain unchanged.')

assert 'catppuccinViewerImageBounds' not in utils
assert utils.count('invoke-virtual {v0, v4}, Landroid/view/View;->setBackgroundColor(I)V') >= 2
print('PASS: full-window image canvas with transparent control panels.')
