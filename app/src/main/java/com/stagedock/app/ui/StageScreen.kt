package com.stagedock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stagedock.app.UiState
import com.stagedock.app.data.Stage
import com.stagedock.app.data.StageSort

@Composable
fun StageScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSortChange: (StageSort) -> Unit,
    onInstall: (Stage) -> Unit,
    onRemove: (String) -> Unit,
    onToggleInstalled: (Boolean) -> Unit,
    onGrantAccess: () -> Unit,
    onLaunchGame: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
    ) {
        Header(state, onQueryChange, onToggleInstalled, onLaunchGame)
        Spacer(Modifier.height(12.dp))

        if (!state.hasAccess) AccessBanner(onGrantAccess)
        state.message?.let { MessageBar(it, onDismissMessage) }

        if (state.showInstalledOnly) {
            InstalledList(state.installedFiles.sorted(), state.hasAccess, onRemove)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageSort.entries.forEach { sort ->
                    FilterChip(selected = state.sort == sort, onClick = { onSortChange(sort) }, label = { Text(sort.label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            BrowseGrid(state, onLoadMore, onRetry, onInstall, onRemove)
        }
    }
}

@Composable
private fun ColumnScope.BrowseGrid(
    state: UiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (Stage) -> Unit,
    onRemove: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    gridState.OnNearEnd(onLoadMore)

    if (state.stages.isEmpty() && !state.loading) {
        CenteredStatus(Modifier.weight(1f)) {
            if (state.loadError != null) {
                Text(state.loadError, color = MaterialTheme.colorScheme.onBackground)
                Button(onClick = onRetry) { Text("Retry") }
            } else {
                Text("No stages found.", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        items(state.stages, key = { it.id }) { stage ->
            StageCard(
                stage = stage,
                installed = state.isInstalled(stage),
                progress = state.progress[stage.id],
                enabled = state.hasAccess,
                onInstall = { onInstall(stage) },
                onRemove = { state.filesFor(stage).forEach(onRemove) },
            )
        }
        if (state.loading || state.loadError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loading) {
                        CircularProgressIndicator(color = Cyan)
                    } else {
                        TextButton(onClick = onRetry) { Text("Couldn't load more. Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredStatus(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun LazyGridState.OnNearEnd(onLoadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && last >= layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onLoadMore() }
}

@Composable
private fun Header(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onToggleInstalled: (Boolean) -> Unit,
    onLaunchGame: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("StageDock", style = MaterialTheme.typography.headlineMedium, color = Magenta)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            enabled = !state.showInstalledOnly,
            placeholder = { Text("Search stages") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(selected = !state.showInstalledOnly, onClick = { onToggleInstalled(false) }, label = { Text("Browse") })
        FilterChip(selected = state.showInstalledOnly, onClick = { onToggleInstalled(true) }, label = { Text("Installed (${state.installedFiles.size})") })
        Button(onClick = onLaunchGame) { Text("Play") }
    }
}

@Composable
private fun AccessBanner(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("All files access is needed to write into SynthRidersUC.", style = MaterialTheme.typography.titleMedium)
            Text(
                "If the settings screen does not open on your headset, run: adb shell appops set --uid com.stagedock.app MANAGE_EXTERNAL_STORAGE allow",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onGrant) { Text("Grant access") }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = Cyan)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StageCard(
    stage: Stage,
    installed: Boolean,
    progress: Float?,
    enabled: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column {
            AsyncImage(
                model = stage.thumbnailUrl,
                contentDescription = stage.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Background),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stage.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stage.author ?: " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    stage.downloadCount?.let {
                        Text("$it downloads", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                when {
                    progress != null -> LinearProgressIndicator(
                        progress = { progress },
                        color = Magenta,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    )
                    installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Installed") }
                        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                    }
                    !stage.questAvailable -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("PC only") }
                    else -> Button(onClick = onInstall, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Install") }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.InstalledList(files: List<String>, hasAccess: Boolean, onRemove: (String) -> Unit) {
    if (!hasAccess) return
    if (files.isEmpty()) {
        CenteredStatus(Modifier.weight(1f)) {
            Text("No custom stages installed yet.", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        items(files, key = { it }) { file ->
            Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(file, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(file) }) { Icon(Icons.Default.Delete, contentDescription = "Remove $file") }
                }
            }
        }
    }
}
