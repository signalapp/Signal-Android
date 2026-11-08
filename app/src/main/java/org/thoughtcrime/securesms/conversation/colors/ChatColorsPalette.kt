package org.thoughtcrime.securesms.conversation.colors

import androidx.compose.ui.graphics.toArgb
import org.signal.core.ui.compose.theme.NameColors

/**
 * Namespaced collection of supported bubble colors and name colors.
 */
object ChatColorsPalette {
  object Bubbles {

    // region Default

    @JvmField
    val ULTRAMARINE = ChatColors.forColor(
      ChatColors.Id.BuiltIn,
      0xFF315FF4.toInt()
    )

    // endregion

    // region Solids

    @JvmField
    val CRIMSON = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFFCF163E.toInt())

    @JvmField
    val VERMILION = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFFC73F0A.toInt())

    @JvmField
    val BURLAP = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF6F6A58.toInt())

    @JvmField
    val FOREST = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF3B7845.toInt())

    @JvmField
    val WINTERGREEN = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF1D8663.toInt())

    @JvmField
    val TEAL = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF077D92.toInt())

    @JvmField
    val BLUE = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF336BA3.toInt())

    @JvmField
    val INDIGO = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF6058CA.toInt())

    @JvmField
    val VIOLET = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFF9932CB.toInt())

    @JvmField
    val PLUM = ChatColors.forColor(ChatColors.Id.BuiltIn, 0xFFAA377A.toInt())

    @JvmField
    val TAUPE = ChatColors.forColor(
      ChatColors.Id.BuiltIn,
      0xFF8F616A.toInt()
    )

    @JvmField
    val STEEL = ChatColors.forColor(
      ChatColors.Id.BuiltIn,
      0xFF71717F.toInt()
    )

    // endregion

    // region Gradients

    @JvmField
    val EMBER = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        168f,
        intArrayOf(0xFFE57C00.toInt(), 0xFF5E0000.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val MIDNIGHT = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        180f,
        intArrayOf(0xFF2C2C3A.toInt(), 0xFF787891.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val INFRARED = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        192f,
        intArrayOf(0xFFF65560.toInt(), 0xFF442CED.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val LAGOON = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        180f,
        intArrayOf(0xFF004066.toInt(), 0xFF32867D.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val FLUORESCENT = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        192f,
        intArrayOf(0xFFEC13DD.toInt(), 0xFF1B36C6.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val BASIL = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        180f,
        intArrayOf(0xFF2F9373.toInt(), 0xFF077343.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val SUBLIME = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        180f,
        intArrayOf(
          0xFF6281D5.toInt(),
          0xFF974460.toInt()
        ),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val SEA = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        180f,
        intArrayOf(0xFF498FD4.toInt(), 0xFF2C66A0.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    @JvmField
    val TANGERINE = ChatColors.forGradient(
      ChatColors.Id.BuiltIn,
      ChatColors.LinearGradient(
        192f,
        intArrayOf(0xFFDB7133.toInt(), 0xFF911231.toInt()),
        floatArrayOf(0f, 1f)
      )
    )

    // endregion

    @JvmStatic
    val default = ULTRAMARINE

    /*
     * If updating this list of colors, make sure to update the backup import/export colors as well.
     */
    val solids = listOf(
      CRIMSON,
      VERMILION,
      BURLAP,
      FOREST,
      WINTERGREEN,
      TEAL,
      BLUE,
      INDIGO,
      VIOLET,
      PLUM,
      TAUPE,
      STEEL
    )
    val gradients =
      listOf(EMBER, MIDNIGHT, INFRARED, LAGOON, FLUORESCENT, BASIL, SUBLIME, SEA, TANGERINE)
    val all = listOf(default) + solids + gradients
  }

  object Names {
    @JvmStatic
    val all = NameColors.all.map { NameColor(lightColor = it.light.toArgb(), darkColor = it.dark.toArgb()) }
  }

  @JvmField
  val UNKNOWN_CONTACT = Bubbles.STEEL
}
