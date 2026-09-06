#!/usr/bin/env python3
# One-shot Material 3 Expressive refactor; removed after successful verification.
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
FEATURE_DIR = ROOT / "app/src/main/java/com/atuy/scomb/ui/features"
APP_FILE = ROOT / "app/src/main/java/com/atuy/scomb/ui/ScombApp.kt"
TARGETS = sorted(FEATURE_DIR.glob("*.kt")) + [APP_FILE]

SHAPES = {
    4: "extraSmall", 6: "extraSmall", 8: "small", 10: "medium",
    12: "large", 14: "large", 16: "largeIncreased", 18: "largeIncreased",
    20: "extraLarge", 24: "extraLarge", 28: "extraLargeIncreased",
    32: "extraExtraLarge",
}

def ensure_material_theme(text: str) -> str:
    if "MaterialTheme." not in text or "import androidx.compose.material3.MaterialTheme" in text:
        return text
    imports = list(re.finditer(r"^import androidx\.compose\.material3\.[^\n]+$", text, re.MULTILINE))
    if imports:
        pos = imports[-1].end()
        return text[:pos] + "\nimport androidx.compose.material3.MaterialTheme" + text[pos:]
    package_end = text.find("\n")
    return text[:package_end + 1] + "\nimport androidx.compose.material3.MaterialTheme" + text[package_end + 1:]

def common(text: str) -> str:
    def shape(match: re.Match[str]) -> str:
        size = int(match.group(1))
        token = SHAPES.get(size)
        return f"MaterialTheme.shapes.{token}" if token else match.group(0)

    text = re.sub(r"RoundedCornerShape\((\d+)\.dp\)", shape, text)
    if "RoundedCornerShape(" not in text:
        text = text.replace("import androidx.compose.foundation.shape.RoundedCornerShape\n", "")
    text = ensure_material_theme(text)
    text = text.replace("CardDefaults.cardElevation(defaultElevation = 2.dp)", "CardDefaults.cardElevation(defaultElevation = 0.dp)")
    text = text.replace("CardDefaults.cardElevation(defaultElevation = 1.dp)", "CardDefaults.cardElevation(defaultElevation = 0.dp)")
    text = text.replace("contentPadding = PaddingValues(16.dp)", "contentPadding = PaddingValues(20.dp)")
    text = text.replace("verticalArrangement = Arrangement.spacedBy(16.dp)", "verticalArrangement = Arrangement.spacedBy(20.dp)")
    text = text.replace(
        "style = MaterialTheme.typography.titleMedium,\n                fontWeight = FontWeight.Bold,",
        "style = MaterialTheme.typography.titleMediumEmphasized,",
    )
    text = text.replace(
        "style = MaterialTheme.typography.titleMedium,\n                fontWeight = FontWeight.Bold",
        "style = MaterialTheme.typography.titleMediumEmphasized",
    )
    return text

def tune(name: str, text: str) -> str:
    if name == "HomeScreen.kt":
        text = text.replace(
            "contentPadding = PaddingValues(20.dp),\n        verticalArrangement = Arrangement.spacedBy(20.dp)",
            "contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),\n        verticalArrangement = Arrangement.spacedBy(20.dp)",
            1,
        )
        text = text.replace(
            "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),\n            shape = MaterialTheme.shapes.largeIncreased",
            "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),\n            shape = MaterialTheme.shapes.extraLarge",
            1,
        )
    elif name == "LoginScreen.kt":
        text = text.replace(
            ".background(MaterialTheme.colorScheme.background)",
            ".background(MaterialTheme.colorScheme.surface)",
        )
        text = text.replace(
            "elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),",
            "elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),",
        )
        text = text.replace(
            "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)\n    ) {",
            "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),\n        shape = MaterialTheme.shapes.extraExtraLarge\n    ) {",
            1,
        )
        text = text.replace(
            "style = MaterialTheme.typography.headlineSmall,\n                    fontWeight = FontWeight.Bold,",
            "style = MaterialTheme.typography.headlineLargeEmphasized,",
            1,
        )
        text = text.replace(".height(50.dp)", ".height(56.dp)", 1)
        text = text.replace("shape = MaterialTheme.shapes.medium", "shape = MaterialTheme.shapes.large")
    elif name == "ErrorState.kt":
        text = text.replace("style = MaterialTheme.typography.titleMedium", "style = MaterialTheme.typography.headlineSmallEmphasized")
        text = text.replace("style = MaterialTheme.typography.bodyMedium", "style = MaterialTheme.typography.bodyLarge")
    elif name in {"NewsScreen.kt", "TaskListScreen.kt", "TimetableScreen.kt", "NewsSearchComponents.kt"}:
        text = text.replace("shape = MaterialTheme.shapes.large,", "shape = MaterialTheme.shapes.largeIncreased,")
    elif name in {"SettingsScreen.kt", "ClassDetailScreen.kt"}:
        text = text.replace("shape = MaterialTheme.shapes.largeIncreased", "shape = MaterialTheme.shapes.extraLarge")
    elif name == "ScombApp.kt":
        text = text.replace(
            "Scaffold(\n        topBar = {",
            "Scaffold(\n        containerColor = MaterialTheme.colorScheme.surface,\n        topBar = {",
            1,
        )
        text = text.replace(
            "NavigationBar {",
            "NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {",
        )
        text = text.replace(
            "NavigationRail(modifier = Modifier.fillMaxHeight()) {",
            "NavigationRail(\n                    modifier = Modifier.fillMaxHeight(),\n                    containerColor = MaterialTheme.colorScheme.surfaceContainer\n                ) {",
        )
    return text

changed = []
for path in TARGETS:
    before = path.read_text(encoding="utf-8")
    after = tune(path.name, common(before))
    if after != before:
        path.write_text(after, encoding="utf-8")
        changed.append(path.relative_to(ROOT).as_posix())

required = {
    "ClassDetailScreen.kt", "ErrorState.kt", "HomeScreen.kt", "LoginScreen.kt",
    "NewsScreen.kt", "NewsSearchComponents.kt", "SettingsScreen.kt",
    "TaskListScreen.kt", "TimetableScreen.kt", "ScombApp.kt",
}
missing = sorted(required - {Path(p).name for p in changed})
if missing:
    raise SystemExit("Expected refactor did not touch: " + ", ".join(missing))

print("Updated Material 3 Expressive UI files:")
for path in changed:
    print(" -", path)
