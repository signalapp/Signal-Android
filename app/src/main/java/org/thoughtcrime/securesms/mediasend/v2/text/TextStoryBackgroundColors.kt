package org.thoughtcrime.securesms.mediasend.v2.text

import org.thoughtcrime.securesms.conversation.colors.ChatColors

object TextStoryBackgroundColors {

  private val backgroundColors: List<ChatColors> = listOf(
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 191.41f,
        colors = intArrayOf(0xFFF53844.toInt(), 0xFFF33845.toInt(), 0xFFEC3848.toInt(), 0xFFE2384C.toInt(), 0xFFD63851.toInt(), 0xFFC73857.toInt(), 0xFFB6385E.toInt(), 0xFFA43866.toInt(), 0xFF93376D.toInt(), 0xFF813775.toInt(), 0xFF70377C.toInt(), 0xFF613782.toInt(), 0xFF553787.toInt(), 0xFF4B378B.toInt(), 0xFF44378E.toInt(), 0xFF42378F.toInt()),
        positions = floatArrayOf(0.2109f, 0.2168f, 0.2339f, 0.2611f, 0.2975f, 0.3418f, 0.3932f, 0.4506f, 0.5129f, 0.5791f, 0.6481f, 0.719f, 0.7907f, 0.8621f, 0.9322f, 1.0f)
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 192.04f,
        colors = intArrayOf(0xFFF04CE6.toInt(), 0xFFEE4BE6.toInt(), 0xFFE54AE5.toInt(), 0xFFD949E5.toInt(), 0xFFC946E4.toInt(), 0xFFB644E3.toInt(), 0xFFA141E3.toInt(), 0xFF8B3FE2.toInt(), 0xFF743CE1.toInt(), 0xFF5E39E0.toInt(), 0xFF4936DF.toInt(), 0xFF3634DE.toInt(), 0xFF2632DD.toInt(), 0xFF1930DD.toInt(), 0xFF112FDD.toInt(), 0xFF0E2FDD.toInt()),
        positions = floatArrayOf(0.0f, 0.0807f, 0.1554f, 0.225f, 0.2904f, 0.3526f, 0.4125f, 0.471f, 0.529f, 0.5875f, 0.6474f, 0.7096f, 0.775f, 0.8446f, 0.9193f, 1.0f)
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
        colors = intArrayOf(0xFF0093E9.toInt(), 0xFF0294E9.toInt(), 0xFF0696E7.toInt(), 0xFF0D99E5.toInt(), 0xFF169EE3.toInt(), 0xFF21A3E0.toInt(), 0xFF2DA8DD.toInt(), 0xFF3AAEDA.toInt(), 0xFF46B5D6.toInt(), 0xFF53BBD3.toInt(), 0xFF5FC0D0.toInt(), 0xFF6AC5CD.toInt(), 0xFF73CACB.toInt(), 0xFF7ACDC9.toInt(), 0xFF7ECFC7.toInt(), 0xFF80D0C7.toInt()),
        positions = floatArrayOf(0.0f, 0.0807f, 0.1554f, 0.225f, 0.2904f, 0.3526f, 0.4125f, 0.471f, 0.529f, 0.5875f, 0.6474f, 0.7096f, 0.775f, 0.8446f, 0.9193f, 1.0f)
      )
    ),
    ChatColors.forGradient(
      id = ChatColors.Id.NotSet,
      linearGradient = ChatColors.LinearGradient(
        degrees = 180f,
        colors = intArrayOf(0xFF65CDAC.toInt(), 0xFF64CDAB.toInt(), 0xFF60CBA8.toInt(), 0xFF5BC8A3.toInt(), 0xFF55C49D.toInt(), 0xFF4DC096.toInt(), 0xFF45BB8F.toInt(), 0xFF3CB687.toInt(), 0xFF33B17F.toInt(), 0xFF2AAC76.toInt(), 0xFF21A76F.toInt(), 0xFF1AA268.toInt(), 0xFF139F62.toInt(), 0xFF0E9C5E.toInt(), 0xFF0B9A5B.toInt(), 0xFF0A995A.toInt()),
        positions = floatArrayOf(0.0f, 0.0807f, 0.1554f, 0.225f, 0.2904f, 0.3526f, 0.4125f, 0.471f, 0.529f, 0.5875f, 0.6474f, 0.7096f, 0.775f, 0.8446f, 0.9193f, 1.0f)
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
}
