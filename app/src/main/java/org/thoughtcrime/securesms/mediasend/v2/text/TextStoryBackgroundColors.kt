package org.thoughtcrime.securesms.mediasend.v2.text

import org.thoughtcrime.securesms.conversation.colors.ChatColors

object TextStoryBackgroundColors {

  private val backgroundColors: List<ChatColors> = listOf(
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 191.41f,
        colors = intArrayOf(0xFFF53844.toInt(), 0xFF42378F.toInt()),
        positions = floatArrayOf(0f, 1.0f)
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 192.04f,
        colors = intArrayOf(0xFFF04CE6.toInt(), 0xFF0E2FDD.toInt()),
        positions = floatArrayOf(0.0f, 1.0f)
      ),
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 175.46f,
        colors = intArrayOf(0xFFFFC044.toInt(), 0xFFFE5C38.toInt()),
        positions = floatArrayOf(0f, 1f)
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 180f,
        colors = intArrayOf(0xFF0093E9.toInt(), 0xFF80D0C7.toInt()),
        positions = floatArrayOf(0.0f, 1.0f)
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 180f,
        colors = intArrayOf(0xFF65CDAC.toInt(), 0xFF0A995A.toInt()),
        positions = floatArrayOf(0.0f, 1.0f)
      )
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFFFC153.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFCCBD33.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF84712E.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF09B37B.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF8B8BF9.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF5151F6.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFF76E6E.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFC84641.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFC6C4A5.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFFA49595.toInt()
    ),
    ChatColors.forColor(
      id = ChatColors.Id.NotSet,
      color = 0xFF292929.toInt()
    ),
  )

  fun getInitialBackgroundColor(): ChatColors = backgroundColors.first()

  fun cycleBackgroundColor(chatColors: ChatColors): ChatColors {
    val indexOfNextColor = (backgroundColors.indexOf(chatColors) + 1) % backgroundColors.size

    return backgroundColors[indexOfNextColor]
  }

  @JvmStatic
  fun getRandomBackgroundColor() = backgroundColors.random()
}
