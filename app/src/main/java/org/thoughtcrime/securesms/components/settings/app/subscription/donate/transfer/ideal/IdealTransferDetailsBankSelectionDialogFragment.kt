/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeDialogFragment
import org.signal.core.ui.R as CoreUiR

/**
 * Dialog fragment for selecting the bank for the iDEAL donation.
 */
class IdealTransferDetailsBankSelectionDialogFragment : ComposeDialogFragment() {

  companion object {
    const val IDEAL_SELECTED_BANK = "ideal.selected.bank"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  @Composable
  override fun DialogContent() {
    BankSelectionContent(
      onNavigationClick = { findNavController().popBackStack() },
      onBankSelected = {
        dismissAllowingStateLoss()

        setFragmentResult(
          IDEAL_SELECTED_BANK,
          bundleOf(
            IDEAL_SELECTED_BANK to it.code
          )
        )
      }
    )
  }
}

@Preview
@Composable
private fun BankSelectionContentPreview() {
  BankSelectionContent(
    onNavigationClick = {},
    onBankSelected = {}
  )
}

@Composable
private fun BankSelectionContent(
  onNavigationClick: () -> Unit,
  onBankSelected: (IdealBank) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.IdealTransferDetailsBankSelectionDialogFragment__choose_your_bank),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_x_24)
  ) { paddingValues ->
    LazyColumn(modifier = Modifier.padding(paddingValues)) {
      items(IdealBank.entries.toTypedArray()) {
        val uiValues = it.getUIValues()

        Row(
          verticalAlignment = CenterVertically,
          modifier = Modifier
            .clickable { onBankSelected(it) }
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter), vertical = 8.dp)
        ) {
          Image(
            painter = painterResource(id = uiValues.icon),
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
          )

          Text(
            text = stringResource(uiValues.name),
            modifier = Modifier.padding(start = 24.dp)
          )
        }
      }
    }
  }
}
