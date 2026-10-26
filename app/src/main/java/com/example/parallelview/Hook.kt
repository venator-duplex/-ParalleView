package com.example.parallelview

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry. ActivityThread.currentApplication() is null during
 * handleLoadPackage, so the decision waits for Instrumentation to create the
 * Application, which also gives us a usable Context.
 */
class Hook : IXposedHookLoadPackage {
    private companion object {
        const val TAG = "ParallelView: "
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log(TAG + "loaded in " + lpparam.packageName)
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation",
                lpparam.classLoader,
                "callApplicationOnCreate",
                "android.app.Application",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val app = param.args[0] as android.app.Application
                            /* LSPosed's scope IS the authorization: if the module
                             * was injected at all, the user selected this app there.
                             * The old Targets.isParallel() gate carried a hardcoded
                             * fallback list (both B站 variants + 酷安), which made it
                             * look like only Bilibili was supported. */
                            if (lpparam.packageName == "com.example.parallelview") return
                            XposedBridge.log(TAG + "enabling for " + lpparam.packageName)
                            EmbeddingExtHook.install(app)
                        } catch (t: Throwable) {
                            XposedBridge.log(TAG + "install threw: $t")
                        }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log(TAG + "arming failed in " + lpparam.packageName + ": $t")
        }
    }
}
