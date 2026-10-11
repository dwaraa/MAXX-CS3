package com.tamildhool

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TamilDhoolPlugin : Plugin() {
    override fun load(context: Context) {
        // Register the TamilDhool MainAPI provider into Cloudstream
        registerMainAPI(TamilDhoolProvider())
    }
}
