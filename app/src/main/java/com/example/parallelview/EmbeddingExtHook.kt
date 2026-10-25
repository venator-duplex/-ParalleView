package com.example.parallelview

import android.content.Context
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Real in-app split by talking to the platform ActivityEmbedding extension
 * directly - no androidx.window, so no library version gate.
 *
 * Discovered from /system/system_ext/framework/androidx.window.extensions.jar:
 *   WindowExtensionsProvider.getWindowExtensions()
 *     .getActivityEmbeddingComponent()          -> SplitController
 *     .getVendorApiLevel()                      -> 9 on this device
 *   SplitPairRule$Builder(Predicate, Predicate, Predicate)
 *     .setSplitRatio(float) .build()
 *   component.setEmbeddingRules(Set<EmbeddingRule>)
 */
object EmbeddingExtHook {
    private const val TAG = "ParallelView/Ext: "

    fun install(context: Context) {
        XposedBridge.log(TAG + "install() called")
        try {
            val pkg = context.packageName
            /* Some apps ship a custom ClassLoader whose chain does not expose the
             * system_ext window-extensions jar (com.mihoyo.hyperion is one: the
             * app loader threw ClassNotFoundException while Bilibili worked).
             * Try every loader we can reach before giving up. */
            val loaders = listOfNotNull(
                context.javaClass.classLoader,
                ClassLoader.getSystemClassLoader(),
                EmbeddingExtHook::class.java.classLoader,
                null
            )
            var wepCls: Class<*>? = null
            var cl: ClassLoader? = null
            for (candidate in loaders) {
                try {
                    wepCls = Class.forName(
                        "androidx.window.extensions.WindowExtensionsProvider", false, candidate)
                    cl = candidate
                    break
                } catch (_: Throwable) {
                    /* try the next loader */
                }
            }
            if (wepCls == null) {
                return XposedBridge.log(TAG + pkg + ": WindowExtensionsProvider not visible from any loader")
            }
            XposedBridge.log(TAG + pkg + ": extension classes via " + (cl?.javaClass?.name ?: "bootstrap"))
            val wep = wepCls
            cl = cl ?: ClassLoader.getSystemClassLoader()
            val extensions = wep.getMethod("getWindowExtensions").invoke(null)
                ?: return XposedBridge.log(TAG + "no WindowExtensions")
            XposedBridge.log(TAG + "vendorApiLevel = " +
                extensions.javaClass.getMethod("getVendorApiLevel").invoke(extensions))

            val component = extensions.javaClass
                .getMethod("getActivityEmbeddingComponent").invoke(extensions)
                ?: return XposedBridge.log(TAG + "no ActivityEmbeddingComponent")

            val predicate = Class.forName(
                "androidx.window.extensions.core.util.function.Predicate", false, cl)
            val builderCls = Class.forName(
                "androidx.window.extensions.embedding.SplitPairRule\$Builder", false, cl)

            val accept = acceptAll(cl, predicate)
            var builder = builderCls
                .getConstructor(predicate, predicate, predicate)
                .newInstance(accept, accept, accept)
            builder = builderCls.getMethod("setSplitRatio", Float::class.javaPrimitiveType)
                .invoke(builder, Targets.ratio(context))
            val rule = builderCls.getMethod("build").invoke(builder)

            val setRules = component.javaClass.methods.firstOrNull {
                it.name == "setEmbeddingRules" && it.parameterTypes.size == 1
            } ?: return XposedBridge.log(TAG + "setEmbeddingRules missing")
            setRules.invoke(component, setOf(rule))
            XposedBridge.log(TAG + "$pkg: embedding rule installed, ratio=" + Targets.ratio(context))
        } catch (t: Throwable) {
            var c: Throwable = t
            while (c.cause != null && c.cause !== c) c = c.cause!!
            XposedBridge.log(TAG + "install failed: $t / cause: $c")
        }
    }

    private fun acceptAll(cl: ClassLoader, predicateCls: Class<*>): Any =
        Proxy.newProxyInstance(cl, arrayOf(predicateCls), InvocationHandler { proxy, method, args ->
            when (method.name) {
                "test" -> true
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ParallelViewAcceptAll"
                else -> null
            }
        })
}
