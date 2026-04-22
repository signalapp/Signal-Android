package org.thoughtcrime.securesms.components.settings.app.subscription.models

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.models.DSLComposePreference
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * DSL Ideal | Wero button for the payments gateway.
 */
object IdealWeroButton {

  @Stable
  class Model(val onClick: () -> Unit) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean = true
  }

  class ViewHolder(itemView: ComposeView) : DSLComposePreference.ViewHolder<Model>(itemView) {
    @Composable
    override fun Content(model: Model) {
      IdealWeroButton(model)
    }
  }

  fun register(adapter: MappingAdapter) {
    DSLComposePreference.register(adapter) { ViewHolder(it) }
  }
}

@Composable
private fun IdealWeroButton(model: IdealWeroButton.Model) {
  var enabled by remember { mutableStateOf(true) }

  Buttons.LargeTonal(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onClick = {
      enabled = false
      model.onClick()
    },
    enabled = enabled,
    modifier = Modifier
      .height(44.dp)
      .horizontalGutters()
      .fillMaxWidth(),
    colors = ButtonColors(
      containerColor = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimaryContainer),
      contentColor = colorResource(org.signal.core.ui.R.color.signal_light_colorOnPrimaryContainer),
      disabledContainerColor = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimaryContainer),
      disabledContentColor = colorResource(org.signal.core.ui.R.color.signal_light_colorOnPrimaryContainer)
    )
  ) {
    Image(
      imageVector = ImageVector.vectorResource(R.drawable.logo_ideal_wero),
      contentDescription = stringResource(R.string.GatewaySelectorBottomSheet__ideal_wero)
    )
  }
}

@DayNightPreviews
@Composable
private fun IdealWeroButtonPreview() {
  Previews.Preview {
    IdealWeroButton(model = remember { IdealWeroButton.Model(onClick = {}) })
  }
}
