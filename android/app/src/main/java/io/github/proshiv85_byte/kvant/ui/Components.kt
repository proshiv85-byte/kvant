// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class ButtonVariant { Standard, Tonal, Filled }

private val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
private val bouncyDp = spring<Dp>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/**
 * M3 icon button. Squishes and squares up while pressed, swells on (mouse) hover and
 * pops its icon when tapped. The icon can spin or nudge when lit, like on desktop.
 */
@Composable
fun KIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Standard,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
    litRotation: Float = 0f,
    litNudge: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val lit = (pressed || hovered) && enabled

    val fg = when (variant) {
        ButtonVariant.Filled -> colors.onPrimary
        ButtonVariant.Tonal -> colors.onSecondaryContainer
        ButtonVariant.Standard -> if (lit) colors.onSurface else colors.onSurfaceVariant
    }
    val bg = when (variant) {
        ButtonVariant.Filled -> colors.primary
        ButtonVariant.Tonal -> colors.secondaryContainer
        ButtonVariant.Standard -> Color.Transparent
    }

    val scale by animateFloatAsState(if (pressed) 0.88f else if (hovered && enabled) 1.08f else 1f, bouncy, label = "scale")
    val corner by animateDpAsState(if (pressed) size * 0.28f else if (hovered) size * 0.4f else size / 2, bouncyDp, label = "corner")
    val rotation by animateFloatAsState(if (lit) litRotation else 0f, spring(dampingRatio = 0.4f), label = "rotation")
    val nudge by animateDpAsState(if (lit) litNudge else 0.dp, bouncyDp, label = "nudge")
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = fg),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
            ) {
                scope.launch {
                    pop.snapTo(0.75f)
                    pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription, tint = fg,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = rotation
                    translationX = nudge.toPx()
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

/** M3 common button (filled / tonal / text) with an optional leading icon. */
@Composable
fun KTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: ButtonVariant = ButtonVariant.Tonal,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val fg = when (variant) {
        ButtonVariant.Filled -> colors.onPrimary
        ButtonVariant.Tonal -> colors.onSecondaryContainer
        ButtonVariant.Standard -> colors.primary
    }
    val bg = when (variant) {
        ButtonVariant.Filled -> colors.primary
        ButtonVariant.Tonal -> colors.secondaryContainer
        ButtonVariant.Standard -> Color.Transparent
    }
    val scale by animateFloatAsState(if (pressed) 0.94f else if (hovered) 1.04f else 1f, bouncy, label = "scale")
    val corner by animateDpAsState(if (pressed) 12.dp else 20.dp, bouncyDp, label = "corner")
    val iconTilt by animateFloatAsState(if (pressed || hovered) -12f else 0f, spring(dampingRatio = 0.4f), label = "tilt")

    Row(
        modifier = modifier
            .height(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(color = fg), role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 16.dp else 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = iconTilt })
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Big play/pause button: a circle while paused, morphs to a rounded square while playing. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else if (hovered) 1.06f else 1f, bouncy, label = "scale")
    val corner by animateDpAsState(
        when {
            pressed -> size * 0.2f
            playing -> size * 0.3f
            else -> size / 2
        },
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "corner",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(colors.primary)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClickLabel = if (playing) "Pause" else "Play",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.4f) + fadeIn(tween(150)))
                    .togetherWith(scaleOut(tween(150), targetScale = 0.4f) + fadeOut(tween(150)))
            },
            label = "playPause",
        ) { isPlaying ->
            Icon(
                if (isPlaying) KvantIcons.Pause else KvantIcons.Play,
                if (isPlaying) "Pause" else "Play",
                tint = colors.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** Album art with rounded corners, or a placeholder; cross-fades between tracks. */
@Composable
fun Cover(image: ImageBitmap?, modifier: Modifier = Modifier, corner: Dp = 28.dp) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(corner)).background(colors.secondaryContainer)) {
        AnimatedContent(targetState = image, transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(350))) }, label = "cover") { img ->
            if (img != null) {
                Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(KvantIcons.Music, null, tint = colors.onSecondaryContainer, modifier = Modifier.fillMaxSize(0.36f))
                }
            }
        }
    }
}
