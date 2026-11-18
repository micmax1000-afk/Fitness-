#!/bin/bash
# Prova dell'app su un telefono virtuale: installa, riempie l'app con dati finti, apre le schermate e fa tocchi casuali.
set -u
APK=${1:-app-debug.apk}
PKG=com.micmax.cibo
OUT=ui-shots
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g "$APK" || exit 1
adb logcat -c
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "schermata: $1"; }
alive() { adb shell pidof $PKG >/dev/null; }
why() { adb logcat -d -s AndroidRuntime:E | tail -60; adb logcat -d -s AndroidRuntime:E | tail -60 > "$OUT/crash.txt" || true; }
start() { adb shell am start -W -n $PKG/.MainActivity "$@" >/dev/null; }

start
shot 01-home 6
alive || { echo "::error::L'app si e' chiusa all'avvio"; why; exit 1; }

start --es action backup
for i in $(seq 1 15); do sleep 2; adb logcat -d -s CIBO_SELFTEST:* | grep -q 'BACKUP' && break; done
BK=$(adb logcat -d -s CIBO_SELFTEST:* | grep -o 'BACKUP.*' | tail -1)
echo "Dati: $BK"
case "$BK" in
  BACKUP\ bytes=*importati=0/0*) ;;
  *) echo "::error::Esporta/importa non funziona ($BK)"; adb logcat -d -s AndroidRuntime:E | tail -30; exit 1;;
esac

start --es action reminders
for i in $(seq 1 15); do sleep 2; adb logcat -d -s CIBO_SELFTEST:* | grep -q 'REMINDERS' && break; done
RM=$(adb logcat -d -s CIBO_SELFTEST:* | grep -o 'REMINDERS.*' | tail -1)
echo "Promemoria: $RM"
case "$RM" in
  REMINDERS\ count=4\ meds=1\ visite=1\ pdf=true\ peso=62.0*) ;;
  *) echo "::error::I promemoria o il referto non funzionano ($RM)"; adb logcat -d -s AndroidRuntime:E | tail -30; exit 1;;
esac

start --es action reminders
for i in $(seq 1 15); do sleep 2; adb logcat -d -s CIBO_SELFTEST:* | grep -q 'TRAIN' && break; done
TR=$(adb logcat -d -s CIBO_SELFTEST:* | grep -o 'TRAIN.*' | tail -1)
echo "Allenamento: $TR"
case "$TR" in
  TRAIN\ settimana=[0-9]*\ tabata=235\ scheda=19) ;;
  *) echo "::error::I conti dell'allenamento non tornano ($TR)"; exit 1;;
esac

for i in $(seq 1 10); do sleep 1; adb logcat -d -s CIBO_SELFTEST:* | grep -q 'ACQUA' && break; done
AQ=$(adb logcat -d -s CIBO_SELFTEST:* | grep -o 'ACQUA.*' | tail -1)
echo "Acqua: $AQ"
[ "$AQ" = "ACQUA accesi=5 ririaccesi=5 spenti=0" ] || { echo "::error::I promemoria per bere non tornano ($AQ)"; exit 1; }

for a in steps sleep train plans runplan tabata acts hc body meds visits emergency report diary water calc recipes recipe cooking menu shopping settings web; do
  start --es action $a
  shot "02-$a" 6
  alive || { echo "::error::L'app si e' chiusa nella schermata $a"; why; exit 1; }
  adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_BACK
  sleep 1
done
start
shot 03-home-con-dati 4

adb shell monkey -p $PKG --throttle 150 --ignore-timeouts --pct-syskeys 0 --pct-appswitch 0 -s 7 300 >"$OUT/monkey.txt" 2>&1
tail -3 "$OUT/monkey.txt"
alive || { echo "::error::L'app si e' chiusa durante i tocchi casuali"; why; exit 1; }
echo "Prova completata"
