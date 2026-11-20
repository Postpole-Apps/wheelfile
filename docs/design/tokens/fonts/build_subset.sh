#!/usr/bin/env bash
# Roboto Flex subset for Wheelfile (bundled as app/src/main/res/font/roboto_flex.ttf). fonttools 4.65.
# step one: pin the axes Wheelfile never uses (GRAD, slnt, parametric XOPQ…YTFI) -> keeps opsz 8–144, wght 100–1000, wdth 25–151
# step two: subset to Latin + Latin-1 + Latin Extended-A/B + Latin Extended Additional + punctuation, currency, letterlike, arrows; keep all OpenType features
set -e
cd "$(dirname "$0")"
SRC=../../../src/assets/RobotoFlex.ttf
fonttools varLib.instancer "$SRC" GRAD=0 slnt=0 XOPQ=drop YOPQ=drop XTRA=drop YTUC=drop YTLC=drop YTAS=drop YTDE=drop YTFI=drop -o /tmp/rf_3axis.ttf
pyftsubset /tmp/rf_3axis.ttf \
  --unicodes="U+0000-00FF,U+0100-024F,U+0259,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+1E00-1EFF,U+2000-206F,U+2070-209F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2212,U+2215,U+2248,U+2260,U+2264-2265,U+FEFF,U+FFFD" \
  --layout-features='*' --name-IDs='*' --name-languages='*' --notdef-outline \
  --output-file=roboto_flex.ttf
ls -l "$SRC" roboto_flex.ttf
