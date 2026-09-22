package com.natam.gitflowmobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.data.Repo
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.ErrorText
import com.natam.gitflowmobile.ui.LoadingRow
import com.natam.gitflowmobile.ui.ScreenTitle
import com.natam.gitflowmobile.ui.SectionTitle
import com.natam.gitflowmobile.ui.StatusText
import com.natam.gitflowmobile.ui.ToggleRow
import com.natam.gitflowmobile.ui.formatSize
import com.natam.gitflowmobile.ui.installApk

@Composable
fun RepoDetailScreen(vm: AppViewModel, fullName: String) {
    val repo = vm.detail?.takeIf { it.fullName.equals(fullName, ignoreCase = true) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val defaultCommitMessage = stringResource(R.string.default_commit_message)
    var showUpload by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var upBranch by remember { mutableStateOf("") }
    var upMessage by remember { mutableStateOf(defaultCommitMessage) }
    var upStrip by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && repo != null) {
            vm.startUpload(repo, uri, upBranch, upMessage, upStrip)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = { vm.pop() }) { Text(stringResource(R.string.back)) }
        ScreenTitle(fullName)

        if (vm.detailLoading && repo == null) LoadingRow(stringResource(R.string.loading_repo))
        vm.detailError?.let { ErrorText(it) }

        if (repo != null) {
            val mine = vm.actionRepo == repo.fullName

            if (repo.description.isNotBlank()) Text(repo.description)
            val visibilityText = stringResource(if (repo.isPrivate) R.string.visibility_private else R.string.visibility_public)
            val starsForksText = stringResource(R.string.detail_stars_forks, repo.stars, repo.forks)
            val defaultBranchText = stringResource(R.string.detail_default_branch, repo.defaultBranch)
            val archivedText = stringResource(R.string.tag_archived)
            Text(
                buildString {
                    append(visibilityText)
                    if (repo.language.isNotBlank()) append(" · ${repo.language}")
                    append(" · ").append(starsForksText)
                    append(" · ").append(defaultBranchText)
                    if (repo.archived) append(" · ").append(archivedText)
                }
            )
            OutlinedButton(
                onClick = { uriHandler.openUri(repo.htmlUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_in_browser))
            }

            // ----- APKs from published releases -----
            SectionTitle(stringResource(R.string.apks_title))
            val apks = vm.releases.flatMap { rel ->
                rel.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }.map { rel to it }
            }
            when {
                vm.detailLoading -> LoadingRow(stringResource(R.string.loading_releases))
                apks.isEmpty() -> Text(stringResource(R.string.no_apks))
                else -> apks.forEach { (rel, asset) ->
                    Button(
                        onClick = { vm.downloadAsset(repo, asset) },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.download_apk, asset.name, formatSize(asset.size), rel.tag.ifBlank { rel.name }))
                    }
                }
            }
            if (mine) StatusText(vm.downloadStatus)

            // ----- Upload code and build -----
            if (repo.canPush) {
                SectionTitle(stringResource(R.string.send_build_title))
                Text(stringResource(R.string.send_build_intro))
                Button(
                    onClick = { showUpload = true },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.send_zip))
                }
                if (mine) StatusText(vm.uploadStatus)

                OutlinedTextField(
                    value = vm.buildBranch,
                    onValueChange = { vm.buildBranch = it },
                    label = { Text(stringResource(R.string.build_branch_label, repo.defaultBranch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.startBuild(repo, false) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.build_apk))
                }
                if (mine) StatusText(vm.buildStatus)

                val outcome = vm.buildOutcome
                if (mine && outcome != null) {
                    OutlinedButton(
                        onClick = { vm.downloadLog(repo, outcome.runId) },
                        enabled = !vm.logBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (vm.logBusy) R.string.downloading_log else R.string.download_log))
                    }
                    if (outcome.htmlUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri(outcome.htmlUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_run))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.startBuild(repo, true) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.recreate_workflow))
                }
            }

            // ----- Downloaded / generated files -----
            if (mine && vm.savedFiles.isNotEmpty()) {
                SectionTitle(stringResource(R.string.saved_files_title))
                vm.savedFiles.forEach { f ->
                    if (f.name.endsWith(".apk", ignoreCase = true)) {
                        Button(
                            onClick = { installApk(context, f.uri) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.install_file, f.name))
                        }
                    } else {
                        Text(f.name)
                    }
                }
            }

            // ----- Danger zone -----
            if (repo.canAdmin) {
                SectionTitle(stringResource(R.string.danger_zone))
                Button(
                    onClick = { showDelete = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_repo))
                }
            }
        }
    }

    if (showUpload && repo != null) {
        AlertDialog(
            onDismissRequest = { showUpload = false },
            title = { Text(stringResource(R.string.upload_dialog_title, repo.name)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.upload_dialog_intro))
                    OutlinedTextField(
                        value = upBranch,
                        onValueChange = { upBranch = it },
                        label = { Text(stringResource(R.string.upload_branch_label, repo.defaultBranch)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upMessage,
                        onValueChange = { upMessage = it },
                        label = { Text(stringResource(R.string.commit_message_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ToggleRow(stringResource(R.string.strip_root), upStrip) { upStrip = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showUpload = false
                    picker.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/x-zip",
                            "application/octet-stream"
                        )
                    )
                }) {
                    Text(stringResource(R.string.pick_and_upload))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpload = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDelete && repo != null) {
        DeleteRepoDialog(
            vm = vm,
            repo = repo,
            onDismiss = { showDelete = false },
            onDeleted = {
                showDelete = false
                vm.pop()
            }
        )
    }
}

@Composable
private fun DeleteRepoDialog(vm: AppViewModel, repo: Repo, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.deleteBusy) onDismiss() },
        title = { Text(stringResource(R.string.delete_title, repo.fullName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.delete_body, repo.name))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.delete_type_label, repo.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.deleteRepo(repo, onDeleted) },
                enabled = confirm == repo.name && !vm.deleteBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(if (vm.deleteBusy) R.string.deleting else R.string.delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !vm.deleteBusy) { Text(stringResource(R.string.cancel)) }
        }
    )
}
