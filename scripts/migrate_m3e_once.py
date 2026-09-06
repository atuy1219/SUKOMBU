#!/usr/bin/env python3
from pathlib import Path
import re

UI = Path("app/src/main/java/com/atuy/scomb/ui")


def ensure_import(text: str, imp: str) -> str:
    line = f"import {imp}"
    if line in text:
        return text
    imports = list(re.finditer(r"^import .+$", text, re.M))
    if not imports:
        raise RuntimeError(f"No import block while adding {imp}")
    pos = imports[-1].end()
    return text[:pos] + "\n" + line + text[pos:]


def ensure_file_opt_in(text: str) -> str:
    marker = "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)"
    if marker not in text:
        text = marker + "\n\n" + text
    return text


def matching_paren(text: str, open_pos: int) -> int:
    depth = 0
    i = open_pos
    quote = None
    triple = False
    while i < len(text):
        if quote:
            if triple:
                if text.startswith(quote * 3, i):
                    i += 3
                    quote = None
                    triple = False
                    continue
                i += 1
                continue
            if text[i] == "\\":
                i += 2
                continue
            if text[i] == quote:
                quote = None
            i += 1
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = len(text) if j < 0 else j + 1
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            if j < 0:
                raise RuntimeError("unterminated comment")
            i = j + 2
            continue
        if text.startswith('"""', i):
            quote = '"'
            triple = True
            i += 3
            continue
        if text[i] in ('"', "'"):
            quote = text[i]
            i += 1
            continue
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise RuntimeError("unmatched parenthesis")


def top_arg_range(inside: str, name: str):
    par = bra = brk = 0
    i = 0
    quote = None
    while i < len(inside):
        if quote:
            if inside[i] == "\\":
                i += 2
                continue
            if inside[i] == quote:
                quote = None
            i += 1
            continue
        if inside.startswith("//", i):
            j = inside.find("\n", i)
            i = len(inside) if j < 0 else j + 1
            continue
        if inside.startswith("/*", i):
            j = inside.find("*/", i + 2)
            i = len(inside) if j < 0 else j + 2
            continue
        c = inside[i]
        if c in ('"', "'"):
            quote = c
            i += 1
            continue
        if c == "(":
            par += 1
        elif c == ")":
            par -= 1
        elif c == "{":
            bra += 1
        elif c == "}":
            bra -= 1
        elif c == "[":
            brk += 1
        elif c == "]":
            brk -= 1
        elif par == bra == brk == 0 and (c.isalpha() or c == "_"):
            j = i + 1
            while j < len(inside) and (inside[j].isalnum() or inside[j] == "_"):
                j += 1
            if inside[i:j] == name:
                k = j
                while k < len(inside) and inside[k].isspace():
                    k += 1
                if k < len(inside) and inside[k] == "=":
                    start = i
                    while start > 0 and inside[start - 1] in " \t":
                        start -= 1
                    p2 = b2 = q2 = 0
                    t = k + 1
                    q = None
                    while t < len(inside):
                        ch = inside[t]
                        if q:
                            if ch == "\\":
                                t += 2
                                continue
                            if ch == q:
                                q = None
                        elif ch in ('"', "'"):
                            q = ch
                        elif ch == "(":
                            p2 += 1
                        elif ch == ")":
                            p2 -= 1
                        elif ch == "{":
                            b2 += 1
                        elif ch == "}":
                            b2 -= 1
                        elif ch == "[":
                            q2 += 1
                        elif ch == "]":
                            q2 -= 1
                        elif ch == "," and p2 == b2 == q2 == 0:
                            end = t + 1
                            if end < len(inside) and inside[end] == "\n":
                                end += 1
                            return start, end
                        t += 1
                    return start, len(inside)
            i = j
            continue
        i += 1
    return None


def transform_calls(text: str, name: str, arg_name=None, arg_value=None, remove_arg=None) -> str:
    pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"\s*\(")
    spans = []
    for m in pattern.finditer(text):
        op = text.find("(", m.start())
        try:
            cl = matching_paren(text, op)
        except RuntimeError:
            continue
        spans.append((op, cl))
    for op, cl in reversed(spans):
        inside = text[op + 1 : cl]
        if remove_arg:
            rr = top_arg_range(inside, remove_arg)
            if rr:
                inside = inside[: rr[0]] + inside[rr[1] :]
        if arg_name is not None and not top_arg_range(inside, arg_name):
            if inside.startswith("\n"):
                line_start = text.rfind("\n", 0, op) + 1
                base_indent = re.match(r"\s*", text[line_start:op]).group(0)
                insertion = "\n" + base_indent + "    " + f"{arg_name} = {arg_value},"
                inside = insertion + inside[1:]
            elif inside.strip():
                inside = f"{arg_name} = {arg_value}, " + inside
            else:
                inside = f"{arg_name} = {arg_value}"
        text = text[: op + 1] + inside + text[cl:]
    return text


# Use alpha27 interaction-aware expressive overloads everywhere they exist.
for path in UI.rglob("*.kt"):
    if "/theme/" in str(path):
        continue
    text = path.read_text()
    original = text
    button_names = ("Button", "OutlinedButton", "TextButton", "FilledTonalButton", "ElevatedButton")
    if any(re.search(r"(?<![A-Za-z0-9_])" + n + r"\s*\(", text) for n in button_names):
        text = ensure_import(text, "androidx.compose.material3.ButtonDefaults")
        for n in button_names:
            text = transform_calls(text, n, "shapes", "ButtonDefaults.shapes()", remove_arg="shape")
    if re.search(r"(?<![A-Za-z0-9_])IconButton\s*\(", text):
        text = ensure_import(text, "androidx.compose.material3.IconButtonDefaults")
        text = transform_calls(text, "IconButton", "shapes", "IconButtonDefaults.shapes()", remove_arg="shape")
    if re.search(r"(?<![A-Za-z0-9_])FilterChip\s*\(", text):
        text = ensure_import(text, "androidx.compose.material3.FilterChipDefaults")
        text = transform_calls(text, "FilterChip", "shapes", "FilterChipDefaults.shapes()", remove_arg="shape")
    if re.search(r"(?<![A-Za-z0-9_])OutlinedTextField\s*\(", text):
        text = ensure_import(text, "androidx.compose.material3.OutlinedTextFieldDefaults")
        text = transform_calls(text, "OutlinedTextField", "shape", "OutlinedTextFieldDefaults.roundedShape", remove_arg="shape")
    if "CircularProgressIndicator" in text:
        text = text.replace("import androidx.compose.material3.CircularProgressIndicator\n", "import androidx.compose.material3.LoadingIndicator\n")
        text = re.sub(r"(?<![A-Za-z0-9_])CircularProgressIndicator\s*\(", "LoadingIndicator(", text)
        text = transform_calls(text, "LoadingIndicator", remove_arg="strokeWidth")
        text = ensure_file_opt_in(text)
    if text != original:
        path.write_text(text)

# Home: deprecated clickable ListItem overloads -> interaction-aware ListItem with shape morphing.
home = UI / "features/HomeScreen.kt"
text = home.read_text()
old = '''                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = backgroundColor),
                        headlineContent = {
                            Text(
                                text = classCell.name ?: "",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                text = classCell.room ?: stringResource(R.string.home_room_unset),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${classCell.period + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .sharedElement(
                                // キーに曜日と時限を含めて一致させる
                                sharedContentState = rememberSharedContentState(key = "class-${classCell.classId}-${classCell.dayOfWeek}-${classCell.period}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clickable {
                                if (classCell.classId.isNotEmpty()) {
                                    onClassClick(classCell.classId, classCell.dayOfWeek, classCell.period)
                                }
                            }
                    )'''
new = '''                    ListItem(
                        onClick = {
                            if (classCell.classId.isNotEmpty()) {
                                onClassClick(classCell.classId, classCell.dayOfWeek, classCell.period)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = backgroundColor),
                        shapes = ListItemDefaults.shapes(),
                        supportingContent = {
                            Text(
                                text = classCell.room ?: stringResource(R.string.home_room_unset),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${classCell.period + 1}",
                                        style = MaterialTheme.typography.titleMediumEmphasized
                                    )
                                }
                            }
                        },
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "class-${classCell.classId}-${classCell.dayOfWeek}-${classCell.period}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    ) {
                        Text(
                            text = classCell.name ?: "",
                            style = if (isCurrent) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }'''
if old not in text:
    raise RuntimeError("Home current-class ListItem block not found")
text = text.replace(old, new)
old = '''                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = task.title,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = task.className,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = DateUtils.timeToString(task.deadline),
                                style = MaterialTheme.typography.labelMedium,
                                color = timeColor
                            )
                            Text(
                                text = DateUtils.formatRemainingTime(task.deadline),
                                style = MaterialTheme.typography.labelSmall,
                                color = timeColor
                            )
                        }
                    },
                    modifier = Modifier.clickable { onTaskClick(task) }
                )'''
new = '''                ListItem(
                    onClick = { onTaskClick(task) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    shapes = ListItemDefaults.shapes(),
                    supportingContent = {
                        Text(
                            text = task.className,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = DateUtils.timeToString(task.deadline),
                                style = MaterialTheme.typography.labelMedium,
                                color = timeColor
                            )
                            Text(
                                text = DateUtils.formatRemainingTime(task.deadline),
                                style = MaterialTheme.typography.labelSmall,
                                color = timeColor
                            )
                        }
                    }
                ) {
                    Text(
                        text = task.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )
                }'''
if old not in text:
    raise RuntimeError("Home task ListItem block not found")
text = text.replace(old, new)
old = '''                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = newsItem.title,
                            maxLines = 2,
                            fontWeight = if (newsItem.unread) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (newsItem.unread) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                            Text(
                                text = newsItem.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = newsItem.publishTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.clickable { onNewsClick(newsItem.url) }
                )'''
new = '''                ListItem(
                    onClick = { onNewsClick(newsItem.url) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    shapes = ListItemDefaults.shapes(),
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (newsItem.unread) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                            Text(
                                text = newsItem.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = newsItem.publishTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    Text(
                        text = newsItem.title,
                        maxLines = 2,
                        style = if (newsItem.unread) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge
                    )
                }'''
if old not in text:
    raise RuntimeError("Home news ListItem block not found")
home.write_text(text.replace(old, new))

# Pull-to-refresh: dedicated M3E loading indicator.
for rel in ("features/NewsScreen.kt", "features/TaskListScreen.kt", "features/TimetableScreen.kt"):
    path = UI / rel
    text = path.read_text()
    text = ensure_import(text, "androidx.compose.material3.pulltorefresh.PullToRefreshDefaults")
    text = ensure_import(text, "androidx.compose.material3.pulltorefresh.rememberPullToRefreshState")
    text = ensure_file_opt_in(text)
    needle = "                PullToRefreshBox(\n"
    if needle not in text:
        raise RuntimeError(f"PullToRefreshBox not found in {path}")
    insert = '''                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
'''
    path.write_text(text.replace(needle, insert, 1))

# App-level expressive navigation, selectable menu and MotionScheme.
app = UI / "ScombApp.kt"
text = app.read_text()
for old_import in (
    "import androidx.compose.animation.core.tween\n",
    "import androidx.compose.material3.DropdownMenuItem\n",
    "import androidx.compose.material3.NavigationBar\n",
    "import androidx.compose.material3.NavigationBarItem\n",
    "import androidx.compose.material3.NavigationRail\n",
    "import androidx.compose.material3.NavigationRailItem\n",
):
    text = text.replace(old_import, "")
for imp in (
    "androidx.compose.material.icons.filled.Check",
    "androidx.compose.material3.MenuDefaults",
    "androidx.compose.material3.SelectableDropdownMenuItem",
    "androidx.compose.material3.ShortNavigationBar",
    "androidx.compose.material3.ShortNavigationBarArrangement",
    "androidx.compose.material3.ShortNavigationBarItem",
    "androidx.compose.material3.WideNavigationRail",
    "androidx.compose.material3.WideNavigationRailItem",
):
    text = ensure_import(text, imp)
old = '''                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    bottomBarScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.resourceId)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }'''
new = '''                ShortNavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    arrangement = ShortNavigationBarArrangement.EqualWeight
                ) {
                    bottomBarScreens.forEach { screen ->
                        ShortNavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.resourceId)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }'''
if old not in text:
    raise RuntimeError("phone navigation block not found")
text = text.replace(old, new)
old = '''                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        bottomBarScreens.forEach { screen ->
                            NavigationRailItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(stringResource(screen.resourceId)) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }'''
new = '''                WideNavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    arrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    bottomBarScreens.forEach { screen ->
                        WideNavigationRailItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.resourceId)) },
                            railExpanded = false,
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }'''
if old not in text:
    raise RuntimeError("tablet navigation block not found")
text = text.replace(old, new)
text = text.replace("fadeIn(animationSpec = tween(75))", "fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())")
text = text.replace("fadeOut(animationSpec = tween(75))", "fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())")
text = text.replace("animationSpec = tween(75)", "animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()")
text = text.replace(
    "fadeIn() togetherWith fadeOut()",
    "fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) togetherWith fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())",
)
old = '''                    val startYear = Calendar.getInstance().get(Calendar.YEAR)
                    for (i in 0..5) {
                        val displayYear = startYear - i
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "2").getDisplayName()) },
                            onClick = {
                                viewModel.changeYearAndTerm(displayYear, "2")
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "1").getDisplayName()) },
                            onClick = {
                                viewModel.changeYearAndTerm(displayYear, "1")
                                menuExpanded = false
                            }
                        )
                    }'''
new = '''                    val startYear = Calendar.getInstance().get(Calendar.YEAR)
                    val terms = remember(startYear) {
                        buildList {
                            for (i in 0..5) {
                                val displayYear = startYear - i
                                add(TimetableTerm(displayYear, "2"))
                                add(TimetableTerm(displayYear, "1"))
                            }
                        }
                    }
                    terms.forEachIndexed { index, term ->
                        SelectableDropdownMenuItem(
                            selected = term == current,
                            text = { Text(term.getDisplayName()) },
                            shapes = MenuDefaults.itemShape(index, terms.size),
                            selectedLeadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = {
                                viewModel.changeYearAndTerm(term.year, term.term)
                                menuExpanded = false
                            }
                        )
                    }'''
if old not in text:
    raise RuntimeError("timetable menu block not found")
app.write_text(text.replace(old, new))

# Search/filter surface transitions use the theme MotionScheme instead of generic defaults.
for rel in ("features/NewsScreen.kt", "features/TaskListScreen.kt"):
    path = UI / rel
    text = path.read_text()
    text = text.replace(
        "enter = expandVertically() + fadeIn(),\n                    exit = shrinkVertically() + fadeOut()",
        "enter = expandVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +\n"
        "                        fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),\n"
        "                    exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +\n"
        "                        fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())",
    )
    path.write_text(text)

# Permanent regression audit.
audit = Path("scripts/check_m3e_ui.py")
audit.write_text(r'''#!/usr/bin/env python3
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
    "MaterialTheme.motionScheme.fastSpatialSpec()",
    "MaterialTheme.motionScheme.fastEffectsSpec()",
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
''')

# Make the static audit a normal PR CI gate.
ci = Path(".github/workflows/android-ci.yml")
c = ci.read_text()
needle = "      - name: Build with Gradle\n        run: ./gradlew assembleDebug testDebugUnitTest -x lint\n"
if "Verify Material 3 Expressive UI" not in c:
    if needle not in c:
        raise RuntimeError("CI build step not found")
    c = c.replace(
        needle,
        "      - name: Verify Material 3 Expressive UI\n"
        "        run: python3 scripts/check_m3e_ui.py\n\n"
        + needle,
    )
ci.write_text(c)
