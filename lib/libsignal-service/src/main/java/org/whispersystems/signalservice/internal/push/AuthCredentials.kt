/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.Credentials

class AuthCredentials(
  @field:JsonProperty private val username: String,
  @field:JsonProperty private val password: String
) {
  fun asBasic(): String = Credentials.basic(username, password)

  fun username(): String = username

  fun password(): String = password

  override fun toString(): String = "AuthCredentials(xxx)"

  companion object {
    @JvmStatic
    fun create(username: String, password: String): AuthCredentials = AuthCredentials(username, password)
  }
}
