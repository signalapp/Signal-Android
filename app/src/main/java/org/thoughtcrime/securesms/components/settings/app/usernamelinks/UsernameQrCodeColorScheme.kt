package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.compose.ui.graphics.Color

/**
 * A set of color schemes for sharing QR codes.
 */
enum class UsernameQrCodeColorScheme(
  val borderColor: Color,
  val foregroundColor: Color,
  val textColor: Color = Color.White,
  val outlineColor: Color = Color.Transparent,
  private val key: String
) {
  Blue(
    borderColor = Color(0xFF506ECD),
    foregroundColor = Color(0xFF2449C0),
    key = "blue"
  ),
  White(
    borderColor = Color(0xFFFFFFFF),
    foregroundColor = Color(0xFF000000),
    textColor = Color.Black,
    outlineColor = Color(0xFFE9E9E9),
    key = "white"
  ),
  Grey(
    borderColor = Color(0xFF6A6C74),
    foregroundColor = Color(0xFF464852),
    key = "grey"
  ),
  Tan(
    borderColor = Color(0xFFBBB29A),
    foregroundColor = Color(0xFF73694F),
    key = "tan"
  ),
  Green(
    borderColor = Color(0xFF97AA89),
    foregroundColor = Color(0xFF55733F),
    key = "green"
  ),
  Orange(
    borderColor = Color(0xFFDE7134),
    foregroundColor = Color(0xFFDA6C2E),
    key = "orange"
  ),
  Pink(
    borderColor = Color(0xFFEA7B9D),
    foregroundColor = Color(0xFFBB617B),
    key = "pink"
  ),
  Purple(
    borderColor = Color(0xFF9E7BE9),
    foregroundColor = Color(0xFF7651C5),
    key = "purple"
  );

  fun serialize(): String {
    return key
  }

  companion object {
    /**
     * Returns the [UsernameQrCodeColorScheme] based on the serialized string. If no match is found, the default of [Blue] is returned.
     */
    @JvmStatic
    fun deserialize(serialized: String?): UsernameQrCodeColorScheme {
      return values().firstOrNull { it.key == serialized } ?: Blue
    }
  }
}
