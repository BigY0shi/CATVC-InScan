package com.example

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.InventoryViewModel
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.Palette
import com.example.ui.shell.InScanApp

class MainActivity : ComponentActivity() {
  // Set once the first screen has composed, i.e. the local "who is signed in" read is done.
  @Volatile private var firstFrameReady = false

  override fun onCreate(savedInstanceState: Bundle?) {
    // Splash (splash-ui-spec.md 4a, D-SP4): it waits only for the local sign-in read, never the
    // network, and a hard 1 s cap releases it even if that read is slow.
    val splash = installSplashScreen()
    val shownAt = SystemClock.uptimeMillis()
    splash.setKeepOnScreenCondition { !firstFrameReady && SystemClock.uptimeMillis() - shownAt < SPLASH_CAP_MS }
    // Exit: a 200 ms fade to the first screen.
    splash.setOnExitAnimationListener { provider ->
      provider.view.animate().alpha(0f).setDuration(200L).withEndAction { provider.remove() }.start()
    }
    super.onCreate(savedInstanceState)
    // Charcoal behind both system bars, with light icons.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Palette.bg.toArgb()),
      navigationBarStyle = SystemBarStyle.dark(Palette.surface.toArgb()),
    )
    setContent {
      InScanTheme {
        val inventoryViewModel: InventoryViewModel = viewModel()
        // The view model reads this phone's connection and sign-in from local storage when it is
        // created, so by the first composition the first screen (sign-in or Home) is known.
        LaunchedEffect(Unit) { firstFrameReady = true }
        InScanApp(viewModel = inventoryViewModel)
      }
    }
  }

  private companion object {
    const val SPLASH_CAP_MS = 1_000L
  }
}

