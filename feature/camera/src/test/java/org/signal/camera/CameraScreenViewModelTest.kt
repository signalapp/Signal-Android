/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CameraScreenViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val mockCameraProvider: ProcessCameraProvider = mockk(relaxed = true)
  private val mockLifecycleOwner: LifecycleOwner = mockk(relaxed = true)
  private val mockSurfaceProvider: Preview.SurfaceProvider = mockk(relaxed = true)
  private val mockContext: Context = mockk(relaxed = true)
  private val mockCameraControl: CameraControl = mockk(relaxed = true)
  private val mockCameraInfo: CameraInfo = mockk(relaxed = true)

  /** A real LiveData rather than a mock, so the view model's observer runs the way it does on a device. */
  private val zoomStateLiveData = MutableLiveData<ZoomState>()
  private val mockCamera: Camera = mockk(relaxed = true)

  private lateinit var viewModel: CameraScreenViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    CameraDependencies.init(
      RuntimeEnvironment.getApplication(),
      object : CameraDependencies.Provider {
        override fun isStoriesFeatureEnabled(): Boolean = false
      }
    )
    viewModel = CameraScreenViewModel()

    every { mockCamera.cameraControl } returns mockCameraControl
    every { mockCamera.cameraInfo } returns mockCameraInfo
    every { mockCameraInfo.zoomState } returns zoomStateLiveData

    every { mockCameraProvider.bindToLifecycle(any(), any(), *anyVararg()) } returns mockCamera
    every { mockCameraProvider.unbindAll() } just Runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private fun bindCamera(
    captureMode: CameraCaptureMode = CameraCaptureMode.ImageAndVideoSimultaneous,
    enableQrScanning: Boolean = false
  ) = viewModel.onEvent(
    CameraScreenEvents.BindCamera(
      lifecycleOwner = mockLifecycleOwner,
      cameraProvider = mockCameraProvider,
      surfaceProvider = mockSurfaceProvider,
      context = RuntimeEnvironment.getApplication(),
      captureMode = captureMode,
      enableQrScanning = enableQrScanning
    )
  )

  /**
   * Installs a [bindToLifecycle] mock that throws for the first [failCount] calls and succeeds
   * thereafter, capturing the use cases passed in each call into the returned list.
   * Each entry in the returned list is the set of use cases passed in that attempt.
   */
  private fun captureBindingAttempts(failCount: Int = 0): MutableList<List<Any?>> {
    val captured = mutableListOf<List<Any?>>()
    var attempts = 0
    every { mockCameraProvider.bindToLifecycle(any(), any(), *anyVararg()) } answers {
      captured.add(useCasesFromArgs(args))
      if (++attempts <= failCount) throw RuntimeException("Cannot bind use cases") else mockCamera
    }
    return captured
  }

  /**
   * Extracts the use case arguments from a [bindToLifecycle] MockK `args` list.
   * Handles both Kotlin-style (individual elements) and Java-style (boxed array) varargs.
   */
  private fun useCasesFromArgs(args: List<Any?>): List<Any?> {
    val varargPart = args.drop(2)
    return if (varargPart.size == 1 && varargPart[0] is Array<*>) {
      (varargPart[0] as Array<*>).toList()
    } else {
      varargPart
    }
  }

  private fun List<Any?>.hasVideoCapture() = any { it is VideoCapture<*> }
  private fun List<Any?>.hasImageAnalysis() = any { it is ImageAnalysis }
  private fun List<Any?>.imageCapture() = filterIsInstance<ImageCapture>().firstOrNull()

  private fun setupZoomState(minZoom: Float, maxZoom: Float, zoomRatio: Float = 1f) {
    zoomStateLiveData.value = mockk<ZoomState>().also {
      every { it.minZoomRatio } returns minZoom
      every { it.maxZoomRatio } returns maxZoom
      every { it.zoomRatio } returns zoomRatio
    }
  }

  // ===========================================================================
  // BindCamera — first attempt succeeds
  // ===========================================================================

  @Test
  fun `binding with all use cases binds video and QR on the first attempt`() {
    val attempts = captureBindingAttempts()

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = true)

    assertThat(attempts.size).isEqualTo(1)
    assertThat(attempts[0].hasVideoCapture()).isTrue()
    assertThat(attempts[0].hasImageAnalysis()).isTrue()
  }

  @Test
  fun `binding with no optional use cases binds only preview and image capture`() {
    val attempts = captureBindingAttempts(failCount = 0)

    bindCamera(captureMode = CameraCaptureMode.ImageOnly, enableQrScanning = false)

    assertThat(attempts.size).isEqualTo(1)
    assertThat(attempts[0].hasVideoCapture()).isFalse()
    assertThat(attempts[0].hasImageAnalysis()).isFalse()
  }

  // ===========================================================================
  // BindCamera — fallback when device cannot bind all use cases simultaneously
  // ===========================================================================

  @Test
  fun `when first attempt fails with video and QR, second attempt drops video but keeps QR`() {
    val attempts = captureBindingAttempts(failCount = 1)

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = true)

    assertThat(attempts.size).isEqualTo(2)
    assertThat(attempts[0].hasVideoCapture()).isTrue()
    assertThat(attempts[0].hasImageAnalysis()).isTrue()
    assertThat(attempts[1].hasVideoCapture()).isFalse()
    assertThat(attempts[1].hasImageAnalysis()).isTrue()
  }

  @Test
  fun `when first two attempts fail with video and QR, third attempt drops both`() {
    val attempts = captureBindingAttempts(failCount = 2)

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = true)

    assertThat(attempts.size).isEqualTo(3)
    assertThat(attempts[2].hasVideoCapture()).isFalse()
    assertThat(attempts[2].hasImageAnalysis()).isFalse()
  }

  @Test
  fun `when all attempts fail, all three use case combinations are tried`() {
    val attempts = captureBindingAttempts(failCount = Int.MAX_VALUE)

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = true)

    assertThat(attempts.size).isEqualTo(3)
  }

  @Test
  fun `with only video requested, fallback drops video and nothing else`() {
    val attempts = captureBindingAttempts(failCount = Int.MAX_VALUE)

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = false)

    assertThat(attempts.size).isEqualTo(2)
    assertThat(attempts[0].hasVideoCapture()).isTrue()
    assertThat(attempts[1].hasVideoCapture()).isFalse()
    assertThat(attempts[1].hasImageAnalysis()).isFalse()
  }

  @Test
  fun `with only QR requested, fallback drops QR and nothing else`() {
    val attempts = captureBindingAttempts(failCount = Int.MAX_VALUE)

    bindCamera(captureMode = CameraCaptureMode.ImageOnly, enableQrScanning = true)

    assertThat(attempts.size).isEqualTo(2)
    assertThat(attempts[0].hasImageAnalysis()).isTrue()
    assertThat(attempts[1].hasImageAnalysis()).isFalse()
    assertThat(attempts[1].hasVideoCapture()).isFalse()
  }

  @Test
  fun `each failed binding attempt calls unbindAll before retrying`() {
    captureBindingAttempts(failCount = 2)

    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = true)

    // unbindAll called once before each of the 3 attempts
    verify(exactly = 3) { mockCameraProvider.unbindAll() }
  }

  // ===========================================================================
  // Limited binding mode — on-demand video rebind for recording
  // ===========================================================================

  @Test
  fun `when video was dropped during initial binding, startRecording rebinds with video`() {
    // Initial bind: first attempt (with video) fails, second (without) succeeds → limited mode
    captureBindingAttempts(failCount = 1)
    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = false)

    val postInitAttempts = captureBindingAttempts()

    try {
      viewModel.startRecording(mockContext, VideoOutput.FileOutput(File.createTempFile("video", ".mp4")), onVideoCaptured = {})
    } catch (_: Exception) {
      // Recording internals may not work fully in the test environment
    }

    assertThat(postInitAttempts.size).isGreaterThan(0)
    assertThat(postInitAttempts[0].hasVideoCapture()).isTrue()
  }

  @Test
  fun `in normal binding mode, startRecording does not rebind`() {
    captureBindingAttempts()
    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = false)

    val postInitAttempts = captureBindingAttempts()

    try {
      viewModel.startRecording(mockContext, VideoOutput.FileOutput(File.createTempFile("video", ".mp4")), onVideoCaptured = {})
    } catch (_: Exception) {
      // Recording internals may not work fully in the test environment
    }

    assertThat(postInitAttempts).isEmpty()
  }

  @Test
  fun `when the video rebind fails, restores the last successful use case set`() {
    captureBindingAttempts(failCount = 1)
    bindCamera(captureMode = CameraCaptureMode.ImageAndVideoSimultaneous, enableQrScanning = false)

    // Both the failed video rebind and the restore attempt are captured here
    val postInitAttempts = captureBindingAttempts(failCount = Int.MAX_VALUE)

    try {
      viewModel.startRecording(mockContext, VideoOutput.FileOutput(File.createTempFile("video", ".mp4")), onVideoCaptured = {})
    } catch (_: Exception) {
      // Expected — video rebind threw, which triggers the restore path
    }

    // Call 1: rebindForVideoCapture (with video), call 2: rebindToLastSuccessfulAttempt (without video)
    assertThat(postInitAttempts.size).isGreaterThan(1)
    assertThat(postInitAttempts[0].hasVideoCapture()).isTrue()
    assertThat(postInitAttempts[1].hasVideoCapture()).isFalse()
  }

  // ===========================================================================
  // Flash mode
  // ===========================================================================

  @Test
  fun `SetFlashMode updates state to the given mode`() {
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.On))

    assertThat(viewModel.state.value.flashMode).isEqualTo(FlashMode.On)
  }

  @Test
  fun `SetFlashMode to Auto updates state accordingly`() {
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.Auto))

    assertThat(viewModel.state.value.flashMode).isEqualTo(FlashMode.Auto)
  }

  @Test
  fun `NextFlashMode cycles Off to On`() {
    // Default flash mode is Off
    viewModel.onEvent(CameraScreenEvents.NextFlashMode)

    assertThat(viewModel.state.value.flashMode).isEqualTo(FlashMode.On)
  }

  @Test
  fun `NextFlashMode cycles On to Auto`() {
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.On))
    viewModel.onEvent(CameraScreenEvents.NextFlashMode)

    assertThat(viewModel.state.value.flashMode).isEqualTo(FlashMode.Auto)
  }

  @Test
  fun `NextFlashMode cycles Auto back to Off`() {
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.Auto))
    viewModel.onEvent(CameraScreenEvents.NextFlashMode)

    assertThat(viewModel.state.value.flashMode).isEqualTo(FlashMode.Off)
  }

  @Test
  fun `rebinding after the flash mode was set carries that flash mode onto the new ImageCapture`() {
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.On))

    // A camera switch or a re-entry into the camera rebinds, building a brand new ImageCapture.
    val attempts = captureBindingAttempts()
    bindCamera()

    assertThat(attempts.last().imageCapture()?.flashMode).isEqualTo(ImageCapture.FLASH_MODE_ON)
  }

  @Test
  fun `binding with the default flash mode leaves the ImageCapture flash off`() {
    val attempts = captureBindingAttempts()
    bindCamera()

    assertThat(attempts.last().imageCapture()?.flashMode).isEqualTo(ImageCapture.FLASH_MODE_OFF)
  }

  // ===========================================================================
  // Camera switching
  // ===========================================================================

  @Test
  fun `SwitchCamera toggles lens facing from back to front`() {
    // Default is LENS_FACING_BACK
    viewModel.onEvent(CameraScreenEvents.SwitchCamera(mockContext))

    assertThat(viewModel.state.value.lensFacing).isEqualTo(CameraSelector.LENS_FACING_FRONT)
  }

  @Test
  fun `SwitchCamera toggles lens facing from front to back`() {
    viewModel.setLensFacing(CameraSelector.LENS_FACING_FRONT)
    viewModel.onEvent(CameraScreenEvents.SwitchCamera(mockContext))

    assertThat(viewModel.state.value.lensFacing).isEqualTo(CameraSelector.LENS_FACING_BACK)
  }

  // ===========================================================================
  // Capture errors
  // ===========================================================================

  @Test
  fun `capturePhoto without a bound camera sets a PhotoCaptureFailed error`() {
    // No bindCamera() call → imageCapture is null
    viewModel.capturePhoto(mockContext) {}

    assertThat(viewModel.state.value.captureError)
      .isNotNull()
      .isInstanceOf(CaptureError.PhotoCaptureFailed::class)
  }

  @Test
  fun `ClearCaptureError removes an existing error from state`() {
    // Plant an error by calling capturePhoto without a bound camera
    viewModel.capturePhoto(mockContext) {}
    assertThat(viewModel.state.value.captureError).isNotNull()

    viewModel.onEvent(CameraScreenEvents.ClearCaptureError)

    assertThat(viewModel.state.value.captureError).isNull()
  }

  // ===========================================================================
  // Selfie flash
  // ===========================================================================

  @Test
  fun `capturePhoto on front camera with flash On activates selfie flash`() {
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.On))
    viewModel.setLensFacing(CameraSelector.LENS_FACING_FRONT)

    viewModel.capturePhoto(mockContext) {}

    // showSelfieFlash is set synchronously before the coroutine delay
    assertThat(viewModel.state.value.showSelfieFlash).isTrue()
  }

  @Test
  fun `capturePhoto on back camera with flash On does not activate selfie flash`() {
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetFlashMode(FlashMode.On))
    // lensFacing stays LENS_FACING_BACK (the default)

    viewModel.capturePhoto(mockContext) {}

    assertThat(viewModel.state.value.showSelfieFlash).isEqualTo(false)
  }

  // ===========================================================================
  // Tap-to-focus
  // ===========================================================================

  @Test
  fun `TapToFocus without a bound camera does not update state`() {
    val stateBefore = viewModel.state.value

    viewModel.onEvent(
      CameraScreenEvents.TapToFocus(
        viewX = 100f,
        viewY = 200f,
        surfaceX = 50f,
        surfaceY = 100f,
        surfaceWidth = 200f,
        surfaceHeight = 400f
      )
    )

    assertThat(viewModel.state.value).isEqualTo(stateBefore)
  }

  @Test
  fun `TapToFocus with a bound camera updates focusPoint and shows the focus indicator`() {
    bindCamera()

    viewModel.onEvent(
      CameraScreenEvents.TapToFocus(
        viewX = 100f,
        viewY = 200f,
        surfaceX = 50f,
        surfaceY = 100f,
        surfaceWidth = 200f,
        surfaceHeight = 400f
      )
    )

    assertThat(viewModel.state.value.focusPoint).isEqualTo(Offset(100f, 200f))
    assertThat(viewModel.state.value.showFocusIndicator).isTrue()
  }

  @Test
  fun `TapToFocus with a bound camera calls startFocusAndMetering on camera control`() {
    bindCamera()

    viewModel.onEvent(
      CameraScreenEvents.TapToFocus(
        viewX = 100f,
        viewY = 200f,
        surfaceX = 50f,
        surfaceY = 100f,
        surfaceWidth = 200f,
        surfaceHeight = 400f
      )
    )

    verify { mockCameraControl.startFocusAndMetering(any()) }
  }

  // ===========================================================================
  // Zoom bar animation
  // ===========================================================================

  @Test
  fun `SetZoomRatio does not arrive at the level the moment it is asked for`() {
    setupZoomState(minZoom = 1f, maxZoom = 16f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(4f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(1f)
  }

  @Test
  fun `SetZoomRatio is on its way to the level partway through`() {
    setupZoomState(minZoom = 1f, maxZoom = 16f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(4f))
    testDispatcher.scheduler.advanceTimeBy(HALFWAY_MS)

    val zoomRatio = viewModel.state.value.zoomRatio
    assertThat(zoomRatio).isGreaterThan(1f)
    assertThat(zoomRatio < 4f).isTrue()
  }

  @Test
  fun `SetZoomRatio finishes on the level exactly`() {
    setupZoomState(minZoom = 1f, maxZoom = 16f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(4f))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  /**
   * Two animations the same distance apart in ratio terms cover the same fraction of that distance in the same time,
   * which is what stops a journey to a far level tearing away at the start. It holds for any easing curve, so this
   * asserts on the interpolation rather than on the curve.
   */
  @Test
  fun `SetZoomRatio covers ground evenly however far out the level is`() {
    setupZoomState(minZoom = 1f, maxZoom = 16f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(4f))
    testDispatcher.scheduler.advanceTimeBy(HALFWAY_MS)
    val reachedFromOne = viewModel.state.value.zoomRatio / 1f

    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(16f))
    testDispatcher.scheduler.advanceTimeBy(HALFWAY_MS)
    val reachedFromFour = viewModel.state.value.zoomRatio / 4f

    assertThat(reachedFromOne).isCloseTo(reachedFromFour, RATIO_TOLERANCE)
  }

  @Test
  fun `Given a travel under way, when the lens is pinched, then the pinch takes it over`() {
    setupZoomState(minZoom = 1f, maxZoom = 16f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(16f))
    testDispatcher.scheduler.advanceTimeBy(HALFWAY_MS)
    val reached = viewModel.state.value.zoomRatio

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 2f))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.zoomRatio).isEqualTo((reached * 2f).coerceAtMost(16f))
  }

  // ===========================================================================
  // Pinch zoom
  // ===========================================================================

  @Test
  fun `PinchZoom without a bound camera does not change zoom ratio`() {
    val initialZoom = viewModel.state.value.zoomRatio

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 2f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(initialZoom)
  }

  @Test
  fun `PinchZoom scales the current zoom ratio by the given factor`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 3f))

    // 1f * 3f = 3f, clamped to [1f, 10f] = 3f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(3f)
  }

  @Test
  fun `PinchZoom clamps zoom ratio to the camera maximum`() {
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 10f))

    // 1f * 10f = 10f, clamped to [1f, 4f] = 4f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  @Test
  fun `PinchZoom clamps zoom ratio to the camera minimum`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 0.1f))

    // 1f * 0.1f = 0.1f, clamped to [1f, 10f] = 1f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(1f)
  }

  @Test
  fun `PinchZoom calls setZoomRatio on camera control with the new ratio`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.PinchZoom(zoomFactor = 2f))

    verify { mockCameraControl.setZoomRatio(2f) }
  }

  // ===========================================================================
  // Zoom ratio (used by the zoom bar)
  // ===========================================================================

  @Test
  fun `SetZoomRatio without a bound camera does not change zoom ratio`() {
    val initialZoom = viewModel.state.value.zoomRatio

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(2f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(initialZoom)
  }

  @Test
  fun `SetZoomRatio travels to the given ratio`() {
    setupZoomState(minZoom = 0.5f, maxZoom = 10f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(5f))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(5f)
    verify { mockCameraControl.setZoomRatio(5f) }
  }

  @Test
  fun `SetZoomRatio clamps to what the lens can reach`() {
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(5f))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  /** Otherwise a drag after a level was picked would carry on from where the recording started instead. */
  @Test
  fun `Given a ratio was set, when dragged from there, then the drag carries on from it`() {
    setupZoomState(minZoom = 1f, maxZoom = 5f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(2f))
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.onEvent(CameraScreenEvents.LinearZoom(0.5f))

    // Half way in from a base of 2f, rather than the 3f that half way in from 1f would have reached.
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(3.5f)
  }

  // ===========================================================================
  // Locking a running recording
  // ===========================================================================

  /** A lock with no recording behind it would outlive the gesture that asked for it and apply to the next one. */
  @Test
  fun `Given nothing is being recorded, when the lock is asked for, then it is not taken`() {
    viewModel.onEvent(CameraScreenEvents.LockRecording)

    assertThat(viewModel.state.value.isRecordingLocked).isFalse()
  }

  // ===========================================================================
  // Pausing a running recording
  // ===========================================================================

  /** The screen goes by what the recorder reports, so a pause it never honored never shows. */
  @Test
  fun `Given nothing is being recorded, when the pause is asked for, then nothing reads as paused`() {
    viewModel.onEvent(CameraScreenEvents.ToggleRecordingPaused)

    assertThat(viewModel.state.value.isRecordingPaused).isFalse()
  }

  // ===========================================================================
  // Zoom range
  // ===========================================================================

  @Test
  fun `Given a camera that reports its zoom, when bound, then its reach is published`() {
    setupZoomState(minZoom = 0.5f, maxZoom = 10f)

    bindCamera()

    assertThat(viewModel.state.value.zoomRange).isEqualTo(0.5f..10f)
  }

  /** A camera that has not reported yet leaves the range at a single point, which reads as a lens that cannot zoom. */
  @Test
  fun `Given a camera that has not reported its zoom, when bound, then the range stays at a single point`() {
    bindCamera()

    assertThat(viewModel.state.value.zoomRange).isEqualTo(1f..1f)
  }

  @Test
  fun `Given nothing has been bound, when asked, then the range is a single point`() {
    assertThat(viewModel.state.value.zoomRange).isEqualTo(1f..1f)
  }

  /** A camera reports its zoom when it is ready rather than by the time it is bound, so a late report still counts. */
  @Test
  fun `Given a camera that reports its zoom after binding, when it does, then its reach is published`() {
    bindCamera()
    assertThat(viewModel.state.value.zoomRange).isEqualTo(1f..1f)

    setupZoomState(minZoom = 0.5f, maxZoom = 10f)

    assertThat(viewModel.state.value.zoomRange).isEqualTo(0.5f..10f)
  }

  @Test
  fun `Given a lens whose reach changes, when a different one is bound, then the newer reach is published`() {
    setupZoomState(minZoom = 0.5f, maxZoom = 10f)
    bindCamera()

    setupZoomState(minZoom = 1f, maxZoom = 3f)

    assertThat(viewModel.state.value.zoomRange).isEqualTo(1f..3f)
  }

  /**
   * Coming back into the camera rebinds it, which drops the lens back to its default zoom, so the zoom bar goes back
   * with it rather than keeping a level highlighted over a preview that is no longer at it.
   */
  @Test
  fun `Given a lens that has been zoomed, when rebound, then the zoom is back at 1x`() {
    setupZoomState(minZoom = 1f, maxZoom = 8f)
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(5f))
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(5f)

    bindCamera()

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(1f)
  }

  /** The reset happens on the bind itself, so a lens reporting afterwards cannot pull it back off 1x. */
  @Test
  fun `Given a rebound lens, when it reports the zoom it was left at, then the zoom stays at 1x`() {
    setupZoomState(minZoom = 1f, maxZoom = 8f)
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(5f))
    testDispatcher.scheduler.advanceUntilIdle()

    bindCamera()
    setupZoomState(minZoom = 1f, maxZoom = 8f, zoomRatio = 5f)

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(1f)
  }

  /** The reset happens on the bind, so it cannot pull the lens back from wherever it has since been sent. */
  @Test
  fun `Given a bound lens that has been zoomed, when it reports again, then the zoom it was sent to stands`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f, zoomRatio = 1f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(4f))
    testDispatcher.scheduler.advanceUntilIdle()
    setupZoomState(minZoom = 1f, maxZoom = 8f, zoomRatio = 1f)

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  // ===========================================================================
  // Linear zoom (used during video recording)
  // ===========================================================================

  @Test
  fun `LinearZoom without a bound camera does not change zoom ratio`() {
    val initialZoom = viewModel.state.value.zoomRatio

    viewModel.onEvent(CameraScreenEvents.LinearZoom(0.5f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(initialZoom)
  }

  @Test
  fun `LinearZoom with positive value interpolates toward max zoom`() {
    // baseZoom = 1f (recordingStartZoomRatio default), min = 1f, max = 4f
    // 0.5f → 1f + (4f - 1f) * 0.5f = 2.5f
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.LinearZoom(0.5f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(2.5f)
  }

  @Test
  fun `LinearZoom with negative value interpolates toward min zoom`() {
    // baseZoom = 1f, min = 0.5f, max = 4f
    // -0.5f → 1f + (1f - 0.5f) * (-0.5f) = 0.75f
    setupZoomState(minZoom = 0.5f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.LinearZoom(-0.5f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(0.75f)
  }

  @Test
  fun `LinearZoom at 1f zooms to maximum`() {
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.LinearZoom(1f))

    // 1f + (4f - 1f) * 1f = 4f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  @Test
  fun `LinearZoom at -1f zooms to minimum`() {
    setupZoomState(minZoom = 0.5f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.LinearZoom(-1f))

    // 1f + (1f - 0.5f) * (-1f) = 0.5f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(0.5f)
  }

  @Test
  fun `LinearZoom input is clamped to the -1 to 1 range`() {
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    // 2f is out of range, should be clamped to 1f → same result as LinearZoom(1f)
    viewModel.onEvent(CameraScreenEvents.LinearZoom(2f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(4f)
  }

  @Test
  fun `LinearZoom calls setZoomRatio on camera control`() {
    setupZoomState(minZoom = 1f, maxZoom = 4f)
    bindCamera()

    viewModel.onEvent(CameraScreenEvents.LinearZoom(0.5f))

    verify { mockCameraControl.setZoomRatio(2.5f) }
  }

  /**
   * A lens that reports a narrower reach can leave the base a drag works from behind it. The camera clamps what it is
   * sent, so the published ratio has to be clamped the same way or the zoom bar reads a level the lens is not at.
   */
  @Test
  fun `Given a drag base above what the lens can reach, when dragged in, then the zoom stays within the lens's reach`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f)
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(8f))
    testDispatcher.scheduler.advanceUntilIdle()

    setupZoomState(minZoom = 1f, maxZoom = 3f)
    viewModel.onEvent(CameraScreenEvents.LinearZoom(0.5f))

    assertThat(viewModel.state.value.zoomRatio).isEqualTo(3f)
  }

  /** Clamping the base as well as the result leaves the drag usable from the top of the range rather than pinned to it. */
  @Test
  fun `Given a drag base above what the lens can reach, when dragged out, then the drag works from the lens's maximum`() {
    setupZoomState(minZoom = 1f, maxZoom = 10f)
    bindCamera()
    viewModel.onEvent(CameraScreenEvents.SetZoomRatio(8f))
    testDispatcher.scheduler.advanceUntilIdle()

    setupZoomState(minZoom = 1f, maxZoom = 3f)
    viewModel.onEvent(CameraScreenEvents.LinearZoom(-0.5f))

    // Half the way out from a base clamped to 3f: 3f + (3f - 1f) * -0.5f
    assertThat(viewModel.state.value.zoomRatio).isEqualTo(2f)
  }

  private companion object {
    /** Far enough into an animation to be clear of both ends without having to know how long it runs for. */
    private const val HALFWAY_MS = 120L

    /** Two animations sampled a frame apart land a frame's worth of ratio apart, which is the tolerance needed. */
    private const val RATIO_TOLERANCE = 0.05f
  }
}
