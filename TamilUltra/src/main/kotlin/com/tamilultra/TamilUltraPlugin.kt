package com.tamilultra

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TamilUltraPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TamilUltraProvider())
    }
}
