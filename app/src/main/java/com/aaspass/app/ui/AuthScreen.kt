package com.aaspass.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aaspass.app.R
import com.aaspass.app.data.AasPassRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(repository: AasPassRepository) {
    var signup by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var resetInfo by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Image(painterResource(R.drawable.splash_mark), "AasPass logo", Modifier.size(106.dp))
        Spacer(Modifier.height(22.dp))
        Text("AasPass", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Connect • Chat • Call • Learn", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(36.dp))
        Text(if (signup) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; error = null },
            label = { Text("Email address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        if (signup) repository.signUp(email, password)
                        else repository.login(email, password)
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "Authentication failed"
                    } finally { busy = false }
                }
            }
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (signup) "Create account" else "Sign in")
        }
        if (!signup) {
            TextButton(onClick = { showReset = true; resetInfo = null }) {
                Text("Forgot password?")
            }
        }
        TextButton(onClick = { signup = !signup; error = null }) {
            Text(if (signup) "Already registered? Sign in" else "New here? Create an account")
        }
        resetInfo?.let { Text(it, color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(14.dp))
        Text(
            "Your conversations are private to participants. End-to-end encryption is not yet enabled.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
    if (showReset) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetBusy by remember { mutableStateOf(false) }
        var resetError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!resetBusy) showReset = false },
            title = { Text("Reset your password") },
            text = {
                Column {
                    Text("Enter your email to receive a password reset link.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(resetEmail, { resetEmail = it; resetError = null },
                        label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    resetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !resetBusy && resetEmail.contains("@"), onClick = {
                    scope.launch {
                        resetBusy = true
                        try {
                            repository.sendPasswordReset(resetEmail)
                            resetInfo = "If an account matches this email, check its inbox for reset instructions."
                            showReset = false
                        } catch (e: Exception) {
                            resetError = e.localizedMessage ?: "Could not request password reset"
                        } finally { resetBusy = false }
                    }
                }) { Text(if (resetBusy) "Sending…" else "Send reset link") }
            },
            dismissButton = { TextButton(enabled = !resetBusy, onClick = { showReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ProfileSetupScreen(repository: AasPassRepository) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create your profile", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose a unique username. Search visibility starts OFF for privacy.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username") },
            supportingText = { Text("3–20 letters, numbers or underscores") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    try { repository.createProfile(name, username) }
                    catch (e: Exception) { error = e.localizedMessage ?: "Profile setup failed" }
                    finally { busy = false }
                }
            }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Saving…" else "Continue to AasPass") }
        TextButton(onClick = {
            scope.launch {
                try { repository.logout() }
                catch (e: Exception) { error = e.localizedMessage ?: "Unable to sign out safely" }
            }
        }) { Text("Sign out") }
    }
}
