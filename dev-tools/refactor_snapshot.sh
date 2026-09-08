#!/bin/bash
# Слепок собранного приложения для сверки до/после рефакторинга.
#
#   ./dev-tools/refactor_snapshot.sh before      — снять слепок
#   ./dev-tools/refactor_snapshot.sh after
#   ./dev-tools/refactor_snapshot.sh diff before after
#
# Смысл: шаги рефакторинга, которые не должны менять поведение (чистка комментариев,
# удаление мёртвого кода), обязаны давать идентичный байткод и идентичный набор
# ресурсов. Компилятор этого не проверяет — проверяет сверка слепков.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SNAPDIR="/tmp/pipdroid-snapshots"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
JAVAP="$JAVA_HOME/bin/javap"

snapshot() {
    local label="$1"
    local out="$SNAPDIR/$label"
    rm -rf "$out" && mkdir -p "$out"

    echo "==> Сборка"
    (cd "$ROOT" && ./gradlew assembleDebug --console=plain -q)

    echo "==> Байткод"
    local classes="$ROOT/app/build/tmp/kotlin-classes/debug"
    (cd "$classes" && find . -name '*.class' | sort | sed 's|^\./||; s|\.class$||' | tr '/' '.' \
        | xargs "$JAVAP" -p -c -classpath "$classes" 2>/dev/null) > "$out/bytecode.txt"

    echo "==> Ресурсы: файлы"
    (cd "$ROOT/app/src/main/res" && find . -type f | sort) > "$out/res-files.txt"

    echo "==> Ресурсы: имена в values"
    grep -rhoE '<(string|string-array|color|dimen|style|integer|bool|array) name="[^"]+"' \
        "$ROOT/app/src/main/res/values" | sort -u > "$out/res-names.txt"

    echo "==> Динамические имена (невидимые компилятору)"
    local src="$ROOT/app/src/main/java"
    { grep -rhoE 'getIdentifier\("[^"]*"' "$src" || true
      # значения ключей SharedPreferences: и через константы *_SPKey, и голыми литералами
      grep -rhoE '[a-zA-Z0-9_]+_SPKey = "[^"]+"' "$src" || true
      grep -rhoE '(get|put)(String|Int|Boolean|Float|Long|StringSet)\("[^"]+"' "$src" \
        | grep -oE '"[^"]+"' || true
      # текстовые команды протокола BLE — контракт с прошивкой ESP32
      grep -rhoE '"(STATS|ITEMS|DATA|POWER|ENCBTN|ENC|GEIGER|HOLOTAPE|HOLOTAPES|RADIOPWR|RADIOFREQ)[^"]*"' "$src" || true
    } | sort -u > "$out/dynamic-names.txt"

    wc -l "$out"/*.txt | sed 's|.*/||'
    echo "==> Слепок: $out"
}

compare() {
    local a="$SNAPDIR/$1" b="$SNAPDIR/$2" rc=0
    for f in bytecode.txt res-files.txt res-names.txt dynamic-names.txt; do
        if diff -q "$a/$f" "$b/$f" >/dev/null 2>&1; then
            echo "OK       $f"
        else
            echo "РАЗЛИЧИЯ $f  ($(diff "$a/$f" "$b/$f" | grep -c '^[<>]') строк)"
            rc=1
        fi
    done
    return $rc
}

case "${1:-}" in
    diff) compare "$2" "$3" ;;
    "")   echo "нужен label или diff <a> <b>"; exit 1 ;;
    *)    snapshot "$1" ;;
esac
