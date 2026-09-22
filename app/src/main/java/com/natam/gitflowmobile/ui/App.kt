package com.natam.gitflowmobile.ui

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.natam.gitflowmobile.ui.screens.AccountScreen
import com.natam.gitflowmobile.ui.screens.LoginScreen
import com.natam.gitflowmobile.ui.screens.MyReposScreen
import com.natam.gitflowmobile.ui.screens.RepoDetailScreen
import com.natam.gitflowmobile.ui.screens.SearchScreen
import com.natam.gitflowmobile.ui.screens.UserReposScreen
import kotlinx.coroutines.launch

@Composable
fun GitFlowMobileApp(vm: AppViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg, duration = SnackbarDuration.Long) }
        }
    }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (vm.storeError == null && !vm.initializing && vm.token.isNotEmpty() && vm.stack.isEmpty()) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = vm.tab == t,
                            onClick = { vm.tab = t },
                            icon = {},
                            label = { Text(stringResource(t.labelRes)) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            when {
                vm.storeError != null -> StoreErrorScreen(vm)
                vm.initializing -> LoadingRow(stringResource(R.string.loading_starting))
                vm.token.isEmpty() -> LoginScreen(vm)
                vm.stack.isNotEmpty() -> when (val s = vm.stack.last()) {
                    is Screen.Detail -> RepoDetailScreen(vm, s.fullName)
                    is Screen.User -> UserReposScreen(vm, s.login)
                }
                else -> when (vm.tab) {
                    Tab.MY_REPOS -> MyReposScreen(vm)
                    Tab.SEARCH -> SearchScreen(vm)
                    Tab.ACCOUNT -> AccountScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun StoreErrorScreen(vm: AppViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        ScreenTitle(stringResource(R.string.error_title))
        ErrorText(vm.storeError.orEmpty())
        Button(onClick = { vm.initStore() }, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}
