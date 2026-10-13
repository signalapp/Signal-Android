/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.camera.CameraScreenState
import org.signal.camera.test.TestTags

/**
 * Covers what the HUD puts up for a given camera: which of the gallery, the lock and the pause holds the corner beside
 * the capture button, what a held recording takes out of reach, and what each control asks the camera for.
 *
 * The window is pinned taller than 16:9 on purpose. Robolectric's default resolves to the one display the zoom bar has
 * no room on, which would put half of this out of reach for the wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h760dp")
class StandardCameraHudTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<StandardCameraHudEvents>()

  private var longPressTimeoutMillis = 0L

  private val pausedLabel: String
    get() = RuntimeEnvironment.getApplication().getString(PAUSED_LABEL_RES)

  //region What holds the corner beside the capture button

  @Test
  fun `Given nothing is being recorded, when displayed, then the gallery holds the corner`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_GALLERY_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_LOCK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_PAUSE_BUTTON).assertDoesNotExist()
  }

  /** The lock has to be within reach of the finger holding the recording open, which is where the gallery was. */
  @Test
  fun `Given a recording that is being held, when displayed, then the lock holds the corner`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_LOCK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_GALLERY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun `Given a recording that is locked, when displayed, then the pause holds the corner`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_PAUSE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_LOCK_BUTTON).assertDoesNotExist()
  }

  //endregion

  //region What a held recording takes out of reach

  @Test
  fun `Given a recording that is being held, when displayed, then the chrome around it cannot be used`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CLOSE_BUTTON).assertIsNotEnabled()
  }

  /** A locked recording leaves the hand free, so nothing but the flash and the camera switch has to be taken away. */
  @Test
  fun `Given a recording that is locked, when displayed, then the chrome around it can still be used`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CLOSE_BUTTON).assertIsEnabled()
  }

  /** The flash is for a photo the camera is not going to take, however the recording was started. */
  @Test
  fun `Given a recording, when displayed, then the flash is gone`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).assertDoesNotExist()
  }

  @Test
  fun `Given a recording that is being held, when displayed, then the flash is gone`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).assertDoesNotExist()
  }

  /** The camera cannot be swapped out from under a running recording, however that recording was started. */
  @Test
  fun `Given a recording, when displayed, then the camera switch is gone`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).assertDoesNotExist()
  }

  @Test
  fun `Given a recording that is being held, when displayed, then the camera switch is gone`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).assertDoesNotExist()
  }

  @Test
  fun `Given a recording that is being held, when displayed, then the zoom bar cannot be used`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithText("2").assertIsNotEnabled()
  }

  @Test
  fun `Given a recording that is locked, when displayed, then the zoom bar can still be used`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithText("2").assertIsEnabled()
  }

  //endregion

  //region What the controls ask for

  @Test
  fun `Given photo mode, when the capture button is tapped, then a photo is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.PhotoCaptureTriggered)
  }

  /** In video mode a tap records rather than takes a photo, and what it starts needs no holding. */
  @Test
  fun `Given video mode, when the capture button is tapped, then a recording that needs no holding is asked for`() {
    setContent(captureButtonMode = CaptureButtonMode.VIDEO)

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.VideoCaptureStarted(isLocked = true))
  }

  /**
   * A recording a tap has asked for is not yet one the camera reports as running, so a hold that arrives in that window
   * is turned away. Were it not, lifting the finger would go on to stop the recording the tap started.
   */
  @Test
  fun `Given a tap has asked for a recording, when a hold arrives before it starts, then nothing is asked to stop`() {
    setContent(captureButtonMode = CaptureButtonMode.VIDEO)

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performClick()
    holdAndRelease()

    assertThat(events).containsExactly(StandardCameraHudEvents.VideoCaptureStarted(isLocked = true))
  }

  @Test
  fun `Given video mode, when the capture button is held and released, then a held recording is asked for and stopped`() {
    setContent(captureButtonMode = CaptureButtonMode.VIDEO)

    holdAndRelease()

    assertThat(events).containsExactly(
      StandardCameraHudEvents.VideoCaptureStarted(isLocked = false),
      StandardCameraHudEvents.VideoCaptureStopped
    )
  }

  @Test
  fun `Given a recording that is locked, when the capture button is tapped, then it is asked to stop`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.VideoCaptureStopped)
  }

  @Test
  fun `Given a recording that is locked, when the pause is clicked, then the pause is asked for`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_PAUSE_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.RecordingPauseToggled)
  }

  @Test
  fun `when the gallery is clicked, then it is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_GALLERY_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.GalleryClick)
  }

  @Test
  fun `when the close is clicked, then leaving is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CLOSE_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.CloseClick)
  }

  @Test
  fun `when the flash is clicked, then the next flash mode is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.ToggleFlash)
  }

  /** Sliding onto the lock is what takes it up, so a tap finds nothing there. */
  @Test
  fun `Given a recording that is being held, when the lock is clicked, then nothing is asked for`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_LOCK_BUTTON).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `when the camera switch is clicked, then the other camera is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.SwitchCamera)
  }

  @Test
  fun `when a zoom level is picked, then the camera is sent to it`() {
    setContent()

    composeTestRule.onNodeWithText("2").performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.SetZoomRatio(2f))
  }

  /** Nothing the chrome offers can be reached while a recording is held, so none of its events go out. */
  @Test
  fun `Given a recording that is being held, when the chrome is clicked, then nothing is asked for`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CLOSE_BUTTON).performClick()

    assertThat(events).isEmpty()
  }

  //endregion

  //region The recording's own report

  @Test
  fun `Given nothing is being recorded, when displayed, then no duration is shown`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_DURATION).assertDoesNotExist()
  }

  @Test
  fun `Given a recording, when displayed, then how long it has run is shown`() {
    setContent(state = lockedRecording().copy(recordingDuration = 65_000L))

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_DURATION).assertIsDisplayed()
    composeTestRule.onNodeWithText("01:05").assertIsDisplayed()
  }

  @Test
  fun `Given a recording that is running, when displayed, then nothing says it is paused`() {
    setContent(state = lockedRecording(), stringResources = pausedStringResources())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_PAUSED).assertDoesNotExist()
  }

  @Test
  fun `Given a paused recording, when displayed, then the caller's word for it is shown below the time`() {
    setContent(state = pausedRecording().copy(recordingDuration = 12_000L), stringResources = pausedStringResources())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_DURATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_PAUSED).assertIsDisplayed()
    composeTestRule.onNodeWithText(pausedLabel).assertIsDisplayed()
    composeTestRule.onNodeWithText("00:12").assertIsDisplayed()
  }

  /** A caller that leaves the label out gets the time alone rather than an empty pill under it. */
  @Test
  fun `Given a paused recording and a caller with no word for it, when displayed, then only the time is shown`() {
    setContent(state = pausedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_DURATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_RECORDING_PAUSED).assertDoesNotExist()
  }

  //endregion

  //region The same controls on a window too large for the bottom bar

  /**
   * Anything larger than a portrait phone runs the controls down the side and puts the flash and the switch together in
   * a pill. They are the same two controls either way, so the same tags find them.
   */
  @Test
  @Config(qualifiers = "w840dp-h1000dp")
  fun `Given a window too large for the bottom bar, when displayed, then the same controls are up`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_GALLERY_BUTTON).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w840dp-h1000dp")
  fun `Given a window too large for the bottom bar, when the pill is used, then it asks what the bottom bar would`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).performClick()

    assertThat(events).containsExactly(StandardCameraHudEvents.ToggleFlash, StandardCameraHudEvents.SwitchCamera)
  }

  /** Neither of the two has anything to offer a running recording, so the pill goes rather than empties. */
  @Test
  @Config(qualifiers = "w840dp-h1000dp")
  fun `Given a window too large for the bottom bar, when a recording runs, then the pill is gone`() {
    setContent(state = lockedRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "w840dp-h1000dp")
  fun `Given a window too large for the bottom bar, when a recording is held, then the pill is gone`() {
    setContent(state = heldRecording())

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_FLASH_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_SWITCH_BUTTON).assertDoesNotExist()
  }

  //endregion

  //region How long a recording is let run for

  @Test
  fun `Given a recording that has run as long as it is allowed, when displayed, then it is asked to stop`() {
    setContent(state = lockedRecording().copy(recordingDuration = MAX_DURATION), maxRecordingDurationMs = MAX_DURATION)

    assertThat(events).containsExactly(StandardCameraHudEvents.VideoCaptureStopped)
  }

  @Test
  fun `Given a recording with time left to run, when displayed, then it is left alone`() {
    setContent(state = lockedRecording().copy(recordingDuration = MAX_DURATION - 1), maxRecordingDurationMs = MAX_DURATION)

    assertThat(events).isEmpty()
  }

  /** A limit of zero is no limit, which is what a caller that does not cap the length passes. */
  @Test
  fun `Given no limit on the length, when a recording runs past where a limit would be, then it is left alone`() {
    setContent(state = lockedRecording().copy(recordingDuration = MAX_DURATION), maxRecordingDurationMs = 0L)

    assertThat(events).isEmpty()
  }

  //endregion

  private fun heldRecording() = CameraScreenState(
    isRecording = true,
    isRecordingLocked = false,
    zoomRange = ZOOM_RANGE
  )

  private fun lockedRecording() = CameraScreenState(
    isRecording = true,
    isRecordingLocked = true,
    zoomRange = ZOOM_RANGE
  )

  private fun pausedRecording() = lockedRecording().copy(isRecordingPaused = true)

  private fun pausedStringResources() = StringResources(recordingPaused = PAUSED_LABEL_RES)

  private fun setContent(
    state: CameraScreenState = CameraScreenState(zoomRange = ZOOM_RANGE),
    captureButtonMode: CaptureButtonMode = CaptureButtonMode.PHOTO,
    maxRecordingDurationMs: Long = 0L,
    stringResources: StringResources = StringResources()
  ) {
    composeTestRule.setContent {
      longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis

      Box(modifier = Modifier.fillMaxSize()) {
        StandardCameraHud(
          state = state,
          emitter = { events += it },
          captureButtonMode = captureButtonMode,
          maxRecordingDurationMs = maxRecordingDurationMs,
          stringResources = stringResources
        )
      }
    }

    composeTestRule.waitForIdle()
  }

  private fun holdAndRelease() {
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(longPressTimeoutMillis + 100L)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput { up() }
    composeTestRule.waitForIdle()
  }

  companion object {
    /** A lens that reaches every level, so that the zoom bar has something to put up. */
    private val ZOOM_RANGE = 0.5f..10f

    private const val MAX_DURATION = 30_000L

    /** The camera module carries no strings of its own, so a framework one stands in for the caller's label. */
    private val PAUSED_LABEL_RES = android.R.string.ok
  }
}
