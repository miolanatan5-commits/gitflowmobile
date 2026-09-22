package com.natam.gitflowmobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.CodeCard
import com.natam.gitflowmobile.ui.ErrorText
import com.natam.gitflowmobile.ui.LoadingRow
import com.natam.gitflowmobile.ui.RepoCard
import com.natam.gitflowmobile.ui.ScreenTitle
import com.natam.gitflowmobile.ui.SearchSort
import com.natam.gitflowmobile.ui.SearchType
import com.natam.gitflowmobile.ui.StatusText
import com.natam.gitflowmobile.ui.UserCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val focus = LocalFocusManager.current
    val shown = vm.repoResults.size + vm.userResults.size + vm.codeResults.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTitle(stringResource(R.string.search_title))
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    label = { Text(stringResource(R.string.search_term)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        vm.search(true)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.search_type), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchType.entries.forEach { t ->
                        FilterChip(
                            selected = vm.searchType == t,
                            onClick = { vm.searchType = t },
                            label = { Text(stringResource(t.labelRes)) }
                        )
                    }
                }
                if (vm.searchType == SearchType.REPOS) {
                    Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchSort.entries.forEach { s ->
                            FilterChip(
                                selected = vm.searchSort == s,
                                onClick = { vm.searchSort = s },
                                label = { Text(stringResource(s.labelRes)) }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        focus.clearFocus()
                        vm.search(true)
                    },
                    enabled = !vm.searchLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.search_button))
                }
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                vm.searchError?.let { ErrorText(it) }
                if (vm.searchTotal >= 0 && !vm.searchLoading) {
                    StatusText(stringResource(R.string.search_status, vm.searchTotal, shown))
                }
                if (vm.searchLoading && shown == 0) LoadingRow(stringResource(R.string.loading_searching))
            }
        }
        when (vm.resultType) {
            SearchType.REPOS -> items(vm.repoResults) { repo ->
                RepoCard(repo) { vm.openRepo(repo.fullName, repo) }
            }
            SearchType.USERS -> items(vm.userResults) { user ->
                UserCard(user) { vm.openUser(user.login) }
            }
            SearchType.CODE -> items(vm.codeResults) { hit ->
                CodeCard(hit) { vm.openRepo(hit.repoFullName) }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (vm.searchLoading && shown > 0) {
                    LoadingRow(stringResource(R.string.loading_more_results))
                } else if (shown > 0 && !vm.searchEnd) {
                    OutlinedButton(
                        onClick = { vm.search(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.load_more_results))
                    }
                }
            }
        }
    }
}
