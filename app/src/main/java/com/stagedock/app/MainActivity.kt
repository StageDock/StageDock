package com.stagedock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stagedock.app.ui.StageDockTheme
import com.stagedock.app.ui.StageScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            StageDockTheme {
                StageScreen(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onLoadMore = { viewModel.loadNext() },
                    onRetry = viewModel::retry,
                    onSortChange = viewModel::setSort,
                    onInstall = viewModel::install,
                    onRemove = viewModel::remove,
                    onToggleInstalled = viewModel::setInstalledOnly,
                    onGrantAccess = ::openAccessSettings,
                    onLaunchGame = ::launchGame,
                    onDismissMessage = viewModel::dismissMessage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccess()
    }

    private fun openAccessSettings() {
        val perApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        val general = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        runCatching { startActivity(perApp) }.onFailure { runCatching { startActivity(general) } }
    }

    private fun launchGame() {
        packageManager.getLaunchIntentForPackage(GAME_PACKAGE)?.let {
            startActivity(it)
            finish()
        }
    }

    companion object {
        const val GAME_PACKAGE = "com.kluge.SynthRiders"
    }
}
