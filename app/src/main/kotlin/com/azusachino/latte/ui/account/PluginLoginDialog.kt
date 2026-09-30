package com.azusachino.latte.ui.account

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.azusachino.latte.ui.common.LatteIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.azusachino.latte.MoebooruLoginActivity
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.AuthFlow
import android.content.Intent
import android.net.Uri
import com.azusachino.latte.PixivLoginActivity
import com.azusachino.latte.data.network.OkHttpProvider
import com.azusachino.latte.data.network.PixivOAuthCallbackBus
import com.azusachino.latte.data.network.PixivOAuthClient
import com.azusachino.latte.plugin.moebooru.MoebooruAuthCallbackBus
import com.azusachino.latte.plugin.moebooru.MoebooruPlugin
import com.azusachino.latte.plugin.pixiv.PixivPlugin
import com.azusachino.latte.ui.common.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

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
    var isCookieImportMode by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val tokenImport = AuthFlow.TOKEN_IMPORT in plugin.supportedAuthFlows
    val pixivPlugin = plugin as? PixivPlugin
    val moebooruPlugin = plugin as? MoebooruPlugin

    LaunchedEffect(moebooruPlugin) {
        if (moebooruPlugin == null) return@LaunchedEffect
        MoebooruAuthCallbackBus.events.collect { auth ->
            if (auth.externalId == moebooruPlugin.platform.externalId) {
                moebooruPlugin.loginWithSession(auth.username, auth.passHash, auth.userId)
                ToastManager.showSuccess("Signed in as ${auth.username}")
                onLoginSuccess()
            }
        }
    }

    LaunchedEffect(pixivPlugin) {
        if (pixivPlugin == null) return@LaunchedEffect
        PixivOAuthCallbackBus.callbacks.collect { callbackUri ->
            val uri = Uri.parse(callbackUri)
            if (!PixivOAuthClient.isCallbackUri(uri)) return@collect
            val error = uri.getQueryParameter("error")
            val code = uri.getQueryParameter("code")
            if (error != null) {
                val description = uri.getQueryParameter("error_description")
                errorMessage = listOfNotNull(error, description)
                    .joinToString(": ")
                    .let { "Pixiv browser sign-in failed: $it" }
                return@collect
            }
            if (code.isNullOrBlank()) return@collect

            isLoading = true
            errorMessage = null
            val result = pixivPlugin.completeBrowserLogin(code)
            isLoading = false
            if (result.isSuccess) {
                onLoginSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Pixiv browser sign-in failed"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        title = { Text(if (tokenImport) "Connect ${plugin.name}" else "Sign in to ${plugin.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tokenImport) {
                    Text(
                        text = "Pixiv passwords are never collected by Latte. Sign in in your browser, or use token import as an advanced recovery path.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            if (pixivPlugin == null) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/login.php")))
                                return@TextButton
                            }
                            val request = pixivPlugin.beginBrowserLogin()
                            if (request.isSuccess) {
                                runCatching {
                                    val customTabsIntent = CustomTabsIntent.Builder()
                                        .setShowTitle(true)
                                        .build()
                                    customTabsIntent.launchUrl(context, Uri.parse(request.getOrThrow().url))
                                }.onFailure {
                                    errorMessage = it.message ?: "Could not open browser sign-in"
                                }
                            } else {
                                errorMessage = request.exceptionOrNull()?.message ?: "Pixiv browser login is unavailable"
                            }
                        },
                        enabled = !isLoading,
                    ) {
                        Text("Sign in with Pixiv (Default Browser / Firefox)")
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
                    TextButton(
                        onClick = {
                            val intent = Intent(context, MoebooruLoginActivity::class.java).apply {
                                putExtra(MoebooruLoginActivity.EXTRA_SITE_NAME, plugin.name)
                                putExtra(MoebooruLoginActivity.EXTRA_LOGIN_URL, "${plugin.platform.webUrl}/user/login")
                                putExtra(MoebooruLoginActivity.EXTRA_EXTERNAL_ID, plugin.platform.externalId)
                            }
                            context.startActivity(intent)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = LatteIcons.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Web (Autofill & Biometrics)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(4.dp))

                    if (isCookieImportMode) {
                        OutlinedTextField(
                            value = cookieInput,
                            onValueChange = {
                                cookieInput = it
                                errorMessage = null
                            },
                            label = { Text("Session cookies / pass_hash") },
                            placeholder = { Text("e.g. user_id=...; session_yande-re=...") },
                            maxLines = 4,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { isCookieImportMode = false },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Use password instead", style = MaterialTheme.typography.bodySmall)
                        }
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
                                        imageVector = if (passwordVisible) LatteIcons.Visibility else LatteIcons.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { isCookieImportMode = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Import session cookies directly", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                    if (isCookieImportMode) {
                        if (cookieInput.isBlank() || isLoading) return@TextButton
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val host = Uri.parse(plugin.platform.webUrl).host ?: plugin.platform.externalId
                            val pairs = cookieInput.split(";").map { it.trim() }
                            val userId = pairs.find { it.startsWith("user_id=") }?.substringAfter("user_id=")
                            val cookieJar = OkHttpProvider.cookieJar
                            val httpUrl = plugin.platform.webUrl.toHttpUrlOrNull()

                            if (httpUrl != null && cookieJar != null) {
                                val okCookies = pairs.mapNotNull { pair ->
                                    val name = pair.substringBefore("=").trim()
                                    val value = pair.substringAfter("=", "").trim()
                                    if (name.isNotEmpty()) {
                                        Cookie.Builder()
                                            .domain(host)
                                            .path("/")
                                            .name(name)
                                            .value(value)
                                            .build()
                                    } else null
                                }
                                cookieJar.saveFromResponse(httpUrl, okCookies)
                            }

                            val resolvedUsername = withContext(Dispatchers.IO) {
                                if (userId != null) {
                                    fetchMoebooruUsername(host, userId)
                                } else null
                            } ?: pairs.find { it.startsWith("login=") }?.substringAfter("login=") ?: "User_$userId"

                            val moebooru = plugin as? MoebooruPlugin
                            if (moebooru != null) {
                                val passHash = pairs.find { it.startsWith("pass_hash=") }?.substringAfter("pass_hash=").orEmpty()
                                moebooru.loginWithSession(resolvedUsername, passHash, userId)
                                ToastManager.showSuccess("Signed in as $resolvedUsername")
                                onLoginSuccess()
                            } else {
                                errorMessage = "Could not apply session to plugin"
                            }
                            isLoading = false
                        }
                        return@TextButton
                    }
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

private fun fetchMoebooruUsername(host: String, userId: String): String? {
    return try {
        val req = Request.Builder().url("https://$host/user.json?id=$userId").build()
        OkHttpProvider.client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                Regex(""""name"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}
