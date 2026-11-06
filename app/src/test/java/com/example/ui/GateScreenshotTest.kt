package com.example.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.gate.GateUi
import com.example.ui.gate.PinPhase
import com.example.ui.gate.PinScreen
import com.example.ui.gate.PinUi
import com.example.ui.gate.UnreachableScreen
import com.example.ui.gate.WhoScreen
import com.example.ui.inscan.InScanTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The sign-in gate against the locked mockup (prototypes/pin.html): same frame, same states. The
// iPhone 17 safe areas (62 top, 34 bottom) are passed in so the frames line up with the mockup.
// The names here are test fixtures, never shipped.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class GateScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val safe = PaddingValues(top = 62.dp, bottom = 34.dp)
    private val people = listOf("Ray Price", "Beth Reyes", "Dale Lewis", "Cody Moss", "Tony Vega", "Jess Tran")

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { InScanTheme { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gate-$name.png")
    }

    private fun pin(name: String, state: PinUi) = shoot(name) { PinScreen(state, {}, {}, {}, safe) }

    @Test fun who() = shoot("who") { WhoScreen(GateUi.Who(people, "Counter phone"), {}, safe) }

    @Test fun pinEmpty() = pin("pin", PinUi("Dale Lewis"))

    @Test fun pinTyping() = pin("typing", PinUi("Dale Lewis", digits = 2))

    @Test fun pinChecking() = pin("checking", PinUi("Dale Lewis", digits = 4, phase = PinPhase.CHECKING, slowCheck = true))

    // The second line needs attemptsLeft from the shop system (spec section 5); drawn here as specified.
    @Test fun pinWrong() = pin("wrong", PinUi("Dale Lewis", digits = 4, phase = PinPhase.WRONG,
        line1 = "That PIN didn't match. Try again.", line2 = "2 tries left before a 5-minute lock."))

    @Test fun pinLocked() = pin("locked", PinUi("Dale Lewis", phase = PinPhase.LOCKED, lockedUntil = "2:19 pm"))

    // Today's server sends no lockedUntil, so this is what a lock shows until it does.
    @Test fun pinLockedNoTime() = pin("locked-no-time", PinUi("Dale Lewis", phase = PinPhase.LOCKED))

    @Test fun pinIdle() = pin("idle", PinUi("Dale Lewis", idle = true))

    @Test fun unreachable() = shoot("unreachable") {
        UnreachableScreen(GateUi.Unreachable("Ray Price", "2:21 pm", stillSignedIn = "Dale Lewis"), {}, {}, safe)
    }

    @Test fun unreachableList() = shoot("unreachable-list") {
        UnreachableScreen(GateUi.Unreachable(null, "2:21 pm", stillSignedIn = null), {}, {}, safe)
    }

    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test fun pinSmallPhone() = pin("pin-360x640", PinUi("Dale Lewis", digits = 1))

    @Config(qualifiers = "w402dp-h874dp-xhdpi", fontScale = 2.0f)
    @Test fun pinFont2() = pin("wrong-font2", PinUi("Dale Lewis", digits = 4, phase = PinPhase.WRONG,
        line1 = "That PIN didn't match. Try again.", line2 = "2 tries left before a 5-minute lock."))
}
