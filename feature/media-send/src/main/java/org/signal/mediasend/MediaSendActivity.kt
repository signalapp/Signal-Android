package org.signal.mediasend

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Activity for the media sending flow.
 */
abstract class MediaSendActivity : FragmentActivity() {
  protected lateinit var contractArgs: MediaSendActivityContract.Args
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    contractArgs = MediaSendActivityContract.Args.fromIntent(intent)

    setContent {
      val viewModel by viewModels<MediaSendViewModel>(factoryProducer = {
        MediaSendViewModel.Factory(
          args = contractArgs,
          isMeteredFlow = MeteredConnectivity.isMetered(applicationContext)
        )
      })

      val state by viewModel.state.collectAsStateWithLifecycle()
      val backStack = rememberNavBackStack(
        if (state.isCameraFirst) MediaSendNavKey.Capture.Camera else MediaSendNavKey.Select
      )

      SignalTheme {
        Surface {
          MediaSendNavDisplay(
            state = state,
            backStack = backStack,
            callback = viewModel,
            modifier = Modifier.fillMaxSize(),
            cameraSlot = { },
            textStoryEditorSlot = { },
            videoEditorSlot = { },
            sendSlot = { }
          )
        }
      }
    }
  }

  companion object {
    /**
     * Creates an intent for [MediaSendActivity].
     *
     * @param context The context.
     * @param args The activity arguments.
     */
    fun createIntent(
      context: Context,
      args: MediaSendActivityContract.Args = MediaSendActivityContract.Args()
    ): Intent {
      return Intent(context, MediaSendActivity::class.java).apply {
        putExtra(MediaSendActivityContract.EXTRA_ARGS, args)
      }
    }
  }
}
