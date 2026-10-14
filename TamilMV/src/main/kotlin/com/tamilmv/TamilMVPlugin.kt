package com.tamilmv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TamilMVPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TamilMVProvider())
    }
}
