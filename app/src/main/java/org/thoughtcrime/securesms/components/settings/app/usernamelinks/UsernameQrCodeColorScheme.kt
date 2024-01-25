package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.compose.ui.graphics.Color

/**
 * A set of color schemes for sharing QR codes.
 */
enum class UsernameQrCodeColorScheme(
  val borderColor: Color,
  val foregroundColor: Color,
  val backgroundColor: Color,
  val textColor: Color = Color.White,
  val outlineColor: Color = Color.Transparent,
  private val key: String
) {
  Blue(
    borderColor = Color(0xFF506ECD),
    foregroundColor = Color(0xFF2449C0),
    backgroundColor = Color(0xFFEDF0FA),
    key = "blue"
  ),
  White(
    borderColor = Color(0xFFFFFFFF),
    foregroundColor = Color(0xFF000000),
    backgroundColor = Color(0xFFF5F5F5),
    textColor = Color.Black,
    outlineColor = Color(0xFFE9E9E9),
    key = "white"
  ),
  Grey(
    borderColor = Color(0xFF6A6C74),
    foregroundColor = Color(0xFF464852),
    backgroundColor = Color(0xFFF0F0F1),
    key = "grey"
  ),
  Tan(
    borderColor = Color(0xFFBBB29A),
    foregroundColor = Color(0xFF73694F),
    backgroundColor = Color(0xFFF6F5F2),
    key = "tan"
  ),
  Green(
    borderColor = Color(0xFF97AA89),
    foregroundColor = Color(0xFF55733F),
    backgroundColor = Color(0xFFF2F5F0),
    key = "green"
  ),
  Orange(
    borderColor = Color(0xFFDE7134),
    foregroundColor = Color(0xFFDA6C2E),
    backgroundColor = Color(0xFFFCF1EB),
    key = "orange"
  ),
  Pink(
    borderColor = Color(0xFFEA7B9D),
    foregroundColor = Color(0xFFBB617B),
    backgroundColor = Color(0xFFFCF1F5),
    key = "pink"
  ),
  Purple(
    borderColor = Color(0xFF9E7BE9),
    foregroundColor = Color(0xFF7651C5),
    backgroundColor = Color(0xFFF5F3FA),
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
