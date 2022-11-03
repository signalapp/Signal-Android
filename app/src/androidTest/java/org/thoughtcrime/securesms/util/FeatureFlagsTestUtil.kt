package org.thoughtcrime.securesms.util

/**
 * Utility to enable / disable feature flags via forced values.
 */
object FeatureFlagsTestUtil {
  fun setStoriesEnabled(isEnabled: Boolean) {
    FeatureFlags.FORCED_VALUES[FeatureFlags.STORIES] = isEnabled
  }
}
