#!/usr/bin/env python3
"""Extracts the bundled hospital list from the data.go.kr 「전국 병의원 및 약국 현황」 zip (dataset 15051059).

Keeps only the five columns the server imports from the `병원정보서비스` CSV, in the same CP949 CSV format,
gzip-compressed without a timestamp so the same source always produces the same bytes. Replaces the previous
bundled file. Public institution data only; there are no personal records in this file.

    python3 -I backend/scripts/extract_hospital_list.py ~/Downloads/전국\\ 병의원\\ 및\\ 약국\\ 현황.zip
"""
import csv
import gzip
import io
import os
import re
import sys
import zipfile
from pathlib import Path

COLUMNS = ["암호화요양기호", "요양기관명", "종별코드", "종별코드명", "주소"]
TARGET = Path(__file__).resolve().parent.parent / "src/main/resources/hospitals"


def entry_name(info: zipfile.ZipInfo) -> str:
    # data.go.kr zips store CP949 names without the UTF-8 flag; zipfile then decodes them as CP437.
    if info.flag_bits & 0x800:
        return info.filename
    return info.filename.encode("cp437").decode("cp949")


def main(source: str) -> None:
    with zipfile.ZipFile(source) as archive:
        info = next(i for i in archive.infolist() if "병원정보서비스" in entry_name(i) and entry_name(i).endswith(".csv"))
        period = re.search(r"\((\d{4})\.(\d{1,2})\.?\)", entry_name(info))
        if not period:
            sys.exit(f"no period in {entry_name(info)}")
        reader = csv.reader(io.TextIOWrapper(archive.open(info), encoding="cp949", newline=""))
        header = [c.strip().lstrip("﻿") for c in next(reader)]
        index = [header.index(c) for c in COLUMNS]
        out = io.StringIO()
        writer = csv.writer(out, lineterminator="\r\n")
        writer.writerow(COLUMNS)
        rows = 0
        for record in reader:
            if any(field.strip() for field in record):
                writer.writerow([record[i] if i < len(record) else "" for i in index])
                rows += 1

    TARGET.mkdir(parents=True, exist_ok=True)
    target = TARGET / f"hira-hospitals({period.group(1)}.{int(period.group(2))}.).csv.gz"
    # Write the new list completely before touching the old one, so a failure never leaves the server without a list.
    partial = TARGET / (target.name + ".partial")
    partial.write_bytes(gzip.compress(out.getvalue().encode("cp949"), compresslevel=9, mtime=0))
    os.replace(partial, target)
    for old in TARGET.glob("*.csv.gz"):
        if old != target:
            old.unlink()
    print(f"{target.name}: {rows} rows, {target.stat().st_size // 1024} KB")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: extract_hospital_list.py <data.go.kr zip>")
    main(sys.argv[1])
