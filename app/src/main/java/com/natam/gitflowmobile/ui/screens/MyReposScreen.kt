package com.natam.gitflowmobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.ErrorText
import com.natam.gitflowmobile.ui.LoadingRow
import com.natam.gitflowmobile.ui.RepoCard
import com.natam.gitflowmobile.ui.ScreenTitle
import com.natam.gitflowmobile.ui.ToggleRow

@Composable
fun MyReposScreen(vm: AppViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    val filter = vm.myFilter
    val filtered = vm.myRepos.filter {
        filter.isBlank() ||
            it.name.contains(filter, ignoreCase = true) ||
            it.description.contains(filter, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTitle(stringResource(R.string.my_repos_title))
                if (vm.login.isNotBlank()) Text(stringResource(R.string.connected_as, vm.login))
                Button(
                    onClick = {
                        vm.createError = null
                        showCreate = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.create_repo))
                }
                OutlinedButton(
                    onClick = { vm.loadMyRepos(true) },
                    enabled = !vm.myLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.refresh_list))
                }
                OutlinedTextField(
                    value = vm.myFilter,
                    onValueChange = { vm.myFilter = it },
                    label = { Text(stringResource(R.string.filter_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                vm.myError?.let { ErrorText(it) }
            }
        }
        items(filtered) { repo ->
            RepoCard(repo) { vm.openRepo(repo.fullName, repo) }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    vm.myLoading -> LoadingRow(stringResource(R.string.loading_repos))
                    !vm.myEnd -> OutlinedButton(
                        onClick = { vm.loadMyRepos(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.load_more_repos))
                    }
                    filtered.isEmpty() -> Text(stringResource(R.string.no_repos))
                }
            }
        }
    }

    if (showCreate) {
        CreateRepoDialog(vm = vm, onDismiss = { showCreate = false })
    }
}

@Composable
private fun CreateRepoDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var autoInit by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!vm.createBusy) onDismiss() },
        title = { Text(stringResource(R.string.create_repo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.repo_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.repo_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                ToggleRow(stringResource(R.string.private_repo), isPrivate) { isPrivate = it }
                ToggleRow(stringResource(R.string.create_with_readme), autoInit) { autoInit = it }
                vm.createError?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.createRepo(name, description, isPrivate, autoInit) { onDismiss() } },
                enabled = !vm.createBusy
            ) {
                Text(stringResource(if (vm.createBusy) R.string.creating else R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !vm.createBusy) { Text(stringResource(R.string.cancel)) }
        }
    )
}
