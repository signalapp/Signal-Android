package org.thoughtcrime.securesms.compose

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment

abstract class ComposeBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  protected open val forceDarkTheme = false

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val isDark = if (forceDarkTheme) {
          true
        } else {
          LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        SignalTheme(isDarkMode = isDark) {
          Surface(
            shape = RoundedCornerShape(cornerRadius.dp, cornerRadius.dp),
            color = SignalTheme.colors.colorSurface1,
            contentColor = MaterialTheme.colorScheme.onSurface
          ) {
            SheetContent()
          }
        }
      }
    }
  }

  @Composable
  abstract fun SheetContent()
}
