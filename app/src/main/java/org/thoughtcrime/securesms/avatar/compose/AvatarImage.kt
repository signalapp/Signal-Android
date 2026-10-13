/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.emoji.Emojifier
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarRenderer
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import kotlin.math.min

/** Fraction of the avatar width the text is allowed to occupy. */
private const val TEXT_WIDTH_PERCENT = 0.8f

/** Ceiling on the font size, as a fraction of the avatar size. */
private const val MAX_TEXT_SIZE_PERCENT = 0.45f

/** Inset applied to each edge of a resource avatar, as a fraction of the avatar size. */
private const val RESOURCE_PADDING_PERCENT = 0.2f

/**
 * Displays the given Avatar.
 */
@Composable
fun AvatarImage(
  avatar: Avatar,
  contentDescription: String?,
  modifier: Modifier = Modifier
) {
  // The avatar is either a vector, text, or a photo or a resource
  when (avatar) {
    is Avatar.Photo -> AvatarPhoto(avatar, contentDescription, modifier)
    is Avatar.Resource -> AvatarResource(avatar, contentDescription, modifier)
    is Avatar.Text -> AvatarText(avatar, contentDescription, modifier)
    is Avatar.Vector -> AvatarVector(avatar, contentDescription, modifier)
  }
}

@Composable
private fun AvatarPhoto(
  avatar: Avatar.Photo,
  contentDescription: String?,
  modifier: Modifier
) {
  GlideImage(
    model = remember(avatar.uri) { DecryptableUri(avatar.uri) },
    scaleType = GlideImageScaleType.CENTER_CROP,
    modifier = modifier.semantics {
      contentDescription?.let { this.contentDescription = it }
    }
  )
}

@Composable
private fun AvatarResource(
  avatar: Avatar.Resource,
  contentDescription: String?,
  modifier: Modifier
) {
  BoxWithConstraints(
    modifier = modifier
      .background(color = Color(avatar.color.backgroundColor))
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(avatar.resourceId),
      contentDescription = contentDescription,
      tint = Color(avatar.color.foregroundColor),
      modifier = Modifier
        .fillMaxSize()
        .padding(maxWidth * RESOURCE_PADDING_PERCENT)
    )
  }
}

@Composable
private fun AvatarText(
  avatar: Avatar.Text,
  contentDescription: String?,
  modifier: Modifier
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val typeface = remember(context) { FontFamily(AvatarRenderer.getTypeface(context)) }

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier
      // Merged so the description replaces the initials, rather than being announced alongside them.
      .semantics(mergeDescendants = true) { contentDescription?.let { this.contentDescription = it } }
      .background(color = Color(avatar.color.backgroundColor))
  ) {
    val fontSize = remember(avatar.text, constraints, density) {
      val sizePx = min(constraints.maxWidth, constraints.maxHeight).toFloat()

      with(density) {
        Avatars.getTextSizeForLength(context, avatar.text, sizePx * TEXT_WIDTH_PERCENT, sizePx * MAX_TEXT_SIZE_PERCENT).toSp()
      }
    }

    Emojifier(
      text = avatar.text
    ) { text, inlineContent ->
      Text(
        text = text,
        textAlign = TextAlign.Center,
        color = Color(avatar.color.foregroundColor),
        fontFamily = typeface,
        fontSize = fontSize,
        inlineContent = inlineContent
      )
    }
  }
}

@Composable
private fun AvatarVector(
  avatar: Avatar.Vector,
  contentDescription: String?,
  modifier: Modifier
) {
  val drawableId = remember(avatar.key) { Avatars.getDrawableResource(avatar.key) }

  Box(
    modifier = modifier
      .background(color = Color(avatar.color.backgroundColor))
  ) {
    if (drawableId != null) {
      Image(
        painter = painterResource(drawableId),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AvatarTextPreview() {
  Previews.Preview {
    AvatarImage(
      Avatar.Text(
        text = "MM",
        color = Avatars.ColorPair(foregroundAvatarColor = Avatars.ForegroundColor.A210, backgroundAvatarColor = AvatarColor.A210),
        databaseId = Avatar.DatabaseId.NotSet
      ),
      contentDescription = null,
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
    )
  }
}

@DayNightPreviews
@Composable
private fun AvatarVectorPreview() {
  Previews.Preview {
    AvatarImage(
      Avatar.Vector(
        key = "avatar_cat",
        color = Avatars.ColorPair(foregroundAvatarColor = Avatars.ForegroundColor.A210, backgroundAvatarColor = AvatarColor.A210),
        databaseId = Avatar.DatabaseId.NotSet
      ),
      contentDescription = null,
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
    )
  }
}

@DayNightPreviews
@Composable
private fun AvatarResourcePreview() {
  Previews.Preview {
    AvatarImage(
      Avatar.getDefaultForGroup(),
      contentDescription = null,
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
    )
  }
}
