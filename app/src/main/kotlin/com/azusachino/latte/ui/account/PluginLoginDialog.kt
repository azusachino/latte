package com.azusachino.latte.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.AuthFlow
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch

@Composable
fun PluginLoginDialog(
    plugin: SitePlugin,
    onDismissRequest: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val passwordFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var accessToken by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val tokenImport = AuthFlow.TOKEN_IMPORT in plugin.supportedAuthFlows

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        title = { Text(if (tokenImport) "Connect ${plugin.name}" else "Sign in to ${plugin.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tokenImport) {
                    Text(
                        text = "Pixiv passwords are never collected by Latte. Sign in in your browser, then paste an access token here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/login.php")))
                        },
                        enabled = !isLoading,
                    ) {
                        Text("Open Pixiv in browser")
                    }
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it; errorMessage = null },
                        label = { Text("Access token") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = refreshToken,
                        onValueChange = { refreshToken = it; errorMessage = null },
                        label = { Text("Refresh token (optional)") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userId,
                        onValueChange = { userId = it; errorMessage = null },
                        label = { Text("Pixiv user ID (optional)") },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if ((if (tokenImport) accessToken else username).isNotBlank() &&
                        (tokenImport || password.isNotBlank()) && !isLoading
                    ) {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val credentials = if (tokenImport) {
                                buildMap {
                                    put("access_token", accessToken)
                                    if (refreshToken.isNotBlank()) put("refresh_token", refreshToken)
                                    if (userId.isNotBlank()) put("user_id", userId)
                                }
                            } else {
                                mapOf("username" to username, "password" to password)
                            }
                            val result = plugin.login(credentials)
                            isLoading = false
                            if (result.isSuccess) {
                                onLoginSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Login failed"
                            }
                        }
                    }
                },
                enabled = (if (tokenImport) accessToken else username).isNotBlank() &&
                    (tokenImport || password.isNotBlank()) && !isLoading,
            ) {
                Text("Sign In")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isLoading,
            ) {
                Text("Cancel")
            }
        },
    )
}
