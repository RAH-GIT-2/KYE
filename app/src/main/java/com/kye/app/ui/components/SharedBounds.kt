@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.kye.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Set around the app's screens so a tapped card can grow into the screen it opens. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/leave animation of the screen being shown (one per navigation destination). */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key shared by an entry's row and its edit form. */
fun entryKey(id: Long) = "txn-$id"

/** Key shared by the + button and a new, empty form. */
const val NEW_ENTRY_KEY = "txn-new"

/**
 * The same [key] on two screens makes one grow into the other when moving between them:
 * a row in a list becomes the edit form, and shrinks back on the way out. Does nothing
 * outside the app's screens (e.g. in a sheet).
 */
@Composable
fun Modifier.sharedCard(key: String, scope: AnimatedVisibilityScope? = LocalNavAnimatedScope.current): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = scope ?: return this
    return with(shared) {
        this@sharedCard.sharedBounds(
            rememberSharedContentState(key),
            anim,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
        )
    }
}
