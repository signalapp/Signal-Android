/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.EmptyMegaphoneActionController
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity

/**
 * The onboarding megaphone (list of cards)
 */
@Composable
fun OnboardingMegaphone(
  megaphoneActionController: MegaphoneActionController,
  modifier: Modifier = Modifier,
  onboardingState: OnboardingState = OnboardingState.rememberOnboardingState(megaphoneActionController)
) {
  Column(
    modifier = modifier
      .padding(bottom = 22.dp)
  ) {
    Box(
      modifier = Modifier
        .height(24.dp)
        .fillMaxWidth()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              MaterialTheme.colorScheme.background
            )
          )
        )
    )

    Text(
      text = stringResource(R.string.Megaphones_get_started),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(start = 16.dp, top = 4.dp),
      color = MaterialTheme.colorScheme.onSurface
    )

    val onboardingItems = remember(onboardingState.displayState) {
      OnboardingListItem.entries.filter(onboardingState.displayState::shouldDisplayListItem)
    }

    LazyRow(
      modifier = Modifier.padding(top = 10.dp)
    ) {
      itemsIndexed(items = onboardingItems) { idx, item ->
        OnboardingMegaphoneListItem(
          onboardingListItem = item,
          onActionClick = {
            onboardingState.onItemActionClick(item)
          },
          onCloseClick = {
            onboardingState.onItemCloseClick(item)
          },
          modifier = if (idx == 0) Modifier.padding(start = 16.dp) else Modifier
        )
      }
    }
  }
}

/**
 * Single megaphone list item, such as "Invite Friends"
 */
@Composable
private fun OnboardingMegaphoneListItem(
  onboardingListItem: OnboardingListItem,
  onActionClick: (OnboardingListItem) -> Unit,
  onCloseClick: (OnboardingListItem) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(28.dp),
    elevation = CardDefaults.cardElevation(0.dp),
    colors = CardDefaults.cardColors(
      containerColor = colorResource(onboardingListItem.cardColor)
    ),
    modifier = modifier
      .padding(end = 12.dp)
      .width(152.dp)
      .clickable(onClick = { onActionClick(onboardingListItem) })
  ) {
    Box(
      modifier = Modifier.fillMaxWidth()
    ) {
      IconButtons.IconButton(
        onClick = { onCloseClick(onboardingListItem) },
        size = 48.dp,
        modifier = Modifier.align(Alignment.TopEnd)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_x_24),
          tint = colorResource(R.color.signal_light_colorOutline),
          contentDescription = stringResource(R.string.Material3SearchToolbar__close)
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 84.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(onboardingListItem.icon),
          contentDescription = null,
          tint = colorResource(R.color.signal_light_colorOnSurface),
          modifier = Modifier.size(24.dp)
        )

        Text(
          text = stringResource(onboardingListItem.title),
          style = MaterialTheme.typography.labelMedium,
          textAlign = TextAlign.Center,
          maxLines = 2,
          color = colorResource(R.color.signal_light_colorOnSurface),
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp)
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun OnboardingMegaphonePreview() {
  Previews.Preview {
    OnboardingMegaphone(
      megaphoneActionController = EmptyMegaphoneActionController,
      onboardingState = OnboardingState.rememberOnboardingState()
    )
  }
}

@SignalPreview
@Composable
private fun OnboardingMegaphoneListItemPreview() {
  Previews.Preview {
    OnboardingMegaphoneListItem(
      onboardingListItem = OnboardingListItem.INVITE,
      onActionClick = {},
      onCloseClick = {}
    )
  }
}

/**
 * Represents a card that can be displayed to the user when showing onboarding content.
 */
enum class OnboardingListItem(
  @StringRes val title: Int,
  @DrawableRes val icon: Int,
  @ColorRes val cardColor: Int
) {
  GROUP(
    title = R.string.Megaphones_new_group,
    icon = R.drawable.symbol_group_24,
    cardColor = R.color.onboarding_background_1
  ),
  INVITE(
    title = R.string.Megaphones_invite_friends,
    icon = R.drawable.symbol_invite_24,
    cardColor = R.color.onboarding_background_2
  ),
  ADD_PHOTO(
    title = R.string.Megaphones_add_a_profile_photo,
    icon = R.drawable.symbol_person_circle_24,
    cardColor = R.color.onboarding_background_4
  ),
  APPEARANCE(
    title = R.string.Megaphones_chat_colors,
    icon = R.drawable.ic_color_24,
    cardColor = R.color.onboarding_background_3
  )
}

/**
 * Maintains the list of displayable cards and drives actions performed by the user.
 */
abstract class OnboardingState private constructor(
  initialState: DisplayState = DisplayState(),
  val megaphoneActionController: MegaphoneActionController
) {

  companion object {
    /**
     * Grabs an [OnboardingState], keyed to the given [MegaphoneActionController]
     */
    @Composable
    fun rememberOnboardingState(megaphoneActionController: MegaphoneActionController = EmptyMegaphoneActionController): OnboardingState {
      return if (LocalInspectionMode.current) {
        Preview
      } else {
        remember(megaphoneActionController) { Real(megaphoneActionController = megaphoneActionController) }
      }
    }
  }

  /**
   * The latest display state for the list of onboarding items. An empty list means we can
   * mark this megaphone as complete.
   */
  var displayState: DisplayState by mutableStateOf(initialState)

  /**
   * When a list item is clicked.
   */
  abstract fun onItemActionClick(onboardingListItem: OnboardingListItem)

  /**
   * When a list item close button is clicked.
   */
  abstract fun onItemCloseClick(onboardingListItem: OnboardingListItem)

  /**
   * Preview implementation, used automatically when rendering previews.
   */
  private object Preview : OnboardingState(
    initialState = DisplayState(
      shouldShowNewGroup = true,
      shouldShowInviteFriends = true,
      shouldShowAddPhoto = true,
      shouldShowAppearance = true
    ),
    megaphoneActionController = EmptyMegaphoneActionController
  ) {
    override fun onItemCloseClick(onboardingListItem: OnboardingListItem) {
      displayState = when (onboardingListItem) {
        OnboardingListItem.GROUP -> displayState.copy(shouldShowNewGroup = false)
        OnboardingListItem.INVITE -> displayState.copy(shouldShowInviteFriends = false)
        OnboardingListItem.ADD_PHOTO -> displayState.copy(shouldShowAddPhoto = false)
        OnboardingListItem.APPEARANCE -> displayState.copy(shouldShowAppearance = false)
      }
    }

    override fun onItemActionClick(onboardingListItem: OnboardingListItem) = Unit
  }

  /**
   * Real implementation, used automatically on-device. Backed by SignalStore.
   */
  private class Real(megaphoneActionController: MegaphoneActionController) : OnboardingState(megaphoneActionController = megaphoneActionController) {
    override fun onItemCloseClick(onboardingListItem: OnboardingListItem) {
      when (onboardingListItem) {
        OnboardingListItem.GROUP -> SignalStore.onboarding.setShowNewGroup(false)
        OnboardingListItem.INVITE -> SignalStore.onboarding.setShowInviteFriends(false)
        OnboardingListItem.ADD_PHOTO -> SignalStore.onboarding.setShowAddPhoto(false)
        OnboardingListItem.APPEARANCE -> SignalStore.onboarding.setShowAppearance(false)
      }

      displayState = DisplayState()

      if (displayState.hasNoVisibleContent()) {
        megaphoneActionController.onMegaphoneCompleted(Megaphones.Event.ONBOARDING)
      }
    }

    override fun onItemActionClick(onboardingListItem: OnboardingListItem) {
      when (onboardingListItem) {
        OnboardingListItem.GROUP -> megaphoneActionController.onMegaphoneNavigationRequested(CreateGroupActivity.newIntent(megaphoneActionController.megaphoneActivity))
        OnboardingListItem.INVITE -> megaphoneActionController.onMegaphoneNavigationRequested(AppSettingsActivity.invite(megaphoneActionController.megaphoneActivity))
        OnboardingListItem.ADD_PHOTO -> {
          megaphoneActionController.onMegaphoneNavigationRequested(EditProfileActivity.getIntentForAvatarEdit(megaphoneActionController.megaphoneActivity))
          SignalStore.onboarding.setShowAddPhoto(false)
        }
        OnboardingListItem.APPEARANCE -> {
          megaphoneActionController.onMegaphoneNavigationRequested(ChatWallpaperActivity.createIntent(megaphoneActionController.megaphoneActivity))
          SignalStore.onboarding.setShowAppearance(false)
        }
      }

      displayState = DisplayState()

      if (displayState.hasNoVisibleContent()) {
        megaphoneActionController.onMegaphoneCompleted(Megaphones.Event.ONBOARDING)
      }
    }
  }

  /**
   * Simple display state, driven by [SignalStore] by default.
   */
  data class DisplayState(
    private val shouldShowNewGroup: Boolean = SignalStore.onboarding.shouldShowNewGroup(),
    private val shouldShowInviteFriends: Boolean = SignalStore.onboarding.shouldShowInviteFriends(),
    private val shouldShowAddPhoto: Boolean = SignalStore.onboarding.shouldShowAddPhoto() && !SignalStore.misc.hasEverHadAnAvatar,
    private val shouldShowAppearance: Boolean = SignalStore.onboarding.shouldShowAppearance()
  ) {
    fun hasNoVisibleContent(): Boolean = !(shouldShowNewGroup || shouldShowInviteFriends || shouldShowAddPhoto || shouldShowAppearance)

    fun shouldDisplayListItem(onboardingListItem: OnboardingListItem): Boolean {
      return when (onboardingListItem) {
        OnboardingListItem.GROUP -> shouldShowNewGroup
        OnboardingListItem.INVITE -> shouldShowInviteFriends
        OnboardingListItem.ADD_PHOTO -> shouldShowAddPhoto
        OnboardingListItem.APPEARANCE -> shouldShowAppearance
      }
    }
  }
}
