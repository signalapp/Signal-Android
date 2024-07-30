/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.util

import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.ChatStyle
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette

// TODO [backup] Passing in chatColorId probably unnecessary. Only stored as separate column in recipient table for querying, I believe.
object BackupConverters {
  fun constructRemoteChatStyle(chatColors: ChatColors?, chatColorId: ChatColors.Id): ChatStyle? {
    var chatStyleBuilder: ChatStyle.Builder? = null

    if (chatColors != null) {
      chatStyleBuilder = ChatStyle.Builder()
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

    // TODO [backup] wallpaper

    return chatStyleBuilder?.build()
  }
}

fun ChatStyle.toLocal(importState: ImportState): ChatColors? {
  if (this.bubbleColorPreset != null) {
    return when (this.bubbleColorPreset) {
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
