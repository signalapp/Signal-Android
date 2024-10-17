package org.thoughtcrime.securesms.components.settings.app.subscription.errors

/**
 * Descriptor of where an error originated from.
 */
enum class DonationErrorSource(private val code: String) {
  /**
   * Refers to a one-time donation where the user paying receives a badge immediately.
   */
  ONE_TIME("boost"),

  /**
   * Refers to a recurring monthly donation where the user paying receives a badge immediately
   * and upon each renewal period.
   */
  MONTHLY("subscription"),

  /**
   * Refers to a one-time donation where the user pays to send a badge to another individual.
   */
  GIFT("gift"),

  /**
   * Refers to when the individual who received a gift token is redeeming it for a badge.
   */
  GIFT_REDEMPTION("gift-redemption"),

  /**
   * Refers to the monthly keep-alive job for an active monthly subscriber, which is started in
   * the background as needed.
   */
  KEEP_ALIVE("keep-alive"),

  /**
   * Refers to backup payments.
   */
  BACKUPS("backups"),

  UNKNOWN("unknown");

  fun serialize(): String = code

  companion object {
    @JvmStatic
    fun deserialize(code: String): DonationErrorSource {
      return values().firstOrNull { it.code == code } ?: UNKNOWN
    }
  }
}
