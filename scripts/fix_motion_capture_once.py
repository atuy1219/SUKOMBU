#!/usr/bin/env python3
from pathlib import Path

p = Path('app/src/main/java/com/atuy/scomb/ui/ScombApp.kt')
s = p.read_text()

boundary = '\n@Composable\nprivate fun AdaptiveRouteContainer('
if boundary not in s:
    raise RuntimeError('ScombApp boundary not found')
head, tail = s.split(boundary, 1)

anchor = '    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600\n'
insert = '''    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val navigationEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val navigationSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
'''
if anchor not in head:
    raise RuntimeError('isTablet anchor not found')
head = head.replace(anchor, insert, 1)
head = head.replace('MaterialTheme.motionScheme.fastEffectsSpec()', 'navigationEffectsSpec')
head = head.replace('MaterialTheme.motionScheme.fastSpatialSpec()', 'navigationSpatialSpec')

app_topbar = '''fun AppTopBar(
    currentRoute: String?,
    timetableViewModel: TimetableViewModel,
    newsViewModel: NewsViewModel,
    taskListViewModel: TaskListViewModel
) {
    AnimatedContent('''
app_topbar_new = '''fun AppTopBar(
    currentRoute: String?,
    timetableViewModel: TimetableViewModel,
    newsViewModel: NewsViewModel,
    taskListViewModel: TaskListViewModel
) {
    val topBarEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent('''
if app_topbar not in tail:
    raise RuntimeError('AppTopBar anchor not found')
tail = tail.replace(app_topbar, app_topbar_new, 1)
tail = tail.replace('MaterialTheme.motionScheme.fastEffectsSpec()', 'topBarEffectsSpec')

p.write_text(head + boundary + tail)
