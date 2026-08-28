package app.promise.android.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleSignInConfig {
    const val WEB_CLIENT_ID = "547289698615-huc17692on1292athsn3ph80fiaagm9p.apps.googleusercontent.com"
}

sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

suspend fun performGoogleSignIn(context: Context): GoogleSignInResult {
    return try {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(GoogleSignInConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = context,
        )

        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(googleIdTokenCredential.idToken)
        } else {
            GoogleSignInResult.Error("Unsupported credential type returned.")
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: GetCredentialException) {
        if (e.message?.contains("cancel", ignoreCase = true) == true) {
            GoogleSignInResult.Cancelled
        } else {
            GoogleSignInResult.Error(e.localizedMessage ?: "Google sign in failed")
        }
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException ||
            t is java.util.concurrent.CancellationException ||
            t.message?.contains("cancel", ignoreCase = true) == true
        ) {
            GoogleSignInResult.Cancelled
        } else {
            GoogleSignInResult.Error(t.localizedMessage ?: "Google sign in failed")
        }
    }
}

suspend fun launchGoogleSignIn(
    context: Context,
    onSuccess: (idToken: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
    onCancelled: () -> Unit = {},
) {
    when (val result = performGoogleSignIn(context)) {
        is GoogleSignInResult.Success -> onSuccess(result.idToken)
        is GoogleSignInResult.Cancelled -> onCancelled()
        is GoogleSignInResult.Error -> onError(result.message)
    }
}
