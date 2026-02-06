package org.signal.camera

import android.content.res.Configuration
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview as CameraPreview
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Previews

/**
 * A camera screen that handles core camera functionality, such as:
 * - Tap to focus
 * - Pinch to zoom
 * - Camera switching
 *
 * among other things.
 *
 * This composable is state-driven and emits events through [emitter]. The parent
 * composable is responsible for handling these events, typically by forwarding them
 * to a [CameraScreenViewModel].
 *
 * Use the [content] parameter to overlay custom HUD elements on top of the camera.
 * For a ready-to-use HUD, see [org.signal.camera.hud.StandardCameraHud].
 *
 * @param state The camera screen state, typically from a [CameraScreenViewModel].
 * @param emitter Callback for events that need to be handled by the parent, likely via [CameraScreenViewModel].
 * @param modifier Modifier to apply to the camera container.
 * @param roundCorners Whether to apply rounded corners to the camera viewfinder. Defaults to true.
 * @param contentAlignment The alignment of the camera viewfinder within the available space. Defaults to center.
 * @param content Composable content to overlay on top of the camera surface. The content is placed in a Box
 *    with the same size and position as the camera surface.
 */
@Composable
fun CameraScreen(
  state: CameraScreenState,
  emitter: (CameraScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  roundCorners: Boolean = true,
  contentAlignment: Alignment = Alignment.Center,
  content: @Composable BoxScope.() -> Unit = {}
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val configuration = LocalConfiguration.current
  val isInPreview = LocalInspectionMode.current

  // State to hold the surface request from CameraX Preview
  var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

  // Determine aspect ratio based on orientation
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val aspectRatio = if (isLandscape) 16f / 9f else 9f / 16f

  // Bind camera and setup surface provider
  LaunchedEffect(lifecycleOwner, state.lensFacing) {
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()

    val surfaceProvider = CameraPreview.SurfaceProvider { request ->
      surfaceRequest = request
    }

    emitter(
      CameraScreenEvents.BindCamera(
        lifecycleOwner = lifecycleOwner,
        cameraProvider = cameraProvider,
        surfaceProvider = surfaceProvider,
        context = context
      )
    )
  }

  BoxWithConstraints(
    contentAlignment = contentAlignment,
    modifier = modifier.fillMaxSize()
  ) {
    // Determine whether to match height constraints first based on available space.
    val availableAspectRatio = maxWidth / maxHeight
    val matchHeightFirst = availableAspectRatio > aspectRatio

    Box(
      modifier = Modifier
        .aspectRatio(aspectRatio, matchHeightConstraintsFirst = matchHeightFirst)
    ) {
      val cornerShape = if (roundCorners) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)

      if (isInPreview) {
        // Preview placeholder - shows a dark box with border to represent camera viewfinder
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clip(cornerShape)
            .drawBehind {
              drawRect(Color(0xFF1A1A1A))
            }
        )
      } else if (surfaceRequest != null) {
        CameraXViewfinder(
          surfaceRequest = surfaceRequest!!,
          modifier = Modifier
            .fillMaxSize()
            .clip(cornerShape)
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  emitter(CameraScreenEvents.SwitchCamera(context))
                },
                onTap = { offset ->
                  emitter(
                    CameraScreenEvents.TapToFocus(
                      x = offset.x,
                      y = offset.y,
                      width = size.width.toFloat(),
                      height = size.height.toFloat()
                    )
                  )
                }
              )
            }
            .pointerInput(Unit) {
              detectTransformGestures { _, _, zoom, _ ->
                emitter(CameraScreenEvents.PinchZoom(zoom))
              }
            }
        )
      }

      if (state.showFocusIndicator && state.focusPoint != null) {
        FocusIndicator(
          focusPoint = state.focusPoint,
          modifier = Modifier.fillMaxSize()
        )
      }

      // Selfie flash overlay (white screen for front camera)
      SelfieFlashOverlay(visible = state.showSelfieFlash)

      // Content overlay (HUD elements, buttons, etc. from parent)
      content()
    }
  }
}

@Composable
private fun FocusIndicator(
  focusPoint: Offset,
  modifier: Modifier = Modifier
) {
  val scale = remember { Animatable(1.5f) }
  val alpha = remember { Animatable(1f) }

  LaunchedEffect(focusPoint) {
    // Reset animations
    scale.snapTo(1.5f)
    alpha.snapTo(1f)

    // Animate scale down with spring
    launch {
      scale.animateTo(
        targetValue = 0.8f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessLow
        )
      )
    }

    // Fade out after delay
    launch {
      delay(400L)
      alpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 400)
      )
    }
  }

  Box(
    modifier = modifier
      .drawBehind {
        val radius = 40.dp.toPx() * scale.value
        drawCircle(
          color = Color.White.copy(alpha = alpha.value),
          radius = radius,
          center = focusPoint,
          style = Stroke(width = 2.dp.toPx())
        )
      }
  )
}

/**
 * White overlay used as a selfie flash for front camera photos.
 * Fades in quickly when shown, fades out when hidden.
 */
@Composable
private fun SelfieFlashOverlay(visible: Boolean) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(durationMillis = 100)),
    exit = fadeOut(animationSpec = tween(durationMillis = 200))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White.copy(alpha = 0.95f))
    )
  }
}

@Preview(name = "Phone", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CameraScreenPreview() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

@Preview(
  name = "Phone - Small",
  showBackground = true,
  backgroundColor = 0xFF000000,
  widthDp = 320,
  heightDp = 568
)
@Composable
private fun CameraScreenPreviewSmallPhone() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

@Preview(
  name = "Tablet",
  showBackground = true,
  backgroundColor = 0xFF000000,
  widthDp = 600,
  heightDp = 960
)
@Composable
private fun CameraScreenPreviewTablet() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

@Preview(
  name = "Landscape",
  showBackground = true,
  backgroundColor = 0xFF000000,
  widthDp = 840,
  heightDp = 400
)
@Composable
private fun CameraScreenPreviewLandscape() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

@Preview(
  name = "Foldable",
  showBackground = true,
  backgroundColor = 0xFF000000,
  widthDp = 673,
  heightDp = 841
)
@Composable
private fun CameraScreenPreviewFoldable() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}
