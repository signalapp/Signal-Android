package org.signal.camera.demo

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import org.signal.camera.demo.screens.gallery.GalleryScreen
import org.signal.camera.demo.screens.imageviewer.ImageViewerScreen
import org.signal.camera.demo.screens.main.MainScreen
import org.signal.camera.demo.screens.videoviewer.VideoViewerScreen

/**
 * Navigation destinations as an enum (automatically Parcelable).
 *
 * To add a new destination:
 * 1. Add a new enum value here
 * 2. Add a corresponding entry provider in NavGraph.kt
 */
enum class Screen : NavKey {
  Main,
  Gallery,
  ImageViewer,
  VideoViewer
}

@Composable
fun NavGraph(
  modifier: Modifier = Modifier
) {
  val backStack = rememberNavBackStack(Screen.Main)
  
  @Suppress("UNCHECKED_CAST")
  val typedBackStack = backStack as NavBackStack<Screen>
  
  NavDisplay(
    backStack = backStack,
    modifier = modifier,
    transitionSpec = {
      // Gallery slides up from bottom
      slideInHorizontally(
        initialOffsetX = { fullWidth ->  fullWidth },
        animationSpec = tween(500)
      ) togetherWith
      // Camera stays in place and fades out
      slideOutHorizontally (
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(500)
      )
    },
    popTransitionSpec = {
      // Camera slides back in from left
      slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(500)
      ) togetherWith
      // Gallery slides out to right
      slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(500)
      )
    },
    predictivePopTransitionSpec = { progress ->
      // Camera slides back in from left (predictive with progress)
      slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(500)
      ) togetherWith
      // Gallery slides out to right (predictive with progress)
      slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(500)
      )
    },
    entryProvider = entryProvider {
      addEntryProvider(
        key = Screen.Main, 
        contentKey = Screen.Main, 
        metadata = emptyMap()
      ) { screen: Screen ->
        MainScreen(backStack = typedBackStack)
      }
      
      addEntryProvider(
        key = Screen.Gallery,
        contentKey = Screen.Gallery,
        metadata = emptyMap()
      ) { screen: Screen ->
        GalleryScreen(backStack = typedBackStack)
      }
      
      addEntryProvider(
        key = Screen.ImageViewer,
        contentKey = Screen.ImageViewer,
        metadata = emptyMap()
      ) { screen: Screen ->
        ImageViewerScreen(backStack = typedBackStack)
      }
      
      addEntryProvider(
        key = Screen.VideoViewer,
        contentKey = Screen.VideoViewer,
        metadata = emptyMap()
      ) { screen: Screen ->
        VideoViewerScreen(backStack = typedBackStack)
      }
    }
  )
}
