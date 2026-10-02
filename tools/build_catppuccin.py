#!/usr/bin/env python3
"""Build the standalone theme MPP using public Morphe and Kotlin artifacts.

The full upstream bundle still uses its original Gradle build. This focused
build needs no GitHub Packages token and can be combined with that bundle.
"""
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / '.cache' / 'catppuccin'
BUILD = ROOT / 'build' / 'catppuccin'
SOURCE = ROOT / 'patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/theme/CatppuccinThemePatch.kt'
MORPHE_VERSION = '1.18.0'
KOTLIN_VERSION = '2.3.21'


def fetch(name, url):
    target = CACHE / name
    if not target.exists():
        print(f'Downloading {name}', flush=True)
        temporary = target.with_suffix('.download')
        urllib.request.urlretrieve(url, temporary)
        temporary.replace(target)
    return target


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    BUILD.mkdir(parents=True, exist_ok=True)
    java_home = Path(os.environ['JAVA_HOME'])
    sdk = Path(os.environ['ANDROID_HOME'])
    java = java_home / 'bin/java'
    morphe = fetch(f'morphe-desktop-{MORPHE_VERSION}-all.jar',
        f'https://github.com/MorpheApp/morphe-desktop/releases/download/v{MORPHE_VERSION}/morphe-desktop-{MORPHE_VERSION}-all.jar')
    compiler = fetch(f'kotlin-compiler-embeddable-{KOTLIN_VERSION}.jar',
        f'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/{KOTLIN_VERSION}/kotlin-compiler-embeddable-{KOTLIN_VERSION}.jar')
    annotations = fetch('annotations-26.0.2.jar',
        'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar')
    classes = BUILD / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    run(java, '-cp', os.pathsep.join(map(str, (compiler, morphe, annotations))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', os.pathsep.join(map(str, (morphe, annotations))),
        '-language-version', '2.2', '-jvm-target', '11', '-d', classes, SOURCE)
    bundle = ROOT / 'build/boost-catppuccin-0.2.10.mpp'
    manifest = ('Manifest-Version: 1.0\nName: Boost - Catppuccin\n'
        'Description: Catppuccin themes for Boost\nVersion: 0.2.10\n'
        'Author: titandrive\nSource: https://github.com/titandrive/boost-catppuccin\n'
        'License: GPL-3.0 with upstream NOTICE conditions\n\n')
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', manifest)
        for path in sorted(classes.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(classes))
        archive.write(ROOT / 'patches/src/main/resources/catppuccin/styles.xml', 'catppuccin/styles.xml')
        archive.write(ROOT / 'patches/src/main/resources/catppuccin/palette.xml', 'catppuccin/palette.xml')
        archive.write(ROOT / 'patches/src/main/resources/catppuccin/restore.xml', 'catppuccin/restore.xml')
        for name in ('LICENSE', 'NOTICE'):
            archive.write(ROOT / name, name)
    dex = BUILD / 'classes.zip'
    d8 = sorted((sdk / 'build-tools').glob('*/lib/d8.jar'))[-1]
    android_jar = sorted((sdk / 'platforms').glob('*/android.jar'))[-1]
    input_jar = BUILD / 'patches.jar'
    shutil.copyfile(bundle, input_jar)
    run(java, '-cp', d8, 'com.android.tools.r8.D8',
        '--release', '--min-api', '26', '--lib', android_jar,
        '--classpath', morphe, '--output', dex, input_jar)
    with zipfile.ZipFile(bundle, 'a') as archive, zipfile.ZipFile(dex) as dex_archive:
        for name in dex_archive.namelist():
            archive.writestr(name, dex_archive.read(name))
    print(f'Built {bundle}', flush=True)


if __name__ == '__main__':
    main()
