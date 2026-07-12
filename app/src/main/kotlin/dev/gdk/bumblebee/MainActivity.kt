package dev.gdk.bumblebee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.gdk.bumblebee.ui.nav.BumblebeeApp
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BumblebeeTheme {
                BumblebeeApp()
            }
        }
    }
}
