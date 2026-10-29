package com.zdkserials

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ZdkSerialsPlugin : Plugin() {
    override fun load(context: Context) {
        // Register the ZDK Serials MainAPI provider into Cloudstream
        registerMainAPI(ZdkSerialsProvider())
    }
}

