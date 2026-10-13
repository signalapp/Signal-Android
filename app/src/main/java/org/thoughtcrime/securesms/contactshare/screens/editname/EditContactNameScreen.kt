/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.TextFields
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

private val HORIZONTAL_PADDING = 24.dp

@Composable
fun EditContactNameScreen(
  state: EditContactNameState,
  onEvent: (EditContactNameEvent) -> Unit
) {
  Scaffolds.Default(
    title = stringResource(R.string.EditContactNameScreen__edit_name),
    onNavigationClick = { onEvent(EditContactNameEvent.BackClicked) },
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24,
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .imePadding()
        .fillMaxSize()
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = HORIZONTAL_PADDING)
          .padding(top = 12.dp)
      ) {
        NameField(
          value = state.parts.prefix,
          testTag = EditContactNameTestTags.PREFIX_FIELD,
          labelRes = R.string.ContactNameEditActivity_prefix,
          onValueChange = { onEvent(EditContactNameEvent.PrefixChanged(it)) }
        )

        NameField(
          value = state.parts.givenName,
          testTag = EditContactNameTestTags.GIVEN_NAME_FIELD,
          labelRes = R.string.ContactNameEditActivity_given_name,
          onValueChange = { onEvent(EditContactNameEvent.GivenNameChanged(it)) }
        )

        NameField(
          value = state.parts.middleName,
          testTag = EditContactNameTestTags.MIDDLE_NAME_FIELD,
          labelRes = R.string.ContactNameEditActivity_middle_name,
          onValueChange = { onEvent(EditContactNameEvent.MiddleNameChanged(it)) }
        )

        NameField(
          value = state.parts.familyName,
          testTag = EditContactNameTestTags.FAMILY_NAME_FIELD,
          labelRes = R.string.ContactNameEditActivity_family_name,
          onValueChange = { onEvent(EditContactNameEvent.FamilyNameChanged(it)) }
        )

        NameField(
          value = state.parts.suffix,
          testTag = EditContactNameTestTags.SUFFIX_FIELD,
          labelRes = R.string.ContactNameEditActivity_suffix,
          imeAction = ImeAction.Done,
          onValueChange = { onEvent(EditContactNameEvent.SuffixChanged(it)) }
        )
      }

      Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = HORIZONTAL_PADDING, vertical = 12.dp)
      ) {
        Buttons.LargeTonal(
          enabled = state.canSave,
          onClick = { onEvent(EditContactNameEvent.SaveClicked) },
          modifier = Modifier.testTag(EditContactNameTestTags.DONE_BUTTON)
        ) {
          Text(text = stringResource(R.string.EditContactNameScreen__done))
        }
      }
    }
  }
}

@Composable
private fun NameField(
  value: String,
  labelRes: Int,
  testTag: String,
  onValueChange: (String) -> Unit,
  imeAction: ImeAction = ImeAction.Next
) {
  TextFields.TextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(text = stringResource(labelRes)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Words,
      imeAction = imeAction
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
  )
}

@DayNightPreviews
@Composable
private fun EditContactNameScreenPreview() {
  Previews.Preview {
    EditContactNameScreen(
      state = EditContactNameState(
        parts = ContactNameParts(givenName = "Paige", familyName = "Hall"),
        original = ContactNameParts(givenName = "Paige", familyName = "Hall")
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun EditContactNameScreenEditedPreview() {
  Previews.Preview {
    EditContactNameScreen(
      state = EditContactNameState(
        parts = ContactNameParts(givenName = "Paige"),
        original = ContactNameParts(givenName = "Paige", familyName = "Hall")
      ),
      onEvent = {}
    )
  }
}
