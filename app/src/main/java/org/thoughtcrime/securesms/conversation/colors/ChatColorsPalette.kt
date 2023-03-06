package org.thoughtcrime.securesms.conversation.colors

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
    val all = listOf(
      NameColor(lightColor = 0xFF006DA3.toInt(), darkColor = 0xFF00A7FA.toInt()),
      NameColor(lightColor = 0xFF067906.toInt(), darkColor = 0xFF0AB80A.toInt()),
      NameColor(lightColor = 0xFFB814B8.toInt(), darkColor = 0xFFF65AF6.toInt()),
      NameColor(lightColor = 0xFFC13215.toInt(), darkColor = 0xFFFF6F52.toInt()),
      NameColor(lightColor = 0xFF5B6976.toInt(), darkColor = 0xFF8BA1B6.toInt()),
      NameColor(lightColor = 0xFFCC0066.toInt(), darkColor = 0xFFF76EB2.toInt()),
      NameColor(lightColor = 0xFF2E51FF.toInt(), darkColor = 0xFF8599FF.toInt()),
      NameColor(lightColor = 0xFF007575.toInt(), darkColor = 0xFF00B2B2.toInt()),
      NameColor(lightColor = 0xFF9C5711.toInt(), darkColor = 0xFFD5920B.toInt()),
      NameColor(lightColor = 0xFFD00B4D.toInt(), darkColor = 0xFFFF6B9C.toInt()),
      NameColor(lightColor = 0xFF8F2AF4.toInt(), darkColor = 0xFFBF80FF.toInt()),
      NameColor(lightColor = 0xFF3D7406.toInt(), darkColor = 0xFF5EB309.toInt()),
      NameColor(lightColor = 0xFFD00B0B.toInt(), darkColor = 0xFFFF7070.toInt()),
      NameColor(lightColor = 0xFF007A3D.toInt(), darkColor = 0xFF00B85C.toInt()),
      NameColor(lightColor = 0xFF5151F6.toInt(), darkColor = 0xFF9494FF.toInt()),
      NameColor(lightColor = 0xFF866118.toInt(), darkColor = 0xFFD68F00.toInt()),
      NameColor(lightColor = 0xFF067953.toInt(), darkColor = 0xFF00B87A.toInt()),
      NameColor(lightColor = 0xFFA20CED.toInt(), darkColor = 0xFFCF7CF8.toInt()),
      NameColor(lightColor = 0xFF4B7000.toInt(), darkColor = 0xFF74AD00.toInt()),
      NameColor(lightColor = 0xFFC70A88.toInt(), darkColor = 0xFFF76EC9.toInt()),
      NameColor(lightColor = 0xFFB34209.toInt(), darkColor = 0xFFF57A3D.toInt()),
      NameColor(lightColor = 0xFF06792D.toInt(), darkColor = 0xFF0AB844.toInt()),
      NameColor(lightColor = 0xFF7A3DF5.toInt(), darkColor = 0xFFAF8AF9.toInt()),
      NameColor(lightColor = 0xFF6B6B24.toInt(), darkColor = 0xFFA4A437.toInt()),
      NameColor(lightColor = 0xFFD00B2C.toInt(), darkColor = 0xFFF77389.toInt()),
      NameColor(lightColor = 0xFF2D7906.toInt(), darkColor = 0xFF42B309.toInt()),
      NameColor(lightColor = 0xFFAF0BD0.toInt(), darkColor = 0xFFE06EF7.toInt()),
      NameColor(lightColor = 0xFF32763E.toInt(), darkColor = 0xFF4BAF5C.toInt()),
      NameColor(lightColor = 0xFF2662D9.toInt(), darkColor = 0xFF7DA1E8.toInt()),
      NameColor(lightColor = 0xFF76681E.toInt(), darkColor = 0xFFB89B0A.toInt()),
      NameColor(lightColor = 0xFF067462.toInt(), darkColor = 0xFF09B397.toInt()),
      NameColor(lightColor = 0xFF6447F5.toInt(), darkColor = 0xFFA18FF9.toInt()),
      NameColor(lightColor = 0xFF5E6E0C.toInt(), darkColor = 0xFF8FAA09.toInt()),
      NameColor(lightColor = 0xFF077288.toInt(), darkColor = 0xFF00AED1.toInt()),
      NameColor(lightColor = 0xFFC20AA3.toInt(), darkColor = 0xFFF75FDD.toInt()),
      NameColor(lightColor = 0xFF2D761E.toInt(), darkColor = 0xFF43B42D.toInt())
    )
  }

  @JvmField
  val UNKNOWN_CONTACT = Bubbles.STEEL
}
