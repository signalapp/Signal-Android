/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

@VisibleForTesting
object MyStorySettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_ALL_SIGNAL_CONNECTIONS = "row-all-signal-connections"
  const val BUTTON_VIEW_ALL_SIGNAL_CONNECTIONS = "button-view-all-signal-connections"
  const val ROW_ALL_EXCEPT = "row-all-except"
  const val ROW_ONLY_SHARE_WITH = "row-only-share-with"
  const val TEXT_LEARN_MORE = "text-learn-more"
  const val ROW_REPLIES_AND_REACTIONS = "row-replies-and-reactions"
}

/**
 * Settings for who can view the user's My Story, and whether it accepts replies and reactions.
 */
@Composable
fun MyStorySettingsScreen(
  state: MyStorySettingsState,
  onEvent: (MyStorySettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val privacyMode = state.myStoryPrivacyState.privacyMode
  val connectionCount = state.myStoryPrivacyState.connectionCount

  Scaffolds.Settings(
    title = stringResource(R.string.MyStorySettingsFragment__my_story),
    onNavigationClick = { onEvent(MyStorySettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up),
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(MyStorySettingsTestTags.SCROLLER)
    ) {
      item {
        Texts.SectionHeader(text = stringResource(R.string.MyStorySettingsFragment__who_can_view_this_story))
      }

      item {
        AllSignalConnectionsRow(
          selected = state.selectedPrivacyMode == DistributionListPrivacyMode.ALL,
          count = state.allSignalConnectionsCount,
          onEvent = onEvent
        )
      }

      item {
        Rows.RadioRow(
          selected = state.selectedPrivacyMode == DistributionListPrivacyMode.ALL_EXCEPT,
          text = stringResource(R.string.MyStorySettingsFragment__all_except),
          label = if (privacyMode == DistributionListPrivacyMode.ALL_EXCEPT) {
            pluralStringResource(R.plurals.MyStorySettingsFragment__d_people_excluded, connectionCount, connectionCount)
          } else {
            stringResource(R.string.MyStorySettingsFragment__hide_your_story_from_specific_people)
          },
          modifier = Modifier
            .selectable(
              selected = state.selectedPrivacyMode == DistributionListPrivacyMode.ALL_EXCEPT,
              role = Role.RadioButton,
              onClick = { onEvent(MyStorySettingsEvent.AllExceptClicked) }
            )
            .testTag(MyStorySettingsTestTags.ROW_ALL_EXCEPT)
        )
      }

      item {
        Rows.RadioRow(
          selected = state.selectedPrivacyMode == DistributionListPrivacyMode.ONLY_WITH,
          text = stringResource(R.string.MyStorySettingsFragment__only_share_with),
          label = if (privacyMode == DistributionListPrivacyMode.ONLY_WITH) {
            pluralStringResource(R.plurals.MyStorySettingsFragment__d_people, connectionCount, connectionCount)
          } else {
            stringResource(R.string.MyStorySettingsFragment__only_share_with_selected_people)
          },
          modifier = Modifier
            .selectable(
              selected = state.selectedPrivacyMode == DistributionListPrivacyMode.ONLY_WITH,
              role = Role.RadioButton,
              onClick = { onEvent(MyStorySettingsEvent.OnlyShareWithClicked) }
            )
            .testTag(MyStorySettingsTestTags.ROW_ONLY_SHARE_WITH)
        )
      }

      item {
        LearnMoreText(onEvent = onEvent)
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.MyStorySettingsFragment__replies_amp_reactions))
      }

      item {
        Rows.ToggleRow(
          checked = state.areRepliesAndReactionsEnabled,
          text = stringResource(R.string.MyStorySettingsFragment__allow_replies_amp_reactions),
          label = stringResource(R.string.MyStorySettingsFragment__let_people_who_can_view_your_story_react_and_reply),
          onCheckChanged = { onEvent(MyStorySettingsEvent.RepliesAndReactionsToggled(it)) },
          modifier = Modifier.testTag(MyStorySettingsTestTags.ROW_REPLIES_AND_REACTIONS)
        )
      }
    }
  }
}

@Composable
private fun AllSignalConnectionsRow(
  selected: Boolean,
  count: Int,
  onEvent: (MyStorySettingsEvent) -> Unit
) {
  Rows.RadioRow(
    selected = selected,
    modifier = Modifier
      .selectable(
        selected = selected,
        role = Role.RadioButton,
        onClick = { onEvent(MyStorySettingsEvent.AllSignalConnectionsClicked) }
      )
      .testTag(MyStorySettingsTestTags.ROW_ALL_SIGNAL_CONNECTIONS),
    content = {
      FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Column {
          Text(
            text = stringResource(R.string.ChooseInitialMyStoryMembershipFragment__all_signal_connections),
            style = MaterialTheme.typography.bodyLarge
          )

          if (count > 0) {
            Text(
              text = pluralStringResource(R.plurals.MyStorySettingsFragment__viewers, count, count),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        TextButton(
          onClick = { onEvent(MyStorySettingsEvent.ViewAllSignalConnectionsClicked) },
          modifier = Modifier.testTag(MyStorySettingsTestTags.BUTTON_VIEW_ALL_SIGNAL_CONNECTIONS)
        ) {
          Text(text = stringResource(R.string.MyStorySettingsFragment__view))
        }
      }
    }
  )
}

@Composable
private fun LearnMoreText(onEvent: (MyStorySettingsEvent) -> Unit) {
  val text = buildAnnotatedString {
    append(stringResource(R.string.MyStorySettingsFragment__choose_who_can_view_your_story))
    append(" ")
    withLink(
      LinkAnnotation.Clickable(
        tag = "learn-more",
        styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
        linkInteractionListener = { onEvent(MyStorySettingsEvent.LearnMoreClicked) }
      )
    ) {
      append(stringResource(R.string.LearnMoreTextView_learn_more))
    }
  }

  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .padding(Rows.defaultPadding())
      .testTag(MyStorySettingsTestTags.TEXT_LEARN_MORE)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MyStorySettingsScreenPreview() {
  MyStorySettingsScreen(
    state = MyStorySettingsState(
      myStoryPrivacyState = MyStoryPrivacyState(
        privacyMode = DistributionListPrivacyMode.ALL_EXCEPT,
        connectionCount = 3
      ),
      areRepliesAndReactionsEnabled = true,
      allSignalConnectionsCount = 42,
      hasUserPerformedManualSelection = true
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MyStorySettingsScreenNoSelectionPreview() {
  MyStorySettingsScreen(
    state = MyStorySettingsState(
      myStoryPrivacyState = MyStoryPrivacyState(
        privacyMode = DistributionListPrivacyMode.ALL,
        connectionCount = 0
      )
    ),
    onEvent = {}
  )
}
