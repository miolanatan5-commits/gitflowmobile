package com.natam.gitflowmobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.ScreenTitle

@Composable
fun AccountScreen(vm: AppViewModel) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(stringResource(R.string.account_title))
        Text(if (vm.login.isNotBlank()) stringResource(R.string.account_connected, vm.login) else stringResource(R.string.account_connected_generic))
        if (vm.login.isNotBlank()) {
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/${vm.login}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.account_open_profile))
            }
        }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/settings/tokens") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.account_manage_tokens))
        }
        Text(stringResource(R.string.account_signout_info))
        Button(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_signout))
        }
        Text(stringResource(R.string.account_version))
    }
}
