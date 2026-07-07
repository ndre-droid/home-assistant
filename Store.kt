package com.nahuel.homeflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity

/** In-app manual: renders the bundled HTML guide (assets/anleitung.html), fully offline. */
class ManualActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.setBackgroundColor(android.graphics.Color.parseColor("#0E1116"))
        web.loadUrl("file:///android_asset/anleitung.html")
        setContentView(web)
    }
}
