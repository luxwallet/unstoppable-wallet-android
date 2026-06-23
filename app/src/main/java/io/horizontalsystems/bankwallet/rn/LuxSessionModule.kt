package io.horizontalsystems.bankwallet.rn

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import io.horizontalsystems.bankwallet.core.App
import io.horizontalsystems.bankwallet.core.managers.OidcSession

/**
 * Native↔RN session bridge. The @hanzo/gui login screen (in @luxwallet/mobile-rn)
 * calls NativeModules.LuxSession.setSession(...) after a successful SIWx login;
 * we persist it into the EXISTING encrypted store via App.oidcAuthManager — so
 * the shared-UI login flows straight into the native wallet's session. Additive:
 * the native wallet keeps its own keystore; this only carries the auth token.
 */
class LuxSessionModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "LuxSession"

    @ReactMethod
    fun setSession(
        token: String,
        refreshToken: String,
        expiresAt: Double,
        address: String,
        chain: String,
    ) {
        App.oidcAuthManager.setSession(
            OidcSession(
                accessToken = token,
                refreshToken = refreshToken.ifEmpty { null },
                idToken = null,
                expiresAt = expiresAt.toLong(),
                // record the proven wallet identity in the scope field for now.
                scope = "web3:$chain:$address",
            ),
        )
    }

    @ReactMethod
    fun getSession(promise: Promise) {
        promise.resolve(App.oidcAuthManager.accessToken())
    }

    /** Dismiss the RN host activity from JS after login completes. */
    @ReactMethod
    fun close() {
        currentActivity?.finish()
    }
}
