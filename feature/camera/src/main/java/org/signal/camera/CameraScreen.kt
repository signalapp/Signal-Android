package org.signal.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import kotlin.time.Duration.Companion.milliseconds
import androidx.camera.core.Preview as CameraPreview

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
 * @param fillViewport When true, the viewfinder fills all available space, cropping the camera frame
 *    if necessary to avoid letterbox bars. Defaults to false (letterbox to a 9:16 / 16:9 aspect ratio).
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
  captureMode: CameraCaptureMode = CameraCaptureMode.ImageAndVideoSimultaneous,
  enableQrScanning: Boolean = false,
  fillViewport: Boolean = false,
  landscape: Boolean? = null,
  implementationMode: ImplementationMode = ImplementationMode.EXTERNAL,
  content: @Composable BoxScope.() -> Unit = {}
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val isInPreview = LocalInspectionMode.current

  var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

  // Bind once; a screen rotation rebuilds just the preview (below), not the whole camera.
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
        context = context,
        captureMode = captureMode,
        enableQrScanning = enableQrScanning
      )
    )
  }

  BoxWithConstraints(
    contentAlignment = contentAlignment,
    modifier = modifier.fillMaxSize()
  ) {
    // Use the caller's orientation when supplied so the viewport agrees with the card; else fall back to layout space.
    val landscapeLayout = landscape ?: (maxWidth > maxHeight)
    val aspectRatio = if (landscapeLayout) 16f / 9f else 9f / 16f

    val availableAspectRatio = maxWidth / maxHeight
    val matchHeightFirst = availableAspectRatio > aspectRatio

    val viewfinderBoxModifier = if (fillViewport) {
      Modifier.fillMaxSize()
    } else {
      Modifier.aspectRatio(aspectRatio, matchHeightConstraintsFirst = matchHeightFirst)
    }

    Box(
      modifier = viewfinderBoxModifier
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
        val coordinateTransformer = remember { MutableCoordinateTransformer() }
        val currentSurfaceRequest = surfaceRequest!!

        CameraXViewfinder(
          implementationMode = implementationMode,
          surfaceRequest = currentSurfaceRequest,
          coordinateTransformer = coordinateTransformer,
          contentScale = if (fillViewport) ContentScale.Crop else ContentScale.Fit,
          modifier = Modifier
            .fillMaxSize()
            .clip(cornerShape)
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  emitter(CameraScreenEvents.SwitchCamera(context))
                },
                onTap = { offset ->
                  val surfaceCoords = with(coordinateTransformer) { offset.transform() }
                  emitter(
                    CameraScreenEvents.TapToFocus(
                      viewX = offset.x,
                      viewY = offset.y,
                      surfaceX = surfaceCoords.x,
                      surfaceY = surfaceCoords.y,
                      surfaceWidth = currentSurfaceRequest.resolution.width.toFloat(),
                      surfaceHeight = currentSurfaceRequest.resolution.height.toFloat()
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
      delay(400.milliseconds)
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

@AllNightPreviews
@Composable
private fun CameraScreenPreview() {
  Previews.Preview {
    CameraScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}
