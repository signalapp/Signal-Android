/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.Tooltips
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageSmall
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.recipients.Recipient

interface MainToolbarCallback {
  fun onNewGroupClick()
  fun onClearPassphraseClick()
  fun onMarkReadClick()
  fun onInviteFriendsClick()
  fun onFilterUnreadChatsClick()
  fun onClearUnreadChatsFilterClick()
  fun onSettingsClick()
  fun onNotificationProfileClick()
  fun onProxyClick()
  fun onSearchClick()
  fun onClearCallHistoryClick()
  fun onFilterMissedCallsClick()
  fun onClearCallFilterClick()
  fun onStoryPrivacyClick()
  fun onCloseSearchClick()
  fun onCloseArchiveClick()
  fun onSearchQueryUpdated(query: String)
  fun onNotificationProfileTooltipDismissed()

  object Empty : MainToolbarCallback {
    override fun onNewGroupClick() = Unit
    override fun onClearPassphraseClick() = Unit
    override fun onMarkReadClick() = Unit
    override fun onInviteFriendsClick() = Unit
    override fun onFilterUnreadChatsClick() = Unit
    override fun onClearUnreadChatsFilterClick() = Unit
    override fun onSettingsClick() = Unit
    override fun onNotificationProfileClick() = Unit
    override fun onProxyClick() = Unit
    override fun onSearchClick() = Unit
    override fun onClearCallHistoryClick() = Unit
    override fun onFilterMissedCallsClick() = Unit
    override fun onClearCallFilterClick() = Unit
    override fun onStoryPrivacyClick() = Unit
    override fun onCloseSearchClick() = Unit
    override fun onCloseArchiveClick() = Unit
    override fun onSearchQueryUpdated(query: String) = Unit
    override fun onNotificationProfileTooltipDismissed() = Unit
  }
}

enum class MainToolbarMode {
  ACTION_MODE,
  FULL,
  BASIC,
  SEARCH
}

data class MainToolbarState(
  val toolbarColor: Color? = null,
  val self: Recipient = Recipient.UNKNOWN,
  val mode: MainToolbarMode = MainToolbarMode.FULL,
  val destination: MainNavigationListLocation = MainNavigationListLocation.CHATS,
  val chatFilter: ConversationFilter = ConversationFilter.OFF,
  val callFilter: CallLogFilter = CallLogFilter.ALL,
  val hasUnreadPayments: Boolean = false,
  val hasFailedBackups: Boolean = false,
  val hasEnabledNotificationProfile: Boolean = false,
  val showNotificationProfilesTooltip: Boolean = false,
  val hasPassphrase: Boolean = false,
  val proxyState: ProxyState = ProxyState.NONE,
  @StringRes val searchHint: Int = R.string.SearchToolbar_search,
  val searchQuery: String = ""
) {
  enum class ProxyState(@DrawableRes val icon: Int) {
    NONE(-1),
    CONNECTING(R.drawable.ic_proxy_connecting_24),
    CONNECTED(R.drawable.ic_proxy_connected_24),
    FAILED(R.drawable.ic_proxy_failed_24)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  if (state.mode == MainToolbarMode.ACTION_MODE) {
    TopAppBar(title = {})
    return
  }

  Crossfade(
    targetState = state.mode != MainToolbarMode.BASIC
  ) { targetState ->
    when (targetState) {
      true -> Box {
        var revealOffset by remember { mutableStateOf(Offset.Zero) }

        BoxWithConstraints {
          val maxWidth = with(LocalDensity.current) {
            maxWidth.toPx()
          }

          PrimaryToolbar(state, callback) {
            revealOffset = Offset(it / maxWidth, 0.5f)
          }

          AnimatedVisibility(
            visible = state.mode == MainToolbarMode.SEARCH,
            enter = EnterTransition.None,
            exit = ExitTransition.None
          ) {
            val visibility = transition.animateFloat(
              transitionSpec = { tween(durationMillis = 400, easing = LinearOutSlowInEasing) },
              label = "Visibility"
            ) { state ->
              if (state == EnterExitState.Visible) 1f else 0f
            }

            SearchToolbar(
              state = state,
              callback = callback,
              modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .circularReveal(visibility, revealOffset)
            )
          }
        }
      }

      false -> ArchiveToolbar(state, callback)
    }
  }
}

@Composable
private fun SearchToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    TextFields.TextField(
      value = state.searchQuery,
      onValueChange = callback::onSearchQueryUpdated,
      leadingIcon = {
        IconButtons.IconButton(
          onClick = callback::onCloseSearchClick
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
            contentDescription = stringResource(R.string.MainToolbar__close_search_content_description)
          )
        }
      },
      trailingIcon = if (state.searchQuery.isNotEmpty()) {
        {
          IconButtons.IconButton(
            onClick = {
              callback.onSearchQueryUpdated("")
            }
          ) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.ic_x_20),
              contentDescription = stringResource(R.string.MainToolbar__clear_search_content_description)
            )
          }
        }
      } else {
        null
      },
      contentPadding = PaddingValues(0.dp),
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      textStyle = MaterialTheme.typography.bodyLarge,
      shape = RoundedCornerShape(50),
      singleLine = true,
      placeholder = {
        Text(text = stringResource(state.searchHint))
      },
      modifier = modifier
        .background(color = state.toolbarColor ?: MaterialTheme.colorScheme.surface)
        .height(dimensionResource(R.dimen.signal_m3_toolbar_height))
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)
    )
  }

  LaunchedEffect(state.mode) {
    if (state.mode == MainToolbarMode.SEARCH) {
      focusRequester.requestFocus()
    } else {
      focusRequester.freeFocus()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = state.toolbarColor ?: MaterialTheme.colorScheme.surface
    ),
    navigationIcon = {
      IconButtons.IconButton(onClick = {
        callback.onCloseArchiveClick()
      }) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
          contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
        )
      }
    },
    title = {
      Text(text = stringResource(R.string.AndroidManifest_archived_conversations))
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimaryToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback,
  onSearchButtonPositioned: (Float) -> Unit
) {
  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = state.toolbarColor ?: MaterialTheme.colorScheme.surface
    ),
    navigationIcon = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(start = 20.dp, end = 16.dp)
          .size(48.dp)
      ) {
        AvatarImage(
          recipient = state.self,
          modifier = Modifier
            .clip(CircleShape)
            .size(28.dp)
        )

        val interactionSource = remember { MutableInteractionSource() }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clickable(
              onClick = callback::onSettingsClick,
              onClickLabel = stringResource(R.string.conversation_list_settings_shortcut),
              interactionSource = interactionSource,
              indication = ripple(radius = 14.dp)
            )
        )

        BadgeImageSmall(
          badge = state.self.featuredBadge,
          modifier = Modifier
            .padding(start = 14.dp, top = 16.dp)
            .size(16.dp)
        )

        HeadsUpIndicator(
          state = state,
          modifier = Modifier.padding(start = 20.dp, bottom = 20.dp)
        )
      }
    },
    title = {
      Text(
        text = stringResource(R.string.app_name)
      )
    },
    actions = {
      NotificationProfileAction(state, callback)
      ProxyAction(state, callback)

      IconButtons.IconButton(
        onClick = callback::onSearchClick,
        modifier = Modifier.onPlaced {
          onSearchButtonPositioned(it.positionInWindow().x + (it.size.width / 2f))
        }
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_search_24),
          contentDescription = stringResource(R.string.conversation_list_search_description)
        )
      }

      val controller = remember { DropdownMenus.MenuController() }
      val dismiss = remember(controller) { { controller.hide() } }

      TooltipOverflowButton(
        onOverflowClick = { controller.show() },
        isTooltipVisible = state.showNotificationProfilesTooltip,
        onDismiss = { callback.onNotificationProfileTooltipDismissed() }
      )

      DropdownMenus.Menu(
        controller = controller
      ) {
        when (state.destination) {
          MainNavigationListLocation.CHATS -> ChatDropdownItems(state, callback, dismiss)
          MainNavigationListLocation.CALLS -> CallDropdownItems(state.callFilter, callback, dismiss)
          MainNavigationListLocation.STORIES -> StoryDropDownItems(callback, dismiss)
        }
      }
    }
  )
}

@Composable
private fun TooltipOverflowButton(
  onOverflowClick: () -> Unit,
  onDismiss: () -> Unit,
  isTooltipVisible: Boolean
) {
  Tooltips.PlainBelowAnchor(
    onDismiss = onDismiss,
    isTooltipVisible = isTooltipVisible,
    tooltipContent = {
      Text(text = stringResource(R.string.ConversationListFragment__turn_your_notification_profile_on_or_off_here))
    },
    anchorContent = {
      IconButtons.IconButton(
        onClick = onOverflowClick
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
          contentDescription = stringResource(R.string.MainToolbar__more_options_content_description)
        )
      }
    }
  )
}

@Composable
private fun NotificationProfileAction(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  if (state.hasEnabledNotificationProfile) {
    IconButtons.IconButton(
      onClick = callback::onNotificationProfileClick
    ) {
      // TODO [alex] - Add proper icon (cannot utilize layer-list)
      Image(
        painter = painterResource(R.drawable.ic_moon_24),
        contentDescription = stringResource(R.string.MainToolbar__notification_profile_content_description)
      )
    }
  }
}

@Composable
private fun ProxyAction(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  if (state.proxyState != MainToolbarState.ProxyState.NONE) {
    IconButtons.IconButton(
      onClick = callback::onProxyClick
    ) {
      Image(
        imageVector = ImageVector.vectorResource(state.proxyState.icon),
        contentDescription = stringResource(R.string.MainToolbar__proxy_content_description)
      )
    }
  }
}

@Composable
private fun HeadsUpIndicator(state: MainToolbarState, modifier: Modifier = Modifier) {
  if (!state.hasUnreadPayments && !state.hasFailedBackups) {
    return
  }

  val color = if (state.hasFailedBackups) {
    Color(0xFFFFCC00)
  } else {
    MaterialTheme.colorScheme.primary
  }

  Box(
    modifier = modifier
      .size(13.dp)
      .background(color = color, shape = CircleShape)
  ) {
    // Intentionally empty
  }
}

@Composable
private fun StoryDropDownItems(callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.StoriesLandingFragment__story_privacy),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onStoryPrivacyClick()
      onOptionSelected()
    }
  )
}

@Composable
private fun CallDropdownItems(callFilter: CallLogFilter, callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.CallLogFragment__clear_call_history),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onClearCallHistoryClick()
      onOptionSelected()
    }
  )

  if (callFilter == CallLogFilter.ALL) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.CallLogFragment__filter_missed_calls),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      onClick = {
        callback.onFilterMissedCallsClick()
        onOptionSelected()
      }
    )
  } else {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.CallLogFragment__clear_filter),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      onClick = {
        callback.onClearCallFilterClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_settings),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onSettingsClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.ConversationListFragment__notification_profile),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onNotificationProfileClick()
      onOptionSelected()
    }
  )
}

@Composable
private fun ChatDropdownItems(state: MainToolbarState, callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_new_group),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onNewGroupClick()
      onOptionSelected()
    }
  )

  if (state.hasPassphrase) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__menu_clear_passphrase),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      onClick = {
        callback.onClearPassphraseClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__mark_all_as_read),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onMarkReadClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__invite_friends),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onInviteFriendsClick()
      onOptionSelected()
    }
  )

  if (state.chatFilter == ConversationFilter.OFF) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__filter_unread_chats),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      onClick = {
        callback.onFilterUnreadChatsClick()
        onOptionSelected()
      }
    )
  } else {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__clear_unread_filter),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      onClick = {
        callback.onClearUnreadChatsFilterClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_settings),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onSettingsClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.ConversationListFragment__notification_profile),
        style = MaterialTheme.typography.bodyLarge
      )
    },
    onClick = {
      callback.onNotificationProfileClick()
      onOptionSelected()
    }
  )
}

@Preview
@SignalPreview
@Composable
private fun FullMainToolbarPreview() {
  Previews.Preview {
    var mode by remember { mutableStateOf(MainToolbarMode.FULL) }

    MainToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false),
        mode = mode,
        destination = MainNavigationListLocation.CHATS,
        hasEnabledNotificationProfile = true,
        proxyState = MainToolbarState.ProxyState.CONNECTED,
        hasFailedBackups = true
      ),
      callback = object : MainToolbarCallback by MainToolbarCallback.Empty {
        override fun onSearchClick() {
          mode = MainToolbarMode.SEARCH
        }

        override fun onCloseSearchClick() {
          mode = MainToolbarMode.FULL
        }
      }
    )
  }
}

@SignalPreview
@Composable
private fun SearchToolbarPreview() {
  Previews.Preview {
    SearchToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false, isSelf = true),
        searchQuery = "Test query"
      ),
      callback = MainToolbarCallback.Empty
    )
  }
}

@SignalPreview
@Composable
private fun ArchiveToolbarPreview() {
  Previews.Preview {
    ArchiveToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false)
      ),
      callback = MainToolbarCallback.Empty
    )
  }
}

@SignalPreview
@Composable
private fun TooltipOverflowButtonPreview() {
  Previews.Preview {
    TooltipOverflowButton(
      onOverflowClick = {},
      onDismiss = {},
      isTooltipVisible = true
    )
  }
}
