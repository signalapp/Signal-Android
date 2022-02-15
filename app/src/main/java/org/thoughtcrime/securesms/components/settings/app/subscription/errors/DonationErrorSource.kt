package org.thoughtcrime.securesms.components.settings.app.subscription.errors

enum class DonationErrorSource(private val code: String) {
  BOOST("boost"),
  SUBSCRIPTION("subscription"),
  KEEP_ALIVE("keep-alive"),
  UNKNOWN("unknown");

  fun serialize(): String = code

  companion object {
    @JvmStatic
    fun deserialize(code: String): DonationErrorSource {
      return values().firstOrNull { it.code == code } ?: UNKNOWN
    }
  }
}
