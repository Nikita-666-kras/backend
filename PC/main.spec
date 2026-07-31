# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[('price_list.json', '.'), ('DJI_T50.pdf', '.'), ('HD525.pdf', '.'), ('HD540.pdf', '.'), ('HD580.pdf', '.'), ('DJI_T30.pdf', '.'), ('DJI_T10.pdf', '.'), ('Прицеп_БАС.pdf', '.'), ('Прицеп_ТЕНТ.pdf', '.'), ('Прицеп_1_дрон.pdf', '.'), ('Растворник_на_1000л.pdf', '.'), ('DJI_M3M.pdf', '.'), ('Manrope-Bold.ttf', '.'), ('Manrope-ExtraBold.ttf', '.'), ('Manrope-ExtraLight.ttf', '.'), ('Manrope-Light.ttf', '.'), ('Manrope-Medium.ttf', '.'), ('Manrope-Regular.ttf', '.'), ('Manrope-SemiBold.ttf', '.')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='main',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
