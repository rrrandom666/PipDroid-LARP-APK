#!/usr/bin/env python3
"""Извлечение и замена блоков комментариев в исходнике Kotlin.

  list  <файл>            — блоки с якорем (первой строкой кода после блока)
  stats <файл>            — сводка по объёму
  apply <файл> <правки>   — применить замены (JSON: {"индекс": "новый текст"|""})

Комментарии не влияют на байткод, поэтому после apply слепок обязан совпасть побайтно.
"""
import json
import re
import sys

COMMENT = re.compile(r'^\s*(//|/\*|\*)')
BLANK = re.compile(r'^\s*$')


def blocks(lines):
    """Список блоков: (индекс, начало, конец, строки, отступ, якорь)."""
    out, i = [], 0
    while i < len(lines):
        if COMMENT.match(lines[i]):
            start = i
            while i < len(lines) and COMMENT.match(lines[i]):
                i += 1
            anchor = ''
            for j in range(i, min(i + 3, len(lines))):
                if not BLANK.match(lines[j]):
                    anchor = lines[j].rstrip()
                    break
            indent = re.match(r'\s*', lines[start]).group()
            out.append((len(out), start, i, lines[start:i], indent, anchor))
        else:
            i += 1
    return out


def main():
    mode, path = sys.argv[1], sys.argv[2]
    lines = open(path, encoding='utf-8').readlines()
    bs = blocks(lines)

    if mode == 'stats':
        total = sum(b[2] - b[1] for b in bs)
        multi = [b for b in bs if b[2] - b[1] > 1]
        print(f'файл: {len(lines)} строк')
        print(f'блоков: {len(bs)}, строк в них: {total} ({total * 100 // len(lines)}%)')
        print(f'многострочных блоков: {len(multi)}, строк в них: {sum(b[2] - b[1] for b in multi)}')

    elif mode == 'list':
        lo = int(sys.argv[3]) if len(sys.argv) > 3 else 0
        hi = int(sys.argv[4]) if len(sys.argv) > 4 else len(bs)
        for idx, start, end, body, _, anchor in bs[lo:hi]:
            if end - start < 2:
                continue
            print(f'### {idx} (строки {start + 1}-{end}, {end - start})')
            print(''.join(body).rstrip())
            print(f'--> {anchor.strip()[:110]}')
            print()

    elif mode == 'apply':
        edits = {}
        for src in sys.argv[3:]:
            edits.update(json.load(open(src, encoding='utf-8')))
        for idx, start, end, _, indent, _ in reversed(bs):
            key = str(idx)
            if key not in edits:
                continue
            text = edits[key]
            new = [] if not text else [f'{indent}{l}\n' for l in text.split('\n')]
            lines[start:end] = new
        open(path, 'w', encoding='utf-8').writelines(lines)
        print(f'применено правок: {len(edits)}')


main()
