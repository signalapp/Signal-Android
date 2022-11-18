package org.thoughtcrime.securesms.conversation.colors

import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import com.google.common.collect.ImmutableMap
import org.thoughtcrime.securesms.color.MaterialColor
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.GradientChatWallpaper
import org.thoughtcrime.securesms.wallpaper.SingleColorChatWallpaper

/**
 * Contains mappings to get the relevant chat colors for either a legacy MaterialColor or a built-in wallpaper.
 */
object ChatColorsMapper {

  private val materialColorToChatColorsBiMap: BiMap<MaterialColor, ChatColors> = ImmutableBiMap.Builder<MaterialColor, ChatColors>().apply {
    put(MaterialColor.CRIMSON, ChatColorsPalette.Bubbles.CRIMSON)
    put(MaterialColor.VERMILLION, ChatColorsPalette.Bubbles.VERMILION)
    put(MaterialColor.BURLAP, ChatColorsPalette.Bubbles.BURLAP)
    put(MaterialColor.FOREST, ChatColorsPalette.Bubbles.FOREST)
    put(MaterialColor.WINTERGREEN, ChatColorsPalette.Bubbles.WINTERGREEN)
    put(MaterialColor.TEAL, ChatColorsPalette.Bubbles.TEAL)
    put(MaterialColor.BLUE, ChatColorsPalette.Bubbles.BLUE)
    put(MaterialColor.INDIGO, ChatColorsPalette.Bubbles.INDIGO)
    put(MaterialColor.VIOLET, ChatColorsPalette.Bubbles.VIOLET)
    put(MaterialColor.PLUM, ChatColorsPalette.Bubbles.PLUM)
    put(MaterialColor.TAUPE, ChatColorsPalette.Bubbles.TAUPE)
    put(MaterialColor.STEEL, ChatColorsPalette.Bubbles.STEEL)
    put(MaterialColor.ULTRAMARINE, ChatColorsPalette.Bubbles.ULTRAMARINE)
  }.build()

  private val wallpaperToChatColorsMap: Map<ChatWallpaper, ChatColors> = ImmutableMap.Builder<ChatWallpaper, ChatColors>().apply {
    put(SingleColorChatWallpaper.BLUSH, ChatColorsPalette.Bubbles.CRIMSON)
    put(SingleColorChatWallpaper.COPPER, ChatColorsPalette.Bubbles.VERMILION)
    put(SingleColorChatWallpaper.DUST, ChatColorsPalette.Bubbles.BURLAP)
    put(SingleColorChatWallpaper.CELADON, ChatColorsPalette.Bubbles.FOREST)
    put(SingleColorChatWallpaper.RAINFOREST, ChatColorsPalette.Bubbles.WINTERGREEN)
    put(SingleColorChatWallpaper.PACIFIC, ChatColorsPalette.Bubbles.TEAL)
    put(SingleColorChatWallpaper.FROST, ChatColorsPalette.Bubbles.BLUE)
    put(SingleColorChatWallpaper.NAVY, ChatColorsPalette.Bubbles.INDIGO)
    put(SingleColorChatWallpaper.LILAC, ChatColorsPalette.Bubbles.VIOLET)
    put(SingleColorChatWallpaper.PINK, ChatColorsPalette.Bubbles.PLUM)
    put(SingleColorChatWallpaper.EGGPLANT, ChatColorsPalette.Bubbles.TAUPE)
    put(SingleColorChatWallpaper.SILVER, ChatColorsPalette.Bubbles.STEEL)
    put(GradientChatWallpaper.SUNSET, ChatColorsPalette.Bubbles.EMBER)
    put(GradientChatWallpaper.NOIR, ChatColorsPalette.Bubbles.MIDNIGHT)
    put(GradientChatWallpaper.HEATMAP, ChatColorsPalette.Bubbles.INFRARED)
    put(GradientChatWallpaper.AQUA, ChatColorsPalette.Bubbles.LAGOON)
    put(GradientChatWallpaper.IRIDESCENT, ChatColorsPalette.Bubbles.FLUORESCENT)
    put(GradientChatWallpaper.MONSTERA, ChatColorsPalette.Bubbles.BASIL)
    put(GradientChatWallpaper.BLISS, ChatColorsPalette.Bubbles.SUBLIME)
    put(GradientChatWallpaper.SKY, ChatColorsPalette.Bubbles.SEA)
    put(GradientChatWallpaper.PEACH, ChatColorsPalette.Bubbles.TANGERINE)
  }.build()

  @JvmStatic
  val entrySet: Set<MutableMap.MutableEntry<MaterialColor, ChatColors>>
    get() = materialColorToChatColorsBiMap.entries

  @JvmStatic
  fun getChatColors(materialColor: MaterialColor): ChatColors {
    return materialColorToChatColorsBiMap[materialColor] ?: ChatColorsPalette.Bubbles.default
  }

  @JvmStatic
  fun getChatColors(wallpaper: ChatWallpaper): ChatColors {
    return wallpaperToChatColorsMap.entries.find { (key, _) ->
      key.isSameSource(wallpaper)
    }?.value ?: ChatColorsPalette.Bubbles.default
  }

  @JvmStatic
  fun getMaterialColor(chatColors: ChatColors): MaterialColor {
    return materialColorToChatColorsBiMap.inverse()[chatColors] ?: MaterialColor.ULTRAMARINE
  }
}
