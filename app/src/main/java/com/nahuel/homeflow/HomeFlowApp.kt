package com.nahuel.homeflow

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nahuel.homeflow.data.Store

class HomeFlowApp : Application() {
    companion object {
        @Volatile private var started = 0
        /** True while any of our activities is visible (drives toast vs. notification). */
        val isForeground: Boolean get() = started > 0
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++ }
            override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
