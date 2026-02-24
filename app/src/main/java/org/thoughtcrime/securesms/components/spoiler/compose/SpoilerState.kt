package org.thoughtcrime.securesms.components.spoiler.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * State holder for managing which spoilers have been revealed in Compose.
 * Similar to the View-based SpoilerAnnotation, but uses Compose state management.
 */
@Stable
class SpoilerState {
  private var revealedSpoilers by mutableStateOf(setOf<String>())

  /**
   * Check if a spoiler with the given ID has been revealed.
   */
  fun isRevealed(spoilerId: String): Boolean {
    return spoilerId in revealedSpoilers
  }

  /**
   * Reveal a spoiler with the given ID.
   */
  fun reveal(spoilerId: String) {
    revealedSpoilers = revealedSpoilers + spoilerId
  }

  /**
   * Reset all revealed spoilers.
   */
  fun reset() {
    revealedSpoilers = emptySet()
  }
}

/**
 * Remember a [SpoilerState] instance.
 */
@Composable
fun rememberSpoilerState(): SpoilerState {
  return remember { SpoilerState() }
}
