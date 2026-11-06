package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.InventoryViewModel
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.Palette
import com.example.ui.shell.InScanApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Charcoal behind both system bars, with light icons.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Palette.bg.toArgb()),
      navigationBarStyle = SystemBarStyle.dark(Palette.surface.toArgb()),
    )
    setContent {
      InScanTheme {
        val inventoryViewModel: InventoryViewModel = viewModel()
        InScanApp(viewModel = inventoryViewModel)
      }
    }
  }
}
