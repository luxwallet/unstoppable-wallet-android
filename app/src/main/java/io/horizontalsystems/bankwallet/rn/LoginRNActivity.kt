package io.horizontalsystems.bankwallet.rn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.ReactDelegate

/**
 * Hosts the "LuxLogin" @hanzo/gui screen (from @luxwallet/mobile-rn) inside the
 * existing native app. Launched additively from one native entry point:
 *
 *   startActivity(Intent(this, LoginRNActivity::class.java))
 *
 * Existing native login/screens are unaffected.
 */
class LoginRNActivity : AppCompatActivity() {

    private lateinit var reactDelegate: ReactDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reactDelegate = ReactDelegate(
            this,
            LuxReactHost.reactHost,
            /* mainComponentName */ "LuxLogin",
            /* launchOptions */ null,
        )
        reactDelegate.loadApp()
        setContentView(reactDelegate.reactRootView)
    }

    override fun onResume() {
        super.onResume()
        reactDelegate.onHostResume()
    }

    override fun onPause() {
        super.onPause()
        reactDelegate.onHostPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        reactDelegate.onHostDestroy()
    }
}
