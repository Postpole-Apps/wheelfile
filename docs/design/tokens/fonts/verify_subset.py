#!/usr/bin/env python3
"""Render sample text with the FULL Roboto Flex and the SUBSET side by side at the weights/widths Wheelfile uses; report pixel diff,
cmap coverage and digit widths. Writes subset-verify.png + subset-verify.json."""
import os, json
import numpy as np
from PIL import Image
from fontTools.ttLib import TTFont
from playwright.sync_api import sync_playwright
H = os.path.dirname(os.path.abspath(__file__)); FULL = os.path.abspath(os.path.join(H, '../../../src/assets/RobotoFlex.ttf')); SUB = os.path.join(H, 'roboto_flex.ttf')
SAMPLES = ['€75.12 · £41.20 · US$ · 62,400 km · 7.4 L/100km', 'Contrôle technique · Müller · Façade · España · Señal · Øresund · Łódź · Ş Ğ İ',
           'Crème brûlée — “quotes” ‘’ … × – › ↑ ↓ → ½ ° ™', '0123456789 · 1111111111 · 8888888888']
STY = [(400, 100, 16), (600, 100, 14), (700, 112, 28), (750, 110, 52), (800, 116, 40)]
def html(fam):
    rows = ''.join(f'<div style="font:{w} {s}px/{int(s*1.3)}px {fam};font-variation-settings:\'wdth\' {wd};font-feature-settings:\'tnum\';white-space:nowrap">{t}</div>'
                   for w, wd, s in STY for t in SAMPLES)
    return (f'<html><head><style>@font-face{{font-family:F;src:url("file://{FULL}")}}@font-face{{font-family:S;src:url("file://{SUB}")}}'
            f'body{{margin:0;padding:16px;background:#fff;color:#221A15;width:1700px}}</style></head><body>{rows}</body></html>')
out = {}
with sync_playwright() as p:
    b = p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg = b.new_page(viewport={'width': 1740, 'height': 400})
    shots = {}
    for k, fam in (('full', 'F'), ('subset', 'S')):
        hp = f'/tmp/verify-{k}.html'; open(hp, 'w').write(html(fam)); pg.goto('file://' + hp); pg.evaluate('document.fonts.ready'); pg.wait_for_timeout(200)
        out[f'{k}_loaded'] = pg.evaluate(f'document.fonts.check("16px {fam}")')
        pg.screenshot(path=f'/tmp/verify-{k}.png', full_page=True); shots[k] = np.array(Image.open(f'/tmp/verify-{k}.png').convert('L')).astype(int)
    b.close()
a, c = shots['full'], shots['subset']; h = min(a.shape[0], c.shape[0])
diff = np.abs(a[:h] - c[:h]); out['pixels_differing'] = int((diff > 8).sum()); out['pixels_total'] = int(diff.size); out['max_diff'] = int(diff.max())
out['differing_fraction'] = round(out['pixels_differing'] / diff.size, 7)  # anti-aliasing rounding from pinning axes
f = TTFont(SUB); cm = f.getBestCmap()
out['missing_chars'] = sorted({ch for t in SAMPLES for ch in t if ch.strip() and ord(ch) not in cm})
out['axes'] = [(x.axisTag, x.minValue, x.defaultValue, x.maxValue) for x in f['fvar'].axes]
out['glyphs'] = len(f.getGlyphOrder()); out['digit_advances'] = sorted({f['hmtx'][cm[ord(d)]][0] for d in '0123456789'})
out['gsub_features'] = sorted({r.FeatureTag for r in f['GSUB'].table.FeatureList.FeatureRecord})
out['bytes'] = dict(full=os.path.getsize(FULL), subset=os.path.getsize(SUB))
out['pass'] = out['subset_loaded'] and not out['missing_chars'] and out['differing_fraction'] < 1e-4 and len(out['digit_advances']) == 1
im = Image.fromarray(np.vstack([a[:h], np.full((12, a.shape[1]), 200), c[:h]]).astype('uint8')).convert('RGB'); im.save(os.path.join(H, 'subset-verify.png'), optimize=True)
json.dump(out, open(os.path.join(H, 'subset-verify.json'), 'w'), indent=1, ensure_ascii=False); print(json.dumps(out, ensure_ascii=False))
