package com.einthusan

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class EinthusanPlugin : Plugin() {
    override fun load(context: Context) {
        // Register the Einthusan MainAPI provider into Cloudstream
        registerMainAPI(EinthusanProvider())
    }
}
