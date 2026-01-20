/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  id("kotlin-parcelize")
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "org.signal.core.models"
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.jackson.core)
}
