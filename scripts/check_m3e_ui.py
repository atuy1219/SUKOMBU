#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path("app/src/main/java/com/atuy/scomb/ui")
files = [p for p in root.rglob("*.kt") if "/theme/" not in str(p)]
errors = []
counts = {}

forbidden = {
    "CircularProgressIndicator": r"\bCircularProgressIndicator\s*\(",
    "baseline NavigationBar": r"\bNavigationBar\s*\(",
    "baseline NavigationRail": r"\bNavigationRail\s*\(",
    "fixed tween": r"\btween\s*\(",
    "deprecated ListItem headlineContent": r"\bheadlineContent\s*=",
    "SegmentedButton": r"\bSegmentedButton\s*\(",
    "hard-coded RoundedCornerShape": r"\bRoundedCornerShape\s*\(",
}
for p in files:
    s = p.read_text()
    for label, pat in forbidden.items():
        if re.search(pat, s):
            errors.append(f"{p}: {label}")


def calls(text, name):
    pat = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"\s*\(")
    out = []
    for m in pat.finditer(text):
        op = text.find("(", m.start())
        d = 0
        i = op
        q = None
        while i < len(text):
            c = text[i]
            if q:
                if c == "\\":
                    i += 2
                    continue
                if c == q:
                    q = None
            elif c in ('"', "'"):
                q = c
            elif c == "(":
                d += 1
            elif c == ")":
                d -= 1
                if d == 0:
                    out.append(text[op + 1 : i])
                    break
            i += 1
    return out


families = {
    "Button-family": (("Button", "OutlinedButton", "TextButton", "FilledTonalButton", "ElevatedButton"), "shapes ="),
    "IconButton": (("IconButton",), "shapes ="),
    "FilterChip": (("FilterChip",), "shapes ="),
    "OutlinedTextField": (("OutlinedTextField",), "OutlinedTextFieldDefaults.roundedShape"),
    "ListItem": (("ListItem",), "shapes ="),
}
for label, (names, required) in families.items():
    total = good = 0
    for p in files:
        s = p.read_text()
        for name in names:
            for body in calls(s, name):
                total += 1
                if required in body:
                    good += 1
                else:
                    errors.append(f"{p}: {name} missing {required}")
    counts[label] = (good, total)

ptr_total = ptr_good = 0
for p in files:
    s = p.read_text()
    for body in calls(s, "PullToRefreshBox"):
        ptr_total += 1
        if "PullToRefreshDefaults.LoadingIndicator" in body:
            ptr_good += 1
        else:
            errors.append(f"{p}: PullToRefreshBox missing Expressive LoadingIndicator")
counts["PullToRefresh"] = (ptr_good, ptr_total)

app = (root / "ScombApp.kt").read_text()
for required in (
    "ShortNavigationBar(",
    "ShortNavigationBarItem(",
    "WideNavigationRail(",
    "WideNavigationRailItem(",
    "SelectableDropdownMenuItem(",
    "MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()",
    "MaterialTheme.motionScheme.fastEffectsSpec<Float>()",
):
    if required not in app:
        errors.append(f"ScombApp.kt: missing {required}")

if errors:
    print("M3E UI audit failed:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("M3E UI audit passed")
for label, (good, total) in counts.items():
    print(f" - {label}: {good}/{total}")
