package io.horizontalsystems.bankwallet.core.managers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.core.IEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class OidcSession(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAt: Long,
    val scope: String?
)

/**
 * IAM (hanzo.id) OpenID Connect client, per HIP-0111. Public mobile client:
 * Authorization Code + PKCE (S256), no client secret. Layered OVER the local
 * AccountManager — it never replaces local key storage; it only proves the user's
 * Lux identity so a future cloud-sync endpoint can back up / restore.
 *
 * Endpoints are host-relative to [SERVER_URL]; NEVER an `/api/` prefix, NEVER legacy
 * `/oauth/...`. Tokens are encrypted with the app's EncryptionManager and kept in the
 * shared SharedPreferences (no plaintext at rest).
 */
class OidcAuthManager(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val encryptionManager: IEncryptionManager,
) {
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Transient PKCE state, alive only between signIn() and handleRedirect().
    @Volatile
    private var pendingVerifier: String? = null

    @Volatile
    private var pendingState: String? = null

    fun signIn(context: Context) {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        pendingVerifier = verifier
        pendingState = state

        val authorizeUrl = Uri.parse("$SERVER_URL$AUTHORIZE_PATH").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, authorizeUrl)
    }

    suspend fun handleRedirect(uri: Uri): OidcSession {
        val expectedState = pendingState
        val verifier = pendingVerifier
            ?: throw IllegalStateException("OIDC: no PKCE verifier; sign-in was not started")

        uri.getQueryParameter("error")?.let { error ->
            clearPending()
            throw IllegalStateException("OIDC: authorization error: $error")
        }

        val returnedState = uri.getQueryParameter("state")
        if (expectedState == null || returnedState != expectedState) {
            clearPending()
            throw IllegalStateException("OIDC: state mismatch")
        }

        val code = uri.getQueryParameter("code")
            ?: run {
                clearPending()
                throw IllegalStateException("OIDC: missing authorization code")
            }

        val session = exchangeCode(code, verifier)
        clearPending()
        persist(session)
        return session
    }

    fun signOut() {
        clearPending()
        preferences.edit().remove(KEY_SESSION).apply()
    }

    fun currentSession(): OidcSession? {
        val ciphertext = preferences.getString(KEY_SESSION, null) ?: return null
        return try {
            gson.fromJson(encryptionManager.decrypt(ciphertext), OidcSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Persist a session produced outside the PKCE web flow — e.g. a Web3 / SIWx
     * login completed in the @hanzo/gui RN screen and handed back over the
     * LuxSession bridge. Same encrypted store as the OIDC path; one way in.
     */
    fun setSession(session: OidcSession) {
        persist(session)
    }

    fun accessToken(): String? =
        currentSession()?.takeIf { it.expiresAt > nowSeconds() }?.accessToken

    suspend fun userInfo(): Map<String, Any>? {
        val token = accessToken() ?: return null
        val request = Request.Builder()
            .url("$SERVER_URL$USERINFO_PATH")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(body, Map::class.java) as? Map<String, Any>
            }
        }
        // SYNC SEAM: bind accessToken() to cloud backup/restore here once the Lux cloud sync endpoint is finalized.
    }

    private suspend fun exchangeCode(code: String, verifier: String): OidcSession {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url("$SERVER_URL$TOKEN_PATH")
            .post(form)
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    throw IllegalStateException("OIDC: token endpoint returned ${response.code}")
                }
                val token = gson.fromJson(body, TokenResponse::class.java)
                    ?: throw IllegalStateException("OIDC: empty token response")
                val accessToken = token.accessToken
                    ?: throw IllegalStateException("OIDC: missing access_token")
                OidcSession(
                    accessToken = accessToken,
                    refreshToken = token.refreshToken,
                    idToken = token.idToken,
                    expiresAt = nowSeconds() + (token.expiresIn ?: 0),
                    scope = token.scope
                )
            }
        }
    }

    private fun persist(session: OidcSession) {
        val ciphertext = encryptionManager.encrypt(gson.toJson(session))
        preferences.edit().putString(KEY_SESSION, ciphertext).apply()
    }

    private fun clearPending() {
        pendingVerifier = null
        pendingState = null
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("id_token") val idToken: String?,
        @SerializedName("expires_in") val expiresIn: Long?,
        @SerializedName("scope") val scope: String?,
    )

    companion object {
        // HIP-0111: hanzo.id OIDC, host-relative paths, PKCE S256, public client (no secret).
        const val SERVER_URL = "https://hanzo.id"
        const val CLIENT_ID = "lux-wallet"
        const val REDIRECT_URI = "luxwallet://oidc/callback"
        const val SCOPES = "openid profile email"

        private const val AUTHORIZE_PATH = "/v1/iam/oauth/authorize"
        private const val TOKEN_PATH = "/v1/iam/oauth/token"
        private const val USERINFO_PATH = "/v1/iam/oauth/userinfo"
        // jwks: /v1/iam/.well-known/jwks — not needed at runtime for this public client.

        private const val KEY_SESSION = "oidc_session"
    }
}
