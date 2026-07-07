package com.nahuel.homeflow

import android.app.Application
import com.nahuel.homeflow.data.Store

class HomeFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
