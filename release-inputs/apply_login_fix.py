from pathlib import Path

root=Path("ci-source")
a=root/"app/src/main/java/com/example/data/firebase/AuthManager.kt"
s=a.read_text()
s=s.replace("import android.content.Context\n","import android.content.Context\nimport android.content.pm.PackageManager\nimport android.os.Build\nimport java.security.MessageDigest\n",1)
s=s.replace("import androidx.credentials.exceptions.GetCredentialCancellationException\n","import androidx.credentials.exceptions.GetCredentialCancellationException\nimport androidx.credentials.exceptions.NoCredentialException\n",1)
s=s.replace("import com.google.firebase.auth.GoogleAuthProvider\n","import com.google.firebase.auth.GoogleAuthProvider\nimport com.google.firebase.auth.FirebaseAuthException\n",1)
start=s.index("fun attemptAutoSignIn(")
end=s.index("fun onGoogleSignInClicked(",start)
s=s[:start]+'''/** Restore an existing Firebase session without opening a surprise account chooser. */
fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) onAuthSuccess() else onUnauthenticated()
}

@Suppress("DEPRECATION")
fun installedSigningSha1(context: Context): String = runCatching {
  val pm = context.packageManager
  val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
  } else {
    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
      .signatures?.firstOrNull()?.toByteArray()
  } ?: error("Missing signing certificate")
  MessageDigest.getInstance("SHA-1").digest(cert)
    .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}.getOrDefault("Unavailable")

fun onEmailSignInClicked(
  email: String,
  password: String,
  createAccount: Boolean,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
) {
  val trimmed = email.trim()
  if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
    onAuthError("Enter a valid email address.")
    return
  }
  if (password.length < 6) {
    onAuthError("Password must contain at least 6 characters.")
    return
  }
  scope.launch {
    try {
      if (createAccount)
        Firebase.auth.createUserWithEmailAndPassword(trimmed, password).await()
      else
        Firebase.auth.signInWithEmailAndPassword(trimmed, password).await()
      onAuthSuccess()
    } catch (e: Exception) {
      val code = (e as? FirebaseAuthException)?.errorCode
      Log.w(AUTH_TAG, "Email auth failed: " + e.javaClass.simpleName)
      onAuthError(when (code) {
        "ERROR_OPERATION_NOT_ALLOWED" -> "Enable Email/Password in Firebase Authentication > Sign-in method."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Email already registered. Sign in or use the original login method."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_WEAK_PASSWORD" -> "Use a longer password."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Check internet connection."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again later."
        else -> "Email login failed. Check credentials and Firebase setup."
      })
    }
  }
}

'''+s[end:]
s=s.replace('Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: \${e.message}", e)','Log.i(AUTH_TAG, "Google account chooser closed")')
s=s.replace('''      Log.e(AUTH_TAG, "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")''','''      Log.w(AUTH_TAG, "Google sign-in failed: " + e.javaClass.simpleName)
      onAuthError("Google login failed. Open Setup Help below and register this APK SHA-1 in Firebase. (" + e.javaClass.simpleName + ")")''')
a.write_text(s)

u=root/"app/src/main/java/com/example/ui/screens/AuthScreen.kt"
s=u.read_text()
s=s.replace("import androidx.compose.material3.ButtonDefaults\n","import androidx.compose.material3.ButtonDefaults\nimport androidx.compose.material3.OutlinedButton\nimport androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.TextButton\n",1)
s=s.replace("import androidx.compose.runtime.rememberCoroutineScope\n","import androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.ui.text.input.KeyboardType\nimport androidx.compose.ui.text.input.PasswordVisualTransformation\nimport androidx.compose.foundation.text.selection.SelectionContainer\n",1)
s=s.replace("import com.example.data.firebase.onGoogleSignInClicked\n","import com.example.data.firebase.onGoogleSignInClicked\nimport com.example.data.firebase.onEmailSignInClicked\nimport com.example.data.firebase.installedSigningSha1\n",1)
s=s.replace("  var errorMessage by remember { mutableStateOf<String?>(null) }","""  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showEmail by remember { mutableStateOf(false) }
  var registerEmail by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var showHelp by remember { mutableStateOf(false) }""",1)
s=s.replace('Sign in with your Google account to claim a unique @username and chat instantly with anyone on MsgU.', 'Sign in with Google or Email to claim your unique @username and chat on MsgU.',1)
needle='''        }
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow'''
assert needle in s, "AuthScreen layout marker not found"
s=s.replace(needle,'''        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
          onClick = { showEmail = !showEmail; errorMessage = null },
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("email_sign_in_toggle"),
        ) { Text(if (showEmail) "Hide Email Login" else "Sign in with Email instead") }
        if (showEmail) {
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("email_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password (6+ characters)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("password_input"),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = {
              isLoading = true
              errorMessage = null
              onEmailSignInClicked(
                email, password, registerEmail,
                onAuthSuccess = { isLoading = false; password = ""; onAuthSuccess() },
                onAuthError = { isLoading = false; errorMessage = it },
                scope = scope,
              )
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("email_login_button"),
          ) { Text(if (registerEmail) "Create MsgU Account" else "Log In with Email") }
          TextButton(onClick = { registerEmail = !registerEmail; errorMessage = null }) {
            Text(if (registerEmail) "Have an account? Log in" else "New? Create an account")
          }
          Text("Email login needs Firebase Authentication > Email/Password enabled.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        TextButton(onClick = { showHelp = !showHelp }) {
          Text(if (showHelp) "Hide Firebase Setup Help" else "Google sign-in not working? Setup Help")
        }
        if (showHelp) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            SelectionContainer {
              Text(
                "Firebase project: gen-lang-client-0351756978\\\\n" +
                "Android package: " + context.packageName + "\\\\n" +
                "Installed APK SHA-1: " + installedSigningSha1(context) + "\\\\n\\\\n" +
                "Add SHA-1 in Firebase Console > Project settings > Your apps > MsgU. " +
                "Enable Google in Authentication > Sign-in method. " +
                "For Email Login, enable Email/Password in the same settings.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow''',1)
u.write_text(s)
b=root/"app/build.gradle.kts"
s=b.read_text().replace("versionCode = 2","versionCode = 3").replace('versionName = "1.1.0-beta"','versionName = "1.1.1-login-fix-beta"')
b.write_text(s)
for n in ["ChatRepository.kt","UserRepository.kt"]:
 p=root/"app/src/main/java/com/example/data/firebase"/n
 p.write_text(p.read_text().replace("User must be signed in with Google before accessing Firestore.","User must be signed in to MsgU before accessing Firestore."))
print("Auth fallback and login diagnostics applied to AI Studio source")
