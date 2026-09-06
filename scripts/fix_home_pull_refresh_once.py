#!/usr/bin/env python3
from pathlib import Path

p = Path('app/src/main/java/com/atuy/scomb/ui/features/HomeScreen.kt')
s = p.read_text()

marker = '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)'
if marker not in s:
    s = marker + '\n\n' + s

for imp in (
    'import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults',
    'import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState',
):
    if imp not in s:
        anchor = 'import androidx.compose.material3.pulltorefresh.PullToRefreshBox\n'
        if anchor not in s:
            raise RuntimeError('PullToRefreshBox import not found')
        s = s.replace(anchor, anchor + imp + '\n', 1)

old = '''            is HomeUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.loadHomeData(forceRefresh = true)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {'''
new = '''            is HomeUiState.Success -> {
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.loadHomeData(forceRefresh = true)
                    },
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {'''
if old not in s:
    raise RuntimeError('Home PullToRefreshBox block not found')
p.write_text(s.replace(old, new, 1))
