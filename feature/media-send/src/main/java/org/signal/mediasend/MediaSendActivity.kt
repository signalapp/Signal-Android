package org.signal.mediasend

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.preupload.PreUploadManager
import org.signal.mediasend.select.MediaSelectScreen

/**
 * Abstract base activity for the media sending flow.
 *
 * App-layer implementations must extend this class and provide:
 * - [preUploadCallback] — For pre-upload job management
 * - [repository] — For media validation, sending, and other app-layer operations
 * - UI slots: [CameraSlot], [TextStoryEditorSlot], [MediaSelectSlot], [ImageEditorSlot], [VideoEditorSlot], [SendSlot]
 *
 * The concrete implementation should be registered in the app's manifest.
 */
abstract class MediaSendActivity : FragmentActivity() {

  /** Pre-upload callback implementation for job management. */
  protected abstract val preUploadCallback: PreUploadManager.Callback

  /** Repository implementation for app-layer operations. */
  protected abstract val repository: MediaSendRepository

  /** Contract args extracted from intent. Available after super.onCreate(). */
  protected lateinit var contractArgs: MediaSendActivityContract.Args
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    contractArgs = MediaSendActivityContract.Args.fromIntent(intent)

    setContent {
      val viewModel by viewModels<MediaSendViewModel>(factoryProducer = {
        MediaSendViewModel.Factory(
          context = applicationContext,
          args = contractArgs,
          isMeteredFlow = MeteredConnectivity.isMetered(applicationContext),
          repository = repository,
          preUploadCallback = preUploadCallback
        )
      })

      val state by viewModel.state.collectAsStateWithLifecycle()
      val backStack = rememberNavBackStack(
        if (state.isCameraFirst) MediaSendNavKey.Capture.Camera else MediaSendNavKey.Select
      )

      Theme {
        Surface {
          MediaSendNavDisplay(
            state = state,
            backStack = backStack,
            callback = viewModel,
            modifier = Modifier.fillMaxSize(),
            cameraSlot = { CameraSlot() },
            textStoryEditorSlot = { TextStoryEditorSlot() },
            mediaSelectSlot = {
              MediaSelectScreen(
                state = state,
                backStack = backStack,
                callback = viewModel
              )
            },
            videoEditorSlot = { VideoEditorSlot() },
            sendSlot = { SendSlot() }
          )
        }
      }
    }
  }

  /** Theme wrapper */
  @Composable
  protected open fun Theme(content: @Composable () -> Unit) {
    SignalTheme(incognitoKeyboardEnabled = false) {
      content()
    }
  }

  /** Camera capture UI slot. */
  @Composable
  protected abstract fun CameraSlot()

  /** Text story editor UI slot. */
  @Composable
  protected abstract fun TextStoryEditorSlot()

  /** Video editor UI slot. */
  @Composable
  protected abstract fun VideoEditorSlot()

  /** Send/review UI slot. */
  @Composable
  protected abstract fun SendSlot()

  companion object {
    /**
     * Creates an intent for a concrete [MediaSendActivity] subclass.
     *
     * @param context The context.
     * @param activityClass The concrete activity class to launch.
     * @param args The activity arguments.
     */
    fun <T : MediaSendActivity> createIntent(
      context: Context,
      activityClass: Class<T>,
      args: MediaSendActivityContract.Args = MediaSendActivityContract.Args()
    ): Intent {
      return Intent(context, activityClass).apply {
        putExtra(MediaSendActivityContract.EXTRA_ARGS, args)
      }
    }
  }
}
