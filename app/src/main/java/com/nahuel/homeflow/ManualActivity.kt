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
        // Let the manual's own light/dark CSS show through the WebView background.
        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        web.setBackgroundColor(android.graphics.Color.parseColor(if (night) "#201E1D" else "#F3F2F2"))
        web.loadUrl("file:///android_asset/anleitung.html")
        setContentView(web)
    }
}
