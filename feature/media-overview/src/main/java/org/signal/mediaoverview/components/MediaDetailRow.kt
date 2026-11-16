/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ByteSize
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewState
import org.signal.mediaoverview.MediaOverviewTestTags
import org.signal.mediaoverview.R
import org.signal.core.ui.R as CoreUiR

private const val MARQUEE_DELAY_MILLIS = 2500
private val AudioIconSize = 18.dp

/**
 * A row in the list layout: an icon or thumbnail, who sent it, and its size, type, and date.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaDetailRow(
  item: MediaOverviewItem,
  isSelected: Boolean,
  showThread: Boolean,
  isContextMenuOpen: Boolean,
  audioPlayback: MediaOverviewState.AudioPlayback?,
  onEvent: (MediaOverviewEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(if (isSelected) SignalTheme.colors.colorSurface2 else Color.Transparent)
        .combinedClickable(
          onClick = { onEvent(MediaOverviewEvent.ItemClicked(item)) },
          onLongClick = { onEvent(MediaOverviewEvent.ItemLongClicked(item)) }
        )
        .heightIn(min = 68.dp)
        .padding(start = 4.dp, end = 4.dp)
        .testTag(MediaOverviewTestTags.item(item.key))
    ) {
      Box(modifier = Modifier.size(48.dp)) {
        DetailLeadingIcon(item = item, modifier = Modifier.fillMaxSize())

        if (isSelected) {
          SelectedCheck(
            modifier = Modifier
              .size(21.dp)
              .align(Alignment.BottomEnd)
              .offset(x = 4.dp, y = 4.dp)
          )
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 16.dp)
      ) {
        Text(
          text = describe(item, showThread),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_DELAY_MILLIS)
        )

        if (item.isLink && !item.linkUrl.isNullOrEmpty()) {
          SecondaryLine(text = item.linkUrl)
        }

        SecondaryLine(text = detailLine(item))
      }

      if (item.kind == MediaOverviewItem.Kind.AUDIO) {
        MediaAudioButton(
          item = item,
          playback = audioPlayback?.takeIf { it.uri == item.uri },
          onClick = { onEvent(MediaOverviewEvent.AudioButtonClicked(item)) }
        )
      }
    }

    if (isContextMenuOpen) {
      MediaContextMenu(item = item, onEvent = onEvent)
    }
  }
}

@Composable
private fun SecondaryLine(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
  )
}

@Composable
private fun DetailLeadingIcon(item: MediaOverviewItem, modifier: Modifier = Modifier) {
  when (item.kind) {
    MediaOverviewItem.Kind.IMAGE,
    MediaOverviewItem.Kind.VIDEO -> {
      SmallThumbnail(item = item, modifier = modifier)
    }
    MediaOverviewItem.Kind.LINK -> {
      Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
          .clip(RoundedCornerShape(8.dp))
          .background(SignalTheme.colors.colorTransparent2)
      ) {
        Icon(
          painter = painterResource(CoreUiR.drawable.symbol_link_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (item.displayUri != null) {
          SmallThumbnail(item = item, modifier = Modifier.fillMaxSize())
        }
      }
    }
    MediaOverviewItem.Kind.AUDIO -> {
      DocumentIcon(modifier = modifier) {
        Image(
          painter = painterResource(R.drawable.ic_audio_24),
          contentDescription = null
        )
      }
    }
    MediaOverviewItem.Kind.DOCUMENT -> {
      DocumentIcon(modifier = modifier) {
        Text(
          text = item.fileExtension.orEmpty(),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Color.Black
        )
      }
    }
  }
}

@Composable
private fun DocumentIcon(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Box(contentAlignment = Alignment.Center, modifier = modifier) {
    Image(
      painter = painterResource(CoreUiR.drawable.ic_document_large),
      contentDescription = null,
      modifier = Modifier.size(width = 38.dp, height = 50.dp)
    )
    content()
  }
}

@Composable
private fun SmallThumbnail(item: MediaOverviewItem, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(SignalTheme.colors.colorSurface3)
  ) {
    if (!LocalInspectionMode.current) {
      if (item.blurHash != null) {
        GlideImage(
          model = item.blurHash,
          scaleType = GlideImageScaleType.CENTER_CROP,
          diskCacheStrategy = DiskCacheStrategy.NONE,
          modifier = Modifier.fillMaxSize()
        )
      }

      val displayUri = item.displayUri
      if (displayUri != null) {
        GlideImage(
          model = DecryptableUri(displayUri, 0, if (item.uri != null) item.contentType else null),
          scaleType = GlideImageScaleType.CENTER_CROP,
          diskCacheStrategy = DiskCacheStrategy.RESOURCE,
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    MediaThumbnailOverlays(
      item = item,
      playOverlaySize = 24.dp,
      modifier = Modifier.fillMaxSize()
    )
  }
}

@Composable
private fun MediaAudioButton(
  item: MediaOverviewItem,
  playback: MediaOverviewState.AudioPlayback?,
  onClick: () -> Unit
) {
  val needsDownload = item.uri == null

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(48.dp)
  ) {
    if (playback != null && (playback.isPlaying || playback.progress > 0f)) {
      CircularProgressIndicator(
        progress = { playback.progress },
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurface,
        trackColor = Color.Transparent,
        modifier = Modifier.size(40.dp)
      )
    }

    IconButton(
      onClick = onClick,
      colors = IconButtonDefaults.iconButtonColors(
        containerColor = SignalTheme.colors.colorSurface1,
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      modifier = Modifier
        .size(40.dp)
        .testTag(MediaOverviewTestTags.audioButton(item.key))
    ) {
      when {
        needsDownload -> {
          Icon(
            painter = painterResource(R.drawable.symbol_arrow_down_24),
            contentDescription = stringResource(R.string.MediaOverviewScreen__download),
            modifier = Modifier.size(AudioIconSize)
          )
        }
        playback?.isPlaying == true -> {
          Icon(
            imageVector = SignalIcons.Pause.imageVector,
            contentDescription = stringResource(R.string.MediaOverviewScreen__pause),
            modifier = Modifier.size(AudioIconSize)
          )
        }
        else -> {
          Icon(
            painter = painterResource(R.drawable.triangle_right),
            contentDescription = stringResource(R.string.MediaOverviewScreen__play),
            modifier = Modifier.size(AudioIconSize)
          )
        }
      }
    }
  }
}

@Composable
private fun describe(item: MediaOverviewItem, showThread: Boolean): String {
  val sentFromTo = sentFromTo(item, showThread)

  val title = when (item.kind) {
    MediaOverviewItem.Kind.AUDIO -> {
      stringResource(R.string.MediaOverviewScreen__voice_message)
    }
    MediaOverviewItem.Kind.LINK -> {
      item.linkTitle?.takeIf { it.isNotEmpty() }
    }
    else -> {
      item.fileName
    }
  }

  return if (title != null) {
    stringResource(R.string.MediaOverviewScreen__two_part_line, title, sentFromTo)
  } else {
    sentFromTo
  }
}

@Composable
private fun sentFromTo(item: MediaOverviewItem, showThread: Boolean): String {
  return when {
    item.isNoteToSelf -> {
      stringResource(R.string.MediaOverviewScreen__note_to_self)
    }
    showThread && (item.isOutgoing || item.isThreadGroup) -> {
      if (item.isOutgoing) {
        stringResource(R.string.MediaOverviewScreen__sent_by_you_to_s, item.threadName)
      } else {
        stringResource(R.string.MediaOverviewScreen__sent_by_s_to_s, item.senderName, item.threadName)
      }
    }
    item.isOutgoing -> {
      stringResource(R.string.MediaOverviewScreen__sent_by_you)
    }
    else -> {
      stringResource(R.string.MediaOverviewScreen__sent_by_s, item.senderName)
    }
  }
}

@Composable
private fun detailLine(item: MediaOverviewItem): String {
  if (item.isLink || !item.hasAttachment) {
    return item.formattedDate
  }

  val type = when (item.kind) {
    MediaOverviewItem.Kind.IMAGE -> {
      stringResource(R.string.MediaOverviewScreen__type_image)
    }
    MediaOverviewItem.Kind.VIDEO -> {
      stringResource(R.string.MediaOverviewScreen__type_video)
    }
    MediaOverviewItem.Kind.AUDIO -> {
      stringResource(R.string.MediaOverviewScreen__type_audio)
    }
    MediaOverviewItem.Kind.DOCUMENT,
    MediaOverviewItem.Kind.LINK -> {
      stringResource(R.string.MediaOverviewScreen__type_file)
    }
  }

  return stringResource(R.string.MediaOverviewScreen__three_part_line, ByteSize(item.size).toUnitString(maxPlaces = 2), type, item.formattedDate)
}
