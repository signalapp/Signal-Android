package org.thoughtcrime.securesms.mediasend.v2.text

import org.thoughtcrime.securesms.conversation.colors.ChatColors

object TextStoryBackgroundColors {

  private val backgroundColors: List<ChatColors> = listOf(
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF688BD4.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF8687C1.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFB47F8C.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF899188.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF539383.toInt()
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        colors = intArrayOf(
          0xFF19A9FA.toInt(),
          0xFF7097D7.toInt(),
          0xFFD1998D.toInt(),
          0xFFFFC369.toInt()
        ),
        positions = floatArrayOf(
          0f,
          0.33f,
          0.66f,
          1f
        ),
        degrees = 180f
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        colors = intArrayOf(
          0xFF4437D8.toInt(),
          0xFF6B70DE.toInt(),
          0xFFB774E0.toInt(),
          0xFFFF8E8E.toInt()
        ),
        positions = floatArrayOf(
          0f,
          0.33f,
          0.66f,
          1f
        ),
        degrees = 180f
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        colors = intArrayOf(
          0xFF004044.toInt(),
          0xFF2C5F45.toInt(),
          0xFF648E52.toInt(),
          0xFF93B864.toInt()
        ),
        positions = floatArrayOf(
          0f,
          0.33f,
          0.66f,
          1f
        ),
        degrees = 180f
      )
    )
  )

  fun getInitialBackgroundColor(): ChatColors = backgroundColors.first()

  fun cycleBackgroundColor(chatColors: ChatColors): ChatColors {
    val indexOfNextColor = (backgroundColors.indexOf(chatColors) + 1) % backgroundColors.size

    return backgroundColors[indexOfNextColor]
  }

  @JvmStatic
  fun getRandomBackgroundColor() = backgroundColors.random()
}
