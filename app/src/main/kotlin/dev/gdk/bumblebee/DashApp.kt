package dev.gdk.bumblebee

import android.app.Application
import dev.gdk.bumblebee.data.local.BumblebeeDb

class DashApp : Application() {
    val database: BumblebeeDb by lazy { BumblebeeDb.create(this) }
}
