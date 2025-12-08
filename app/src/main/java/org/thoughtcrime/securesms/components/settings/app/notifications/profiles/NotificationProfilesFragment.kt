package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiStrings
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileRow
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.UUID

/**
 * Primary entry point for Notification Profiles. When user has no profiles, shows empty state, otherwise shows
 * all current profiles.
 */
class NotificationProfilesFragment : ComposeFragment() {

  private val viewModel: NotificationProfilesViewModel by viewModels(
    factoryProducer = { NotificationProfilesViewModel.Factory() }
  )

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle(initialValue = NotificationProfilesState(profiles = emptyList()))
    val callback = remember { DefaultNotificationProfilesScreenCallback() }

    NotificationProfilesScreen(
      state = state,
      callbacks = callback
    )
  }

  inner class DefaultNotificationProfilesScreenCallback : NotificationProfilesScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onCreateNewProfile() {
      findNavController().safeNavigate(R.id.action_notificationProfilesFragment_to_editNotificationProfileFragment)
    }

    override fun onProfileClick(profileId: Long) {
      findNavController().safeNavigate(NotificationProfilesFragmentDirections.actionNotificationProfilesFragmentToNotificationProfileDetailsFragment(profileId))
    }
  }
}

@Composable
fun NotificationProfilesScreen(
  state: NotificationProfilesState,
  callbacks: NotificationProfilesScreenCallback
) {
  val title = if (state.profiles.isEmpty()) {
    ""
  } else {
    stringResource(R.string.NotificationsSettingsFragment__notification_profiles)
  }

  Scaffolds.Settings(
    title = title,
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.Material3SearchToolbar__close)
  ) { paddingValues ->
    if (state.profiles.isEmpty()) {
      NoNotificationProfilesEmpty(
        onCreateProfileClick = callbacks::onCreateNewProfile,
        modifier = Modifier.padding(paddingValues)
      )
    } else {
      LazyColumn(
        modifier = Modifier.padding(paddingValues)
      ) {
        item {
          Texts.SectionHeader(
            text = stringResource(R.string.NotificationProfilesFragment__profiles)
          )
        }

        item {
          Rows.TextRow(
            text = {
              Text(text = stringResource(R.string.NotificationProfilesFragment__new_profile))
            },
            icon = {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.symbol_plus_24),
                contentDescription = null,
                modifier = Modifier
                  .size(40.dp)
                  .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                  .padding(8.dp)
              )
            },
            onClick = callbacks::onCreateNewProfile
          )
        }

        state.profiles.sortedDescending().forEach { profile ->
          item {
            NotificationProfileRow(
              profile = profile,
              isActiveProfile = profile == state.activeProfile,
              onClick = callbacks::onProfileClick
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NoNotificationProfilesEmpty(
  onCreateProfileClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .horizontalGutters(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(96.dp))

    Box(
      modifier = Modifier
        .size(88.dp)
        .background(
          color = Color(AvatarColor.A100.colorInt()),
          shape = CircleShape
        )
        .padding(20.dp),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(R.drawable.ic_sleeping_face),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = stringResource(R.string.NotificationProfilesFragment__notification_profiles),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = stringResource(R.string.NotificationProfilesFragment__create_a_profile_to_receive_notifications_and_calls_only_from_the_people_and_groups_you_want_to_hear_from),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onCreateProfileClick,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = stringResource(R.string.NotificationProfilesFragment__create_profile))
    }
  }
}

@DayNightPreviews
@Composable
fun NotificationProfilesScreenPreview() {
  Previews.Preview {
    val profile = remember {
      NotificationProfile(
        id = 1L,
        name = "Test Profile",
        emoji = EmojiStrings.AUDIO,
        createdAt = System.currentTimeMillis(),
        schedule = NotificationProfileSchedule(
          id = 1
        ),
        notificationProfileId = NotificationProfileId(UUID.randomUUID())
      )
    }

    NotificationProfilesScreen(
      state = NotificationProfilesState(
        profiles = listOf(
          profile
        ),
        activeProfile = null
      ),
      callbacks = NotificationProfilesScreenCallback.Empty
    )
  }
}

@DayNightPreviews
@Composable
private fun NoNotificationProfilesEmptyPreview() {
  Previews.Preview {
    NoNotificationProfilesEmpty(
      onCreateProfileClick = {}
    )
  }
}

interface NotificationProfilesScreenCallback {
  fun onNavigationClick()
  fun onCreateNewProfile()
  fun onProfileClick(profileId: Long)

  object Empty : NotificationProfilesScreenCallback {
    override fun onNavigationClick() = Unit
    override fun onCreateNewProfile() = Unit
    override fun onProfileClick(profileId: Long) = Unit
  }
}
