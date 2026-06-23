package io.horizontalsystems.bankwallet.rn

import android.app.Application
import com.facebook.react.ReactHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.ReactNativeHost
import com.facebook.soloader.SoLoader

/**
 * Brownfield React Native host for the additive @hanzo/gui screens
 * (shared bundle: @luxwallet/mobile-rn). Composition, NOT inheritance — the
 * Application already extends CoreApp, so we cannot extend ReactApplication.
 * App.onCreate() calls [init] once; native screens stay untouched.
 *
 * The JS bundle registers components by name (see ../../../../rn index.js):
 *   "LuxLogin" -> the SIWx multi-chain login screen.
 */
object LuxReactHost {

    private lateinit var application: Application

    /** Lazily-built RN host pointing at the @luxwallet/mobile-rn bundle. */
    val reactHost: ReactHost by lazy {
        DefaultReactHost.getDefaultReactHost(
            application.applicationContext,
            reactNativeHost,
        )
    }

    private val reactNativeHost: ReactNativeHost by lazy {
        object : DefaultReactNativeHost(application) {
            override fun getUseDeveloperSupport(): Boolean =
                io.horizontalsystems.bankwallet.BuildConfig.DEBUG

            // Autolinked packages (reanimated, screens, svg, safe-area-context)
            // are appended by the RN Gradle plugin via PackageList at build time.
            override fun getPackages(): List<ReactPackage> =
                com.facebook.react.PackageList(this).packages.apply {
                    add(LuxSessionPackage())
                }

            override fun getJSMainModuleName(): String = "index"
            override fun getBundleAssetName(): String = "index.android.bundle"

            override val isNewArchEnabled: Boolean = true
            override val isHermesEnabled: Boolean = true
        }
    }

    /** Call once from App.onCreate() AFTER super.onCreate(). */
    fun init(app: Application) {
        application = app
        SoLoader.init(app, /* native exopackage */ false)
    }
}
