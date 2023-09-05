/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.viewmodel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.whispersystems.signalservice.internal.push.AuthCredentials

@Parcelize
data class SvrAuthCredentialSet(
  private val svr1Credentials: ParcelableAuthCredentials?,
  private val svr2Credentials: ParcelableAuthCredentials?
) : Parcelable {
  constructor(
    svr1Credentials: AuthCredentials?,
    svr2Credentials: AuthCredentials?
  ) : this(ParcelableAuthCredentials.createOrNull(svr1Credentials), ParcelableAuthCredentials.createOrNull(svr2Credentials))

  val svr1: AuthCredentials? = svr1Credentials?.credentials()
  val svr2: AuthCredentials? = svr2Credentials?.credentials()

  @Parcelize
  data class ParcelableAuthCredentials(private val username: String, private val password: String) : Parcelable {

    companion object {
      fun createOrNull(creds: AuthCredentials?): ParcelableAuthCredentials? {
        return if (creds != null) {
          ParcelableAuthCredentials(creds.username(), creds.password())
        } else {
          null
        }
      }
    }

    fun credentials(): AuthCredentials {
      return AuthCredentials.create(username, password)
    }
  }
}
