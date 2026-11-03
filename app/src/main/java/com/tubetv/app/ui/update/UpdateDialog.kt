package com.tubetv.app.ui.update

import com.tubetv.app.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tubetv.app.TitleLanguages
import com.tubetv.app.data.update.GitHubReleases

@Composable
fun UpdateDialog(vm: UpdateViewModel, onLanguageChanged: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Open the installer as soon as the download is done.
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Ready && !s.attempted) vm.install(context)
    }

    val s = state
    if (s is UpdateState.Hidden) return
    val current = Strings.currentVersion(vm.currentBuild)

    when (s) {
        UpdateState.Hidden -> Unit
        UpdateState.About -> UpdateBox(Strings.settings, Strings.about(vm.currentBuild, vm.versionName, GitHubReleases.REPO), onBack = vm::dismiss) {
            Choice(Strings.checkForUpdates, primary = true, onClick = vm::checkNow)
            Choice(Strings.setting(Strings.languageSetting, vm.titleLanguageName), onClick = vm::chooseLanguage)
            Choice(Strings.close, onClick = vm::dismiss)
        }
        UpdateState.Language -> UpdateBox(
            Strings.languageSetting,
            Strings.languageMessage,
            onBack = vm::showAbout,
            vertical = true,
        ) {
            val current = vm.titleLanguage
            TitleLanguages.all.forEach { (tag, name) ->
                Choice(if (tag == current) "✓ $name" else name, primary = tag == current, onClick = {
                    if (vm.setLanguage(tag)) onLanguageChanged()
                })
            }
        }
        UpdateState.Checking -> UpdateBox(Strings.checking, current, onBack = vm::dismiss) {
            Choice(Strings.cancel, primary = true, onClick = vm::dismiss)
        }
        is UpdateState.UpToDate -> UpdateBox(Strings.upToDate, current, onBack = vm::dismiss) {
            Choice(Strings.ok, primary = true, onClick = vm::dismiss)
        }
        is UpdateState.Available -> UpdateBox(Strings.newVersion(s.release.build), current, s.release.notes, onBack = vm::dismiss) {
            Choice(Strings.update, primary = true, onClick = { vm.download(s.release) })
            Choice(Strings.later, onClick = vm::dismiss)
        }
        is UpdateState.Downloading -> UpdateBox(Strings.downloading(s.release.build, (s.progress * 100).toInt()), current, onBack = vm::dismiss) {
            Box(Modifier.width(360.dp).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(s.progress).height(6.dp).background(MaterialTheme.colorScheme.primary))
            }
            Choice(Strings.cancel, primary = true, onClick = vm::dismiss)
        }
        is UpdateState.Ready -> UpdateBox(
            Strings.downloaded(s.release.build),
            if (s.needsPermission) Strings.allowInstall
            else Strings.pressInstall,
            onBack = vm::dismiss,
        ) {
            Choice(Strings.install, primary = true, onClick = { vm.install(context) })
            Choice(Strings.later, onClick = vm::dismiss)
        }
        is UpdateState.Failed -> UpdateBox(Strings.updateFailed, s.message, onBack = vm::dismiss) {
            val release = s.release
            Choice(Strings.retry, primary = true, onClick = { if (release != null) vm.download(release) else vm.checkNow() })
            Choice(Strings.close, onClick = vm::dismiss)
        }
    }
}

private class Choices {
    val primary = FocusRequester()
}

@Composable
private fun UpdateBox(
    title: String,
    message: String,
    notes: String? = null,
    onBack: () -> Unit,
    vertical: Boolean = false,
    buttons: @Composable Choices.() -> Unit,
) {
    val choices = remember { Choices() }
    Dialog(onDismissRequest = onBack) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.width(520.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                if (!notes.isNullOrBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                if (vertical) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { choices.buttons() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { choices.buttons() }
                }
            }
        }
    }
    LaunchedEffect(title) { runCatching { choices.primary.requestFocus() } }
}

@Composable
private fun Choices.Choice(label: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) Button(onClick = onClick, modifier = Modifier.focusRequester(this.primary)) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}
