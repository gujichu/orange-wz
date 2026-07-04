#!/usr/bin/env python3
"""Compare TMS273 reference Skill JSON vs exported JSON."""
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path


def is_sub(obj):
    return isinstance(obj, dict) and obj.get("_dirType") == "sub"


def compare(ref, out, path=""):
    diffs = []
    if type(ref) != type(out):
        diffs.append((path, "type", type(ref).__name__, type(out).__name__))
        return diffs

    if not isinstance(ref, dict):
        if ref != out:
            diffs.append((path, "value", ref, out))
        return diffs

    ref_dt = ref.get("_dirType")
    out_dt = out.get("_dirType")
    if ref_dt != out_dt:
        diffs.append((path, "_dirType", ref_dt, out_dt))
        return diffs

    if ref_dt == "sub":
        ref_keys = {k for k in ref if k != "_dirType"}
        out_keys = {k for k in out if k != "_dirType"}
        for k in sorted(ref_keys - out_keys):
            diffs.append((f"{path}/{k}" if path else k, "missing_in_out", "present", None))
        for k in sorted(out_keys - ref_keys):
            diffs.append((f"{path}/{k}" if path else k, "extra_in_out", None, "present"))
        for k in sorted(ref_keys & out_keys):
            child_path = f"{path}/{k}" if path else k
            diffs.extend(compare(ref[k], out[k], child_path))
        return diffs

    value_keys = [k for k in ref if k != "_dirType"]
    for k in value_keys:
        rk, ok = ref.get(k), out.get(k)
        if rk != ok:
            diffs.append((f"{path}.{k}" if path else k, "field", rk, ok))
    return diffs


def main():
    ref_dir = Path(r"G:/273/server273/WZ_JSON_TW/Skill")
    out_dir = Path(r"G:/273/XMLOUT/Skill_00000.ms")
    if len(sys.argv) >= 3:
        ref_dir = Path(sys.argv[1])
        out_dir = Path(sys.argv[2])

    common = sorted(set(p.name for p in ref_dir.glob("*.json")) & set(p.name for p in out_dir.glob("*.json")))
    print(f"common files: {len(common)}")

    diff_types = Counter()
    missing_prefix = Counter()
    extra_prefix = Counter()
    field_name = Counter()
    per_file = []

    for name in common:
        with open(ref_dir / name, encoding="utf-8") as f:
            ref = json.load(f)
        with open(out_dir / name, encoding="utf-8") as f:
            out = json.load(f)
        diffs = compare(ref, out)
        if not diffs:
            continue
        per_file.append((name, len(diffs), diffs[:8]))
        for path, kind, rv, ov in diffs:
            diff_types[kind] += 1
            if kind == "missing_in_out":
                missing_prefix[path.split("/")[0]] += 1
            elif kind == "extra_in_out":
                extra_prefix[path.split("/")[0]] += 1
            elif kind == "field":
                field_name[path.split(".")[-1]] += 1

    print(f"files with diffs: {len(per_file)} / {len(common)}")
    print("diff kinds:", dict(diff_types))
    print("top missing roots:", missing_prefix.most_common(12))
    print("top extra roots:", extra_prefix.most_common(12))
    print("top field mismatches:", field_name.most_common(12))
    print("\n--- samples ---")
    for name, count, samples in sorted(per_file, key=lambda x: -x[1])[:10]:
        print(f"\n{name} ({count} diffs)")
        for s in samples:
            print(" ", s)


if __name__ == "__main__":
    main()
