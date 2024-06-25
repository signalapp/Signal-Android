/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.viewmodel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials

@Parcelize
data class SvrAuthCredentialSet(
  private val svr2Credentials: ParcelableAuthCredentials?,
  private val svr3Credentials: ParcelableSvr3AuthCredentials?
) : Parcelable {
  constructor(
    svr2Credentials: AuthCredentials?,
    svr3Credentials: Svr3Credentials?
  ) : this(
    ParcelableAuthCredentials.createOrNull(svr2Credentials),
    ParcelableSvr3AuthCredentials.createOrNull(svr3Credentials)
  )

  val svr2: AuthCredentials? = svr2Credentials?.credentials()
  val svr3: Svr3Credentials? = svr3Credentials?.credentials()

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

  @Parcelize
  data class ParcelableSvr3AuthCredentials(private val username: String, private val password: String, private val shareSet: ByteArray?) : Parcelable {

    companion object {
      fun createOrNull(creds: Svr3Credentials?): ParcelableSvr3AuthCredentials? {
        return if (creds != null) {
          ParcelableSvr3AuthCredentials(creds.username, creds.password, creds.shareSet)
        } else {
          null
        }
      }
    }

    fun credentials(): Svr3Credentials {
      return Svr3Credentials(username, password, shareSet)
    }
  }
}
