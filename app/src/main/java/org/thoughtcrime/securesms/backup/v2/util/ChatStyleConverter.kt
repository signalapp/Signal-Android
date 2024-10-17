/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.util

import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.ChatStyle
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.PartUriParser
import org.thoughtcrime.securesms.util.UriUtil
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory
import org.thoughtcrime.securesms.wallpaper.GradientChatWallpaper
import org.thoughtcrime.securesms.wallpaper.SingleColorChatWallpaper
import org.thoughtcrime.securesms.wallpaper.UriChatWallpaper

/**
 * Contains a collection of methods to chat styles to and from their archive format.
 * These are in a file of their own just because they're rather long (with all of the various constants to map between) and used in multiple places.
 */
object ChatStyleConverter {
  fun constructRemoteChatStyle(
    db: SignalDatabase,
    chatColors: ChatColors?,
    chatColorId: ChatColors.Id,
    chatWallpaper: Wallpaper?
  ): ChatStyle? {
    if (chatColors == null && chatWallpaper == null) {
      return null
    }

    val chatStyleBuilder = ChatStyle.Builder()

    if (chatColors != null) {
      when (chatColorId) {
        ChatColors.Id.NotSet -> {}
        ChatColors.Id.Auto -> {
          chatStyleBuilder.autoBubbleColor = ChatStyle.AutomaticBubbleColor()
        }

        ChatColors.Id.BuiltIn -> {
          chatStyleBuilder.bubbleColorPreset = chatColors.toRemote()
        }

        is ChatColors.Id.Custom -> {
          chatStyleBuilder.customColorId = chatColorId.longValue
        }
      }
    }

    if (chatWallpaper != null) {
      when {
        chatWallpaper.singleColor != null -> {
          chatStyleBuilder.wallpaperPreset = chatWallpaper.singleColor.color.toRemoteWallpaperPreset()
        }
        chatWallpaper.linearGradient != null -> {
          chatStyleBuilder.wallpaperPreset = chatWallpaper.linearGradient.toRemoteWallpaperPreset()
        }
        chatWallpaper.file_ != null -> {
          chatStyleBuilder.wallpaperPhoto = chatWallpaper.file_.toFilePointer(db)
        }
      }

      chatStyleBuilder.dimWallpaperInDarkMode = chatWallpaper.dimLevelInDarkTheme > 0
    }

    return chatStyleBuilder.build()
  }
}

fun ChatStyle.toLocal(importState: ImportState): ChatColors? {
  if (this.bubbleColorPreset != null) {
    return when (this.bubbleColorPreset) {
      // Solids
      ChatStyle.BubbleColorPreset.SOLID_CRIMSON -> ChatColorsPalette.Bubbles.CRIMSON
      ChatStyle.BubbleColorPreset.SOLID_VERMILION -> ChatColorsPalette.Bubbles.VERMILION
      ChatStyle.BubbleColorPreset.SOLID_BURLAP -> ChatColorsPalette.Bubbles.BURLAP
      ChatStyle.BubbleColorPreset.SOLID_FOREST -> ChatColorsPalette.Bubbles.FOREST
      ChatStyle.BubbleColorPreset.SOLID_WINTERGREEN -> ChatColorsPalette.Bubbles.WINTERGREEN
      ChatStyle.BubbleColorPreset.SOLID_TEAL -> ChatColorsPalette.Bubbles.TEAL
      ChatStyle.BubbleColorPreset.SOLID_BLUE -> ChatColorsPalette.Bubbles.BLUE
      ChatStyle.BubbleColorPreset.SOLID_INDIGO -> ChatColorsPalette.Bubbles.INDIGO
      ChatStyle.BubbleColorPreset.SOLID_VIOLET -> ChatColorsPalette.Bubbles.VIOLET
      ChatStyle.BubbleColorPreset.SOLID_PLUM -> ChatColorsPalette.Bubbles.PLUM
      ChatStyle.BubbleColorPreset.SOLID_TAUPE -> ChatColorsPalette.Bubbles.TAUPE
      ChatStyle.BubbleColorPreset.SOLID_STEEL -> ChatColorsPalette.Bubbles.STEEL
      // Gradients
      ChatStyle.BubbleColorPreset.GRADIENT_EMBER -> ChatColorsPalette.Bubbles.EMBER
      ChatStyle.BubbleColorPreset.GRADIENT_MIDNIGHT -> ChatColorsPalette.Bubbles.MIDNIGHT
      ChatStyle.BubbleColorPreset.GRADIENT_INFRARED -> ChatColorsPalette.Bubbles.INFRARED
      ChatStyle.BubbleColorPreset.GRADIENT_LAGOON -> ChatColorsPalette.Bubbles.LAGOON
      ChatStyle.BubbleColorPreset.GRADIENT_FLUORESCENT -> ChatColorsPalette.Bubbles.FLUORESCENT
      ChatStyle.BubbleColorPreset.GRADIENT_BASIL -> ChatColorsPalette.Bubbles.BASIL
      ChatStyle.BubbleColorPreset.GRADIENT_SUBLIME -> ChatColorsPalette.Bubbles.SUBLIME
      ChatStyle.BubbleColorPreset.GRADIENT_SEA -> ChatColorsPalette.Bubbles.SEA
      ChatStyle.BubbleColorPreset.GRADIENT_TANGERINE -> ChatColorsPalette.Bubbles.TANGERINE
      ChatStyle.BubbleColorPreset.UNKNOWN_BUBBLE_COLOR_PRESET, ChatStyle.BubbleColorPreset.SOLID_ULTRAMARINE -> ChatColorsPalette.Bubbles.ULTRAMARINE
    }
  }

  if (this.autoBubbleColor != null) {
    return ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto)
  }

  if (this.customColorId != null) {
    return importState.remoteToLocalColorId[this.customColorId]?.let { localId ->
      val colorId = ChatColors.Id.forLongValue(localId)
      ChatColorsPalette.Bubbles.default.withId(colorId)
    }
  }

  return null
}

fun ChatColors.toRemote(): ChatStyle.BubbleColorPreset? {
  when (this) {
    // Solids
    ChatColorsPalette.Bubbles.CRIMSON -> return ChatStyle.BubbleColorPreset.SOLID_CRIMSON
    ChatColorsPalette.Bubbles.VERMILION -> return ChatStyle.BubbleColorPreset.SOLID_VERMILION
    ChatColorsPalette.Bubbles.BURLAP -> return ChatStyle.BubbleColorPreset.SOLID_BURLAP
    ChatColorsPalette.Bubbles.FOREST -> return ChatStyle.BubbleColorPreset.SOLID_FOREST
    ChatColorsPalette.Bubbles.WINTERGREEN -> return ChatStyle.BubbleColorPreset.SOLID_WINTERGREEN
    ChatColorsPalette.Bubbles.TEAL -> return ChatStyle.BubbleColorPreset.SOLID_TEAL
    ChatColorsPalette.Bubbles.BLUE -> return ChatStyle.BubbleColorPreset.SOLID_BLUE
    ChatColorsPalette.Bubbles.INDIGO -> return ChatStyle.BubbleColorPreset.SOLID_INDIGO
    ChatColorsPalette.Bubbles.VIOLET -> return ChatStyle.BubbleColorPreset.SOLID_VIOLET
    ChatColorsPalette.Bubbles.PLUM -> return ChatStyle.BubbleColorPreset.SOLID_PLUM
    ChatColorsPalette.Bubbles.TAUPE -> return ChatStyle.BubbleColorPreset.SOLID_TAUPE
    ChatColorsPalette.Bubbles.STEEL -> return ChatStyle.BubbleColorPreset.SOLID_STEEL
    ChatColorsPalette.Bubbles.ULTRAMARINE -> return ChatStyle.BubbleColorPreset.SOLID_ULTRAMARINE
    // Gradients
    ChatColorsPalette.Bubbles.EMBER -> return ChatStyle.BubbleColorPreset.GRADIENT_EMBER
    ChatColorsPalette.Bubbles.MIDNIGHT -> return ChatStyle.BubbleColorPreset.GRADIENT_MIDNIGHT
    ChatColorsPalette.Bubbles.INFRARED -> return ChatStyle.BubbleColorPreset.GRADIENT_INFRARED
    ChatColorsPalette.Bubbles.LAGOON -> return ChatStyle.BubbleColorPreset.GRADIENT_LAGOON
    ChatColorsPalette.Bubbles.FLUORESCENT -> return ChatStyle.BubbleColorPreset.GRADIENT_FLUORESCENT
    ChatColorsPalette.Bubbles.BASIL -> return ChatStyle.BubbleColorPreset.GRADIENT_BASIL
    ChatColorsPalette.Bubbles.SUBLIME -> return ChatStyle.BubbleColorPreset.GRADIENT_SUBLIME
    ChatColorsPalette.Bubbles.SEA -> return ChatStyle.BubbleColorPreset.GRADIENT_SEA
    ChatColorsPalette.Bubbles.TANGERINE -> return ChatStyle.BubbleColorPreset.GRADIENT_TANGERINE
  }
  return null
}

fun ChatStyle.WallpaperPreset.toLocal(): ChatWallpaper? {
  return when (this) {
    ChatStyle.WallpaperPreset.SOLID_BLUSH -> SingleColorChatWallpaper.BLUSH
    ChatStyle.WallpaperPreset.SOLID_COPPER -> SingleColorChatWallpaper.COPPER
    ChatStyle.WallpaperPreset.SOLID_DUST -> SingleColorChatWallpaper.DUST
    ChatStyle.WallpaperPreset.SOLID_CELADON -> SingleColorChatWallpaper.CELADON
    ChatStyle.WallpaperPreset.SOLID_RAINFOREST -> SingleColorChatWallpaper.RAINFOREST
    ChatStyle.WallpaperPreset.SOLID_PACIFIC -> SingleColorChatWallpaper.PACIFIC
    ChatStyle.WallpaperPreset.SOLID_FROST -> SingleColorChatWallpaper.FROST
    ChatStyle.WallpaperPreset.SOLID_NAVY -> SingleColorChatWallpaper.NAVY
    ChatStyle.WallpaperPreset.SOLID_LILAC -> SingleColorChatWallpaper.LILAC
    ChatStyle.WallpaperPreset.SOLID_PINK -> SingleColorChatWallpaper.PINK
    ChatStyle.WallpaperPreset.SOLID_EGGPLANT -> SingleColorChatWallpaper.EGGPLANT
    ChatStyle.WallpaperPreset.SOLID_SILVER -> SingleColorChatWallpaper.SILVER
    ChatStyle.WallpaperPreset.GRADIENT_SUNSET -> GradientChatWallpaper.SUNSET
    ChatStyle.WallpaperPreset.GRADIENT_NOIR -> GradientChatWallpaper.NOIR
    ChatStyle.WallpaperPreset.GRADIENT_HEATMAP -> GradientChatWallpaper.HEATMAP
    ChatStyle.WallpaperPreset.GRADIENT_AQUA -> GradientChatWallpaper.AQUA
    ChatStyle.WallpaperPreset.GRADIENT_IRIDESCENT -> GradientChatWallpaper.IRIDESCENT
    ChatStyle.WallpaperPreset.GRADIENT_MONSTERA -> GradientChatWallpaper.MONSTERA
    ChatStyle.WallpaperPreset.GRADIENT_BLISS -> GradientChatWallpaper.BLISS
    ChatStyle.WallpaperPreset.GRADIENT_SKY -> GradientChatWallpaper.SKY
    ChatStyle.WallpaperPreset.GRADIENT_PEACH -> GradientChatWallpaper.PEACH
    else -> null
  }
}

fun ChatStyle.parseChatWallpaper(wallpaperAttachmentId: AttachmentId?): ChatWallpaper? {
  val chatWallpaper = if (this.wallpaperPreset != null) {
    this.wallpaperPreset.toLocal()
  } else if (wallpaperAttachmentId != null) {
    UriChatWallpaper(PartAuthority.getAttachmentDataUri(wallpaperAttachmentId), 0f)
  } else {
    null
  }

  return if (chatWallpaper != null && this.dimWallpaperInDarkMode) {
    ChatWallpaperFactory.updateWithDimming(chatWallpaper, ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME)
  } else {
    chatWallpaper
  }
}

private fun Int.toRemoteWallpaperPreset(): ChatStyle.WallpaperPreset {
  return when (this) {
    SingleColorChatWallpaper.BLUSH.color -> ChatStyle.WallpaperPreset.SOLID_BLUSH
    SingleColorChatWallpaper.COPPER.color -> ChatStyle.WallpaperPreset.SOLID_COPPER
    SingleColorChatWallpaper.DUST.color -> ChatStyle.WallpaperPreset.SOLID_DUST
    SingleColorChatWallpaper.CELADON.color -> ChatStyle.WallpaperPreset.SOLID_CELADON
    SingleColorChatWallpaper.RAINFOREST.color -> ChatStyle.WallpaperPreset.SOLID_RAINFOREST
    SingleColorChatWallpaper.PACIFIC.color -> ChatStyle.WallpaperPreset.SOLID_PACIFIC
    SingleColorChatWallpaper.FROST.color -> ChatStyle.WallpaperPreset.SOLID_FROST
    SingleColorChatWallpaper.NAVY.color -> ChatStyle.WallpaperPreset.SOLID_NAVY
    SingleColorChatWallpaper.LILAC.color -> ChatStyle.WallpaperPreset.SOLID_LILAC
    SingleColorChatWallpaper.PINK.color -> ChatStyle.WallpaperPreset.SOLID_PINK
    SingleColorChatWallpaper.EGGPLANT.color -> ChatStyle.WallpaperPreset.SOLID_EGGPLANT
    SingleColorChatWallpaper.SILVER.color -> ChatStyle.WallpaperPreset.SOLID_SILVER
    else -> ChatStyle.WallpaperPreset.UNKNOWN_WALLPAPER_PRESET
  }
}

private fun Wallpaper.LinearGradient.toRemoteWallpaperPreset(): ChatStyle.WallpaperPreset {
  val colorArray = colors.toIntArray()
  return when {
    colorArray contentEquals GradientChatWallpaper.SUNSET.colors -> ChatStyle.WallpaperPreset.GRADIENT_SUNSET
    colorArray contentEquals GradientChatWallpaper.NOIR.colors -> ChatStyle.WallpaperPreset.GRADIENT_NOIR
    colorArray contentEquals GradientChatWallpaper.HEATMAP.colors -> ChatStyle.WallpaperPreset.GRADIENT_HEATMAP
    colorArray contentEquals GradientChatWallpaper.AQUA.colors -> ChatStyle.WallpaperPreset.GRADIENT_AQUA
    colorArray contentEquals GradientChatWallpaper.IRIDESCENT.colors -> ChatStyle.WallpaperPreset.GRADIENT_IRIDESCENT
    colorArray contentEquals GradientChatWallpaper.MONSTERA.colors -> ChatStyle.WallpaperPreset.GRADIENT_MONSTERA
    colorArray contentEquals GradientChatWallpaper.BLISS.colors -> ChatStyle.WallpaperPreset.GRADIENT_BLISS
    colorArray contentEquals GradientChatWallpaper.SKY.colors -> ChatStyle.WallpaperPreset.GRADIENT_SKY
    colorArray contentEquals GradientChatWallpaper.PEACH.colors -> ChatStyle.WallpaperPreset.GRADIENT_PEACH
    else -> ChatStyle.WallpaperPreset.UNKNOWN_WALLPAPER_PRESET
  }
}

private fun Wallpaper.File.toFilePointer(db: SignalDatabase): FilePointer? {
  val attachmentId: AttachmentId = UriUtil.parseOrNull(this.uri)?.let { PartUriParser(it).partId } ?: return null
  val attachment = db.attachmentTable.getAttachment(attachmentId)
  return attachment?.toRemoteFilePointer(mediaArchiveEnabled = true)
}
