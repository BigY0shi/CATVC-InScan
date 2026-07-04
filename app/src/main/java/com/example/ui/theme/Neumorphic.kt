package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-fidelity Neumorphic Card for dark mode.
 * Combines light-catching edge borders with a deep bottom shadow to create a volumetric extruded effect.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = CinematicCharcoalDark,
    lightBorderColor: Color = Color(0xFF2C2C2C),
    darkBorderColor: Color = Color(0xFF101010),
    shadowElevation: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            // Deep ambient bottom-right dark shadow
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
                clip = false
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.95f),
                        CinematicCharcoalMedium.copy(alpha = 0.95f)
                    )
                ),
                shape = shape
            )
            // Premium skeuomorphic double-border simulating directional light hitting the top-left edge
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lightBorderColor,
                        darkBorderColor
                    )
                ),
                shape = shape
            )
            .padding(1.5.dp) // Offset for the light-catch border
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        CinematicCharcoalMedium
                    )
                ),
                shape = shape
            )
            .clip(shape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * A "Recessed" (Sunken/Debossed) panel.
 * Simulates a container carved deep into the charcoal dashboard.
 */
@Composable
fun NeumorphicRecessedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = CinematicBlack,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF030303),
                        Color(0xFF141414)
                    )
                ),
                shape = shape
            )
            // Internal rim highlight on the bottom-right to simulate light pooling
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000000),
                        Color(0xFF242424)
                    )
                ),
                shape = shape
            )
            .padding(1.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        Color(0xFF0D0D0D)
                    )
                ),
                shape = shape
            )
            .clip(shape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Interactive Neumorphic Button with cinematic micro-interactions.
 * Integrates physical spring animations:
 * - When hovered/pressed, the button physically sinks (reduces scale and shadow elevation)
 * - Highlights dynamically change to respond to pressure.
 */
@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = CinematicCharcoalMedium,
    accentColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Cinematic Spring Animations for pressure & height
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ButtonScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonElevation"
    )

    val borderTopLight by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF101010) else Color(0xFF3A3A3A),
        label = "ButtonTopBorder"
    )

    val borderBottomDark by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF2E2E2E) else Color(0xFF020202),
        label = "ButtonBottomBorder"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
                clip = false
            )
            // Outer bezel gradient
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(borderTopLight, borderBottomDark)
                ),
                shape = shape
            )
            .padding(1.5.dp) // Rim width
            .background(
                brush = Brush.linearGradient(
                    colors = if (accentColor != null) {
                        if (isPressed) {
                            listOf(accentColor.copy(alpha = 0.85f), accentColor)
                        } else {
                            listOf(accentColor, accentColor.copy(alpha = 0.75f))
                        }
                    } else {
                        if (isPressed) {
                            listOf(containerColor, CinematicCharcoalDark)
                        } else {
                            listOf(containerColor.copy(alpha = 1.1f), containerColor)
                        }
                    }
                ),
                shape = shape
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (accentColor != null) BoneWhite else BoneWhite
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Circular Neumorphic Icon Button, great for icon-only actions (scanners, close buttons, deletes).
 */
@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = CinematicCharcoalMedium,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = remember { RoundedCornerShape(50) } // Circular
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "IconButtonScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "IconButtonElevation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
                clip = false
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isPressed) {
                        listOf(Color(0xFF101010), Color(0xFF2C2C2C))
                    } else {
                        listOf(Color(0xFF333333), Color(0xFF040404))
                    }
                ),
                shape = shape
            )
            .padding(1.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = if (accentColor != null) {
                        listOf(accentColor, accentColor.copy(alpha = 0.8f))
                    } else {
                        listOf(containerColor, CinematicCharcoalDark)
                    }
                ),
                shape = shape
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable the stock ripple to keep the cinematic pressure feel pristine
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
