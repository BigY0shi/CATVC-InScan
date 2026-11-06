package com.example.ui

import android.graphics.drawable.AnimatedVectorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// splash-ui-spec.md section 10: the launcher icon in circle, squircle and rounded-square masks, and
// themed, at 48 dp and 108 dp; plus the splash mark on charcoal.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w520dp-h600dp-xhdpi")
class IconScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val squircle = RoundedCornerShape(38)
    private val rounded = RoundedCornerShape(22)

    @Test
    fun launcherIconInEveryMask() {
        compose.setContent {
            Column(Modifier.background(Color(0xFF3B3F45)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (s in listOf(108.dp, 48.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(s)) { CenteredAdaptive(CircleShape, s) }
                        Box(Modifier.size(s)) { CenteredAdaptive(squircle, s) }
                        Box(Modifier.size(s)) { CenteredAdaptive(rounded, s) }
                        Box(Modifier.size(s)) { CenteredAdaptive(CircleShape, s, themed = true) }
                    }
                }
                Box(Modifier.size(192.dp).background(Color(0xFF1E1F21)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Image(painterResource(R.drawable.inscan_splash_mark), null, Modifier.size(288.dp))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/icon-masks.png")
    }

    @androidx.compose.runtime.Composable
    private fun CenteredAdaptive(shape: Shape, size: Dp, themed: Boolean = false) {
        Box(Modifier.size(size).clip(shape).background(if (themed) Color(0xFFD6E8DF) else Color(0xFF10B981)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Image(
                painterResource(if (themed) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground), null,
                Modifier.size(size * 1.5f),
                colorFilter = if (themed) ColorFilter.tint(Color(0xFF1B4D3E)) else null,
            )
        }
    }

    @Test
    fun splashAnimationInflates() {
        val d = ContextCompat.getDrawable(ApplicationProvider.getApplicationContext(), R.drawable.avd_inscan_splash)
        assertTrue("splash icon must be an animated vector, got $d", d is AnimatedVectorDrawable)
    }
}
