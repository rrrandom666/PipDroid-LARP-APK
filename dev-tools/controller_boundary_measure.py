#!/usr/bin/env python3
"""Замер границы перед выносом экрана в контроллер: где она проходит на самом деле.

Вынос Часов показал, почему это нужно отдельным шагом: заход планировался узким
(только доменные функции), а замер его опроверг — 65 обращений к полям экрана извне,
и узкий вынос дал бы класс с полутора десятками публичных свойств. Решение принимается
по числу обращений снаружи, а не по ощущению «этот кусок выглядит отдельным».

    ./dev-tools/controller_boundary_measure.py --pattern '[Jj]ournal|[Ee]ntry'
    ./dev-tools/controller_boundary_measure.py --pattern '[Mm]ap|[Mm]arker|[Rr]oute' --top 15

Что считает:
  * члены класса, чьё ИМЯ подошло под шаблон — сколько их и на сколько строк;
  * поля состояния среди них (val/var) и обращения к этим полям из НЕподошедших
    членов — это и есть будущая публичная поверхность контроллера;
  * блоки внутри длинной функции-обёртки (по умолчанию onCreate): они уезжают
    вместе с выносом, поэтому обращения оттуда считаются отдельно и не пугают.

ВАЖНО: классификация идёт по именам и врёт в обе стороны, список членов надо
просмотреть глазами.
  * Недосчёт: на выносе Карты мимо шаблона прошли armTapMode, updateActiveNavigation,
    rerouteActiveNavigation, currentLocationOrNull — оценка «~1700 строк» оказалась
    ниже факта (1801).
  * Пересчёт: на замере Журнала шаблон '[Ee]ntr' притянул listEntries (53 строки) —
    это экран фильтра, к Журналу отношения не имеет.
"""

import argparse
import re
import sys

DECL = re.compile(
    r"^    (?:@\w+(?:\([^)]*\))?\s+)?(?:private |internal |public |protected )?"
    r"(?:override )?(?:lateinit )?(?:inner )?"
    r"(?:fun|val|var|class|object|enum class|companion object)\b"
)
FIELD = re.compile(r"^    (?:private |internal |public |protected )?(?:lateinit )?va[lr] ")
NAME = re.compile(r"(?:fun|val|var|class|object)\s+([A-Za-z_]\w*)")


def members(lines):
    """Члены класса верхнего уровня: имя, диапазон строк (1-based), тело."""
    starts = [i for i, l in enumerate(lines) if DECL.match(l)] + [len(lines)]
    out = []
    for a, b in zip(starts, starts[1:]):
        m = NAME.search(lines[a])
        out.append({
            "name": m.group(1) if m else lines[a].strip()[:40],
            "start": a + 1,
            "end": b,
            "size": b - a,
            "head": lines[a],
            "body": "\n".join(lines[a:b]),
        })
    return out


def wrapper_range(lines, name):
    """Диапазон длинной функции-обёртки по балансу скобок."""
    start = next((i for i, l in enumerate(lines) if re.search(rf"fun {name}\b", l)), None)
    if start is None:
        return None
    depth = 0
    for i in range(start, len(lines)):
        depth += lines[i].count("{") - lines[i].count("}")
        if i > start and depth <= 0:
            return start, i
    return start, len(lines) - 1


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--file", default="app/src/main/java/com/malto4/pipdroid/MainActivity.kt")
    p.add_argument("--pattern", required=True, help="регэксп по именам членов, напр. '[Jj]ournal|[Ee]ntry'")
    p.add_argument("--wrapper", default="onCreate", help="длинная функция, уезжающая вместе с выносом")
    p.add_argument("--top", type=int, default=10, help="сколько самых больших членов показать")
    p.add_argument("--gap", type=int, default=8, help="разрыв в строках, склеивающий блоки внутри обёртки")
    args = p.parse_args()

    lines = open(args.file).read().split("\n")
    kw = re.compile(args.pattern)
    all_members = members(lines)
    matched = [m for m in all_members if kw.search(m["name"])]
    if not matched:
        sys.exit(f"Под шаблон {args.pattern!r} не подошёл ни один член класса.")
    matched_names = {m["name"] for m in matched}
    fields = [m["name"] for m in matched if FIELD.match(m["head"])]

    total = sum(m["size"] for m in matched)
    print(f"Файл: {args.file}")
    print(f"Всего членов класса: {len(all_members)}, строк в них: {sum(m['size'] for m in all_members)}")
    print(f"\nПодошло под шаблон: {len(matched)} членов, {total} строк")
    for m in sorted(matched, key=lambda x: -x["size"])[: args.top]:
        print(f"  {m['size']:5d}  :{m['start']:<6d} {m['name']}")

    print(f"\nПоля состояния среди них: {len(fields)}")
    print("  " + ", ".join(fields) if fields else "  (нет)")

    wrapper = wrapper_range(lines, args.wrapper)
    wrapper_span = range(wrapper[0], wrapper[1] + 1) if wrapper else range(0)

    inside, outside = {}, {}
    for m in all_members:
        if m["name"] in matched_names:
            continue
        target = inside if (m["start"] - 1) in wrapper_span else outside
        for f in fields:
            n = len(re.findall(rf"\b{re.escape(f)}\b", m["body"]))
            if n:
                target.setdefault(f, []).append((m["name"], n))
    # Обёртку члены не покрывают: она сама член, поэтому считаем её тело отдельно.
    if wrapper:
        wrapper_body = "\n".join(lines[wrapper[0]:wrapper[1] + 1])
        for f in fields:
            n = len(re.findall(rf"\b{re.escape(f)}\b", wrapper_body))
            if n:
                inside.setdefault(f, []).append((args.wrapper, n))

    out_total = sum(c for v in outside.values() for _, c in v)
    in_total = sum(c for v in inside.values() for _, c in v)
    print(f"\nОбращения к этим полям ИЗВНЕ, без {args.wrapper}(): {out_total}")
    print("  ↑ это и есть будущая публичная поверхность контроллера")
    for f, v in sorted(outside.items(), key=lambda x: -sum(c for _, c in x[1])):
        print(f"  {f}: {sum(c for _, c in v)}  ← {', '.join(n for n, _ in v)}")
    print(f"\nОбращения из {args.wrapper}(): {in_total} — уезжают вместе с блоком, в поверхность не входят")

    if wrapper:
        hits = [i for i in wrapper_span if kw.search(lines[i])]
        blocks, cur = [], []
        for i in hits:
            if cur and i - cur[-1] > args.gap:
                blocks.append(cur)
                cur = []
            cur.append(i)
        if cur:
            blocks.append(cur)
        big = [b for b in blocks if b[-1] - b[0] >= 10]
        span = sum(b[-1] - b[0] + 1 for b in big)
        print(f"\nБлоки внутри {args.wrapper}() ({args.wrapper}: строки {wrapper[0] + 1}-{wrapper[1] + 1}):")
        for b in big:
            print(f"  {b[0] + 1}-{b[-1] + 1}  ({b[-1] - b[0] + 1} строк)")
        print(f"\nОЦЕНКА РАЗМЕРА КОНТРОЛЛЕРА (снизу): {total} + {span} = {total + span} строк")
    print("\nСписок членов просмотреть глазами: шаблон по именам и недобирает "
          "(член назван без ключевого слова), и притягивает чужих (слово совпало случайно).")


if __name__ == "__main__":
    main()
