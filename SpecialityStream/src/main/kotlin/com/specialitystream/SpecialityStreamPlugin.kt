package com.specialitystream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SpecialityStreamPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SpecialityStreamProvider())
    }
}
