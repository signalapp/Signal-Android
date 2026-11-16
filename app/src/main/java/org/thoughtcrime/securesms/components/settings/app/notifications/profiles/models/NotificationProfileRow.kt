package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.emoji.EmojiUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.util.formatHours
import java.util.UUID

/**
 * @param activeStatus How long [profile] stays on if it is the active profile, or null if it isn't active.
 */
@Composable
fun NotificationProfileRow(
  profile: NotificationProfile,
  activeStatus: NotificationProfiles.ActiveStatus? = null,
  showSwitch: Boolean = false,
  enabled: Boolean = true,
  onClick: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = { onClick(profile.id) })
      .horizontalGutters()
      .padding(vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(
          color = Color(profile.color.colorInt()),
          shape = CircleShape
        )
        .padding(8.dp),
      contentAlignment = Alignment.Center
    ) {
      if (profile.emoji.isNotEmpty()) {
        val emojiDrawable = remember(profile.emoji) { EmojiUtil.convertToDrawable(context, profile.emoji) }

        Image(
          painter = rememberDrawablePainter(drawable = emojiDrawable),
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
      } else {
        Image(
          imageVector = ImageVector.vectorResource(R.drawable.ic_moon_24),
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
      }
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = profile.name,
        style = MaterialTheme.typography.bodyLarge
      )

      if (activeStatus != null) {
        Text(
          text = activeStatusDescription(activeStatus),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (showSwitch) {
      Switch(
        checked = activeStatus != null,
        onCheckedChange = { onClick(profile.id) },
        enabled = enabled
      )
    }
  }
}

@Composable
private fun activeStatusDescription(activeStatus: NotificationProfiles.ActiveStatus): String {
  return when (activeStatus) {
    NotificationProfiles.ActiveStatus.On -> stringResource(R.string.NotificationProfilesFragment__on)
    is NotificationProfiles.ActiveStatus.OnUntil -> {
      val until = activeStatus.until.formatHours(is24Hour = DateFormat.is24HourFormat(LocalContext.current), locale = LocalLocale.current.platformLocale)
      stringResource(R.string.NotificationProfileSelection__on_until_s, until)
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfileRowPreview() {
  Column {
    NotificationProfileRow(
      profile = NotificationProfile(
        id = 1L,
        name = "Work",
        createdAt = 0L,
        schedule = NotificationProfileSchedule(
          id = 1L
        ),
        emoji = "",
        notificationProfileId = NotificationProfileId(UUID.randomUUID())
      ),
      onClick = {}
    )

    NotificationProfileRow(
      profile = NotificationProfile(
        id = 1L,
        name = "Sleep",
        createdAt = 0L,
        schedule = NotificationProfileSchedule(
          id = 1L
        ),
        emoji = "",
        notificationProfileId = NotificationProfileId(UUID.randomUUID())
      ),
      onClick = {}
    )
  }
}
