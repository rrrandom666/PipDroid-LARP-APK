#!/usr/bin/env python3
"""Сверка выноса кода в контроллер: что переехало дословно, а что подменено.

Слепок refactor_snapshot.sh для выноса бесполезен — методы меняют класс, и байткод
расходится весь. Здесь вместо байткода сверяется текст: строки исходных диапазонов
нормализуются (пустые и комментарии выброшены), к ним применяются механические
переименования, и результат сверяется с новым файлом в обе стороны.

Смысл не в нулевом выводе, а в том, чтобы КАЖДОЕ расхождение было заявленным:
подстановка колбэка, смена приёмника (this -> activity), private -> public.
Незаявленное расхождение — потерянная или придуманная строка.

Пример (вынос Часов, волна 3):
    ./dev-tools/controller_extract_check.py \\
        --rev HEAD --orig app/src/main/java/com/malto4/pipdroid/MainActivity.kt \\
        --new app/src/main/java/com/malto4/pipdroid/ClockController.kt \\
        --range 4021-4106 --range 4507-4911 --range 4948-4954 \\
        --range 4959-5231 --range 6997-7238 \\
        --rename bindingMain=binding --rename sharedPreferences=prefs \\
        --rename menuNavigator=navigator \\
        --re 'playTickAudio\\(\\)=playTick()' --re 'getString\\(=activity.getString('
"""

import argparse
import re
import subprocess
import sys


def normalize(lines):
    """Пустые строки и комментарии выброшены: сверяется код, а не оформление."""
    out = []
    for line in lines:
        s = line.strip()
        if not s or s.startswith("//") or s.startswith("/**") or s.startswith("*"):
            continue
        out.append(s)
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--rev", default="HEAD", help="ревизия с исходным файлом до выноса")
    p.add_argument("--orig", required=True, help="путь к исходному файлу в этой ревизии")
    p.add_argument("--new", required=True, help="путь к новому файлу контроллера")
    p.add_argument("--range", dest="ranges", action="append", default=[],
                   metavar="A-B", help="диапазон строк оригинала, 1-based, включительно")
    p.add_argument("--rename", action="append", default=[], metavar="СТАРОЕ=НОВОЕ",
                   help="переименование по границам слова")
    p.add_argument("--re", dest="regexes", action="append", default=[], metavar="РЕГЭКСП=ЗАМЕНА",
                   help="то же произвольным регэкспом, для вызовов со скобками")
    args = p.parse_args()

    original = subprocess.run(["git", "show", f"{args.rev}:{args.orig}"],
                              capture_output=True, text=True, check=True).stdout.split("\n")
    source = []
    for r in args.ranges:
        a, b = (int(x) for x in r.split("-"))
        source += original[a - 1:b]
    if not source:
        sys.exit("Не задано ни одного --range: сверять нечего.")

    subs = [(re.compile(rf"\b{re.escape(k)}\b"), v)
            for k, v in (s.split("=", 1) for s in args.rename)]
    subs += [(re.compile(k), v) for k, v in (s.split("=", 1) for s in args.regexes)]

    def apply_renames(s):
        for pattern, repl in subs:
            s = pattern.sub(repl, s)
        return s

    old = [apply_renames(x) for x in normalize(source)]
    new = normalize(open(args.new).read().split("\n"))
    old_set, new_set = set(old), set(new)

    print(f"строк в диапазонах оригинала: {len(old)}")
    print(f"строк в новом файле:          {len(new)}\n")
    for title, rows, other in (("ЕСТЬ В ОРИГИНАЛЕ, НЕТ В НОВОМ", old, new_set),
                               ("ЕСТЬ В НОВОМ, НЕТ В ОРИГИНАЛЕ", new, old_set)):
        missing = list(dict.fromkeys(x for x in rows if x not in other))
        print(f"=== {title} ({len(missing)}) ===")
        for x in missing:
            print("   ", x)
        print()
    print("Каждая строка выше обязана быть заявленной подменой. Иначе — потеря.")


if __name__ == "__main__":
    main()
