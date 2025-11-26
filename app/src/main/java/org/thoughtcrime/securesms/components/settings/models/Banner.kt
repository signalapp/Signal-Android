/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.models

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.DslBannerBinding
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

/**
 * Displays a banner to notify the user of certain state or action that needs to be taken.
 */
object Banner {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, DslBannerBinding::inflate))
  }

  class Model(
    @StringRes val textId: Int,
    @StringRes val actionId: Int,
    val onClick: () -> Unit
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return textId == newItem.textId && actionId == newItem.actionId
    }
  }

  private class ViewHolder(binding: DslBannerBinding) : BindingViewHolder<Model, DslBannerBinding>(binding) {
    override fun bind(model: Model) {
      binding.bannerText.setText(model.textId)
      binding.bannerAction.setText(model.actionId)
      binding.bannerAction.setOnClickListener { model.onClick() }
    }
  }
}

/**
 * Replicates the Banner DSL preference for use in compose components.
 */
@Composable
fun Banner(
  text: String,
  action: String,
  onActionClick: () -> Unit
) {
  OutlinedCard(
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(width = 1.dp, color = colorResource(R.color.signal_colorOutline_38)),
    modifier = Modifier
      .horizontalGutters()
      .fillMaxWidth()
  ) {
    Column {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(horizontal = 16.57.dp)
          .padding(top = 16.dp, bottom = 10.dp)
      )

      TextButton(
        onClick = onActionClick,
        modifier = Modifier
          .align(Alignment.End)
          .padding(horizontal = 8.dp)
      ) {
        Text(text = action)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(
      text = "Banner text will go here and probably be about something important",
      action = "Action",
      onActionClick = {}
    )
  }
}
