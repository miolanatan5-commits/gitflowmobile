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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.ErrorText
import com.natam.gitflowmobile.ui.LoadingRow
import com.natam.gitflowmobile.ui.RepoCard
import com.natam.gitflowmobile.ui.ScreenTitle

@Composable
fun UserReposScreen(vm: AppViewModel, login: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.pop() }) { Text(stringResource(R.string.back)) }
                ScreenTitle(stringResource(R.string.user_repos_title, login))
                vm.userReposError?.let { ErrorText(it) }
            }
        }
        items(vm.userRepos) { repo ->
            RepoCard(repo) { vm.openRepo(repo.fullName, repo) }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    vm.userReposLoading -> LoadingRow(stringResource(R.string.loading_repos))
                    !vm.userReposEnd -> OutlinedButton(
                        onClick = { vm.loadUserRepos(login, false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.load_more_repos))
                    }
                    vm.userRepos.isEmpty() -> Text(stringResource(R.string.user_no_repos))
                }
            }
        }
    }
}
