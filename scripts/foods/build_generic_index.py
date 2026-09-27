#!/usr/bin/env python3
"""
Builds the offline generic-foods index bundled with the web (public/foods/generic.json)
and the Android app (assets/foods/generic.json). Python standard library only.

Sources (open data, credited in the app and in Metodología):
  - Swiss Food Composition Database (FSVO), generic foods. Free to use, incl. in
    nutrition-diary apps, with acknowledgement of the source.
  - CIQUAL 2020 (ANSES), Licence Ouverte / Etalab 2.0.

Spanish first: most users are Spanish speakers, so every food gets a Spanish
name from scripts/foods/names-es.tsv (id<TAB>name, reviewable by hand). Foods
whose Spanish name repeats keep the first one (Swiss before CIQUAL), so a search
does not show near-duplicates.

Usage:
  python3 scripts/foods/build_generic_index.py            # download + build
  python3 scripts/foods/build_generic_index.py --export   # also write the names that
                                                           # still need a Spanish name
"""
import html
import json
import os
import re
import sys
import unicodedata
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(HERE, ".cache")
SWISS_URL = "https://naehrwertdaten.ch/wp-content/uploads/2026/07/Swiss_food_composition_database.xlsx"
CIQUAL_URL = "https://ciqual.anses.fr/cms/sites/default/files/inline-files/XML_2020_07_07.zip"
NAMES_ES = os.path.join(HERE, "names-es.tsv")
OUTPUTS = [
    os.path.join(ROOT, "public", "foods", "generic.json"),
    os.path.join(ROOT, "android", "app", "src", "main", "assets", "foods", "generic.json"),
]
# CIQUAL groups left out: baby food is not something adults log.
CIQUAL_EXCLUDED_GROUPS = {"baby food"}
# Brand-name mineral waters (0 kcal): noise in a macro tracker.
CIQUAL_EXCLUDED_NAMES = re.compile(r"^(Mineral (still |sparkling )?water|Spring (still )?water|Water, mineral)", re.I)

sys.path.insert(0, HERE)
import xlsx_reader  # noqa: E402


def fetch(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print("downloading", url)
        urllib.request.urlretrieve(url, path)
    return path


def num(value):
    try:
        return round(float(str(value).replace(",", ".")), 1)
    except (TypeError, ValueError):
        return None


def swiss_foods():
    rows = xlsx_reader.read(fetch(SWISS_URL, "swiss.xlsx"))
    header = rows[2]
    col = {name: header.index(name) for name in ("ID", "Name", "Energy, kilocalories (kcal)", "Fat, total (g)")}
    protein = next(i for i, h in enumerate(header) if h and h.startswith("Protein"))
    carbs = next(i for i, h in enumerate(header) if h and h.startswith("Carbohydrates, available"))
    foods = []
    for row in rows[3:]:
        if not row or not row[0]:
            continue
        cell = lambda i: row[i] if i < len(row) else None  # noqa: E731
        kcal = num(cell(col["Energy, kilocalories (kcal)"]))
        if kcal is None:
            continue
        foods.append({
            "id": "ch:" + row[col["ID"]],
            "source": "ch",
            "names": {"en": cell(col["Name"]).strip()},
            "per100g": [kcal, num(cell(protein)) or 0, num(cell(carbs)) or 0, num(cell(col["Fat, total (g)"])) or 0],
        })
    return foods


def ciqual_foods():
    folder = os.path.join(CACHE, "ciqual")
    if not os.path.isdir(folder):
        zipfile.ZipFile(fetch(CIQUAL_URL, "ciqual.zip")).extractall(folder)

    def blocks(name, tag):
        # The official XML has unescaped «<» and «&» in names: read it with regexes.
        text = open(os.path.join(folder, name), encoding="windows-1252").read()
        for block in re.findall(r"<%s>(.*?)</%s>" % (tag, tag), text, re.S):
            yield {k: html.unescape(v.strip()) for k, v in re.findall(r"<(\w+)>(.*?)</\1>", block, re.S)}

    groups = {g.get("alim_grp_code", ""): g.get("alim_grp_nom_eng", "") for g in blocks("alim_grp_2020_07_07.xml", "ALIM_GRP")}
    foods = {}
    for a in blocks("alim_2020_07_07.xml", "ALIM"):
        if groups.get(a.get("alim_grp_code", "")) in CIQUAL_EXCLUDED_GROUPS:
            continue
        if CIQUAL_EXCLUDED_NAMES.match(a.get("alim_nom_eng", "")):
            continue
        foods[a["alim_code"]] = {"fr": a.get("alim_nom_fr", ""), "en": a.get("alim_nom_eng", ""), "v": {}}

    compo = open(os.path.join(folder, "compo_2020_07_07.xml"), encoding="windows-1252").read()
    pattern = r"<alim_code>\s*(\d+)\s*</alim_code>\s*<const_code>\s*(\d+)\s*</const_code>\s*<teneur>\s*([^<]*?)\s*</teneur>"
    for code, const, value in re.findall(pattern, compo):
        if code in foods:
            value = value.strip()
            foods[code]["v"][const] = 0.0 if value.lower().startswith("traces") else num(value)

    result = []
    for code, food in foods.items():
        v = food["v"]
        # Energy: EU regulation kcal (328), else Jones kcal (333), else kJ (327) / 4.184.
        kcal = v.get("328") if v.get("328") is not None else v.get("333")
        if kcal is None and v.get("327") is not None:
            kcal = round(v["327"] / 4.184, 1)
        if kcal is None:
            continue
        result.append({
            "id": "ciqual:" + code,
            "source": "ciqual",
            "names": {"en": food["en"], "fr": food["fr"]},
            "per100g": [kcal, v.get("25000") or 0, v.get("31000") or 0, v.get("40000") or 0],
        })
    return result


def normalize(text):
    text = unicodedata.normalize("NFD", text)
    return " ".join("".join(c for c in text if unicodedata.category(c) != "Mn").lower().split())


def main():
    foods = swiss_foods() + ciqual_foods()
    names_es = {}
    if os.path.exists(NAMES_ES):
        for line in open(NAMES_ES, encoding="utf-8"):
            if "\t" in line and not line.startswith("#"):
                food_id, name = line.rstrip("\n").split("\t", 1)
                if name.strip():
                    names_es[food_id] = name.strip()

    missing = [f for f in foods if f["id"] not in names_es]
    if "--export" in sys.argv:
        with open(os.path.join(CACHE, "to-translate.tsv"), "w", encoding="utf-8") as out:
            for f in missing:
                out.write("%s\t%s\t%s\n" % (f["id"], f["names"]["en"], f["names"].get("fr", "")))
        print("exported", len(missing), "names without Spanish to", os.path.join(CACHE, "to-translate.tsv"))

    seen = set()
    index = []
    for f in foods:
        es = names_es.get(f["id"])
        if es:
            key = normalize(es)
            if key in seen:
                continue
            seen.add(key)
            f["names"] = {"es": es, **f["names"]}
        index.append({"id": f["id"], "s": f["source"], "n": f["names"], "v": f["per100g"]})

    payload = {
        "version": 1,
        "sources": {
            "ch": "Swiss Food Composition Database, FSVO (naehrwertdaten.ch)",
            "ciqual": "Anses, Table Ciqual 2020 (Licence Ouverte / Etalab 2.0)",
        },
        "foods": index,
    }
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    for path in OUTPUTS:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as out:
            out.write(text)
    print("foods:", len(index), "with Spanish name:", sum(1 for f in index if "es" in f["n"]),
          "without:", len(missing), "bytes:", len(text.encode()))


if __name__ == "__main__":
    main()
