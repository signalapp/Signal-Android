/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import org.thoughtcrime.securesms.window.AppScaffoldAnimationDefaults
import org.thoughtcrime.securesms.window.AppScaffoldAnimationState
import org.thoughtcrime.securesms.window.WindowSizeClass
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.milliseconds

fun NavGraphBuilder.chatNavGraphBuilder(
  chatNavGraphState: ChatNavGraphState
) {
  composable<MainNavigationDetailLocation.Empty> {
    EmptyDetailScreen()
  }

  composable<MainNavigationDetailLocation.Chats.Conversation>(
    typeMap = mapOf(
      typeOf<ConversationArgs>() to JsonSerializableNavType(ConversationArgs.serializer())
    )
  ) { navBackStackEntry ->
    val route = navBackStackEntry.toRoute<MainNavigationDetailLocation.Chats.Conversation>()
    val fragmentState = key(route) { rememberFragmentState() }
    val context = LocalContext.current
    val insets by rememberVerticalInsets()
    val insetFlow = remember { snapshotFlow { insets } }

    // Because it can take a long time to load content, we use a "fake" chat list image to delay displaying
    // the fragment and prevent pop-in
    var shouldDisplayFragment by remember { mutableStateOf(false) }
    val transition: Transition<Boolean> = updateTransition(shouldDisplayFragment)
    val bitmap = chatNavGraphState.chatBitmap

    val fakeChatListAnimationState = transition.fakeChatListAnimationState()
    val chatAnimationState = transition.chatAnimationState(bitmap != null)

    LaunchedEffect(transition.currentState, transition.isRunning) {
      if (transition.currentState && !transition.isRunning) {
        chatNavGraphState.clearBitmap()
      }
    }

    LaunchedEffect(shouldDisplayFragment) {
      (context as? MainNavigator.NavigatorProvider)?.onFirstRender()
    }

    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
          .graphicsLayer {
            with(fakeChatListAnimationState) {
              applyChildValues()
            }
          }
          .fillMaxSize()
      )
    }

    AndroidFragment(
      clazz = ConversationFragment::class.java,
      fragmentState = fragmentState,
      arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) { "Handed null Conversation intent arguments." },
      modifier = Modifier
        .graphicsLayer {
          with(chatAnimationState) {
            applyChildValues()
          }
        }
        .background(MaterialTheme.colorScheme.background)
        .fillMaxSize()
    ) { fragment ->
      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
          insetFlow.collect {
            fragment.applyRootInsets(insets)
          }
        }
      }

      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
          fragment.didFirstFrameRender.collectLatest {
            shouldDisplayFragment = it
            if (!it) {
              delay(150.milliseconds)
              shouldDisplayFragment = true
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Transition<Boolean>.fakeChatListAnimationState(): AppScaffoldAnimationState {
  val alpha = animateFloat(transitionSpec = { AppScaffoldAnimationDefaults.tween() }) { if (it) 0f else 1f }
  val offset = animateDp(transitionSpec = { AppScaffoldAnimationDefaults.tween() }) { if (it) (-48).dp else 0.dp }

  return remember {
    AppScaffoldAnimationState(
      offset = offset,
      alpha = alpha
    )
  }
}

@Composable
private fun Transition<Boolean>.chatAnimationState(hasFake: Boolean): AppScaffoldAnimationState {
  val alpha = animateFloat(transitionSpec = { AppScaffoldAnimationDefaults.tween() }) { if (it) 1f else 0f }

  return if (!hasFake) {
    remember {
      AppScaffoldAnimationState(
        offset = mutableStateOf(0.dp),
        alpha = alpha
      )
    }
  } else {
    val offset = animateDp(transitionSpec = { AppScaffoldAnimationDefaults.tween() }) { if (it) 0.dp else 48.dp }

    remember {
      AppScaffoldAnimationState(
        offset = offset,
        alpha = alpha
      )
    }
  }
}

/**
 * Allows the setting of a "fake" bitmap driven by a graphics layer to coordinate delayed animations
 * in lieu of proper support for postponing enter transitions.
 */
@Stable
class ChatNavGraphState private constructor(
  val windowSizeClass: WindowSizeClass,
  val graphicsLayer: GraphicsLayer
) {
  companion object {
    @Composable
    fun remember(windowSizeClass: WindowSizeClass): ChatNavGraphState {
      val graphicsLayer = rememberGraphicsLayer()

      return remember(windowSizeClass) {
        ChatNavGraphState(
          windowSizeClass,
          graphicsLayer
        )
      }
    }
  }

  var chatBitmap: ImageBitmap? by mutableStateOf(null)
    private set

  private var hasWrittenToGraphicsLayer: Boolean by mutableStateOf(false)

  suspend fun writeGraphicsLayerToBitmap() {
    if (WindowSizeClass.isLargeScreenSupportEnabled() && !windowSizeClass.isSplitPane() && hasWrittenToGraphicsLayer) {
      chatBitmap = graphicsLayer.toImageBitmap()
    }
  }

  fun writeContentToGraphicsLayer(): Modifier {
    return Modifier.drawWithContent {
      graphicsLayer.record {
        this@drawWithContent.drawContent()
        hasWrittenToGraphicsLayer = true
      }

      drawLayer(graphicsLayer)
    }
  }

  fun clearBitmap() {
    chatBitmap = null
  }
}
