package com.natam.gitflowmobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.ui.AppViewModel
import com.natam.gitflowmobile.ui.ErrorText
import com.natam.gitflowmobile.ui.ScreenTitle

@Composable
fun LoginScreen(vm: AppViewModel) {
    var tokenText by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(stringResource(R.string.app_name))
        Text(stringResource(R.string.login_intro))
        OutlinedButton(
            onClick = {
                uriHandler.openUri(
                    "https://github.com/settings/tokens/new?scopes=repo,workflow,delete_repo&description=GitFlow%20Mobile"
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login_create_token))
        }
        OutlinedTextField(
            value = tokenText,
            onValueChange = { tokenText = it },
            label = { Text(stringResource(R.string.login_token_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.signIn(tokenText) }),
            modifier = Modifier.fillMaxWidth()
        )
        vm.loginError?.let { ErrorText(it) }
        Button(
            onClick = { vm.signIn(tokenText) },
            enabled = !vm.loginBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (vm.loginBusy) R.string.login_signing_in else R.string.login_sign_in))
        }
    }
}
