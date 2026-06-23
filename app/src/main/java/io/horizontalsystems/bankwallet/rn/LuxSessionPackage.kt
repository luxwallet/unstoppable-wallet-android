package io.horizontalsystems.bankwallet.rn

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/** Registers [LuxSessionModule] with the RN bridge (see LuxReactHost). */
class LuxSessionPackage : ReactPackage {
    override fun createNativeModules(
        reactContext: ReactApplicationContext,
    ): List<NativeModule> = listOf(LuxSessionModule(reactContext))

    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = emptyList()
}
