/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.CallLinkUpdateSendJob
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkManager
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult

/**
 * Repository for performing update operations on call links:
 * <ul>
 *   <li>Set name</li>
 *   <li>Set restrictions</li>
 *   <li>Revoke link</li>
 * </ul>
 *
 * All of these will delegate to the [SignalCallLinkManager] but will additionally update the database state.
 */
class UpdateCallLinkRepository(
  private val callLinkManager: SignalCallLinkManager = ApplicationDependencies.getSignalCallManager().callLinkManager
) {
  fun setCallName(credentials: CallLinkCredentials, name: String): Single<UpdateCallLinkResult> {
    return callLinkManager
      .updateCallLinkName(
        credentials = credentials,
        name = name
      )
      .doOnSuccess(updateState(credentials))
      .subscribeOn(Schedulers.io())
  }

  fun setCallRestrictions(credentials: CallLinkCredentials, restrictions: CallLinkState.Restrictions): Single<UpdateCallLinkResult> {
    return callLinkManager
      .updateCallLinkRestrictions(
        credentials = credentials,
        restrictions = restrictions
      )
      .doOnSuccess(updateState(credentials))
      .subscribeOn(Schedulers.io())
  }

  fun revokeCallLink(credentials: CallLinkCredentials): Single<UpdateCallLinkResult> {
    return callLinkManager
      .updateCallLinkRevoked(credentials, true)
      .doOnSuccess(updateState(credentials))
      .subscribeOn(Schedulers.io())
  }

  private fun updateState(credentials: CallLinkCredentials): (UpdateCallLinkResult) -> Unit {
    return { result ->
      if (result is UpdateCallLinkResult.Success) {
        SignalDatabase.callLinks.updateCallLinkState(credentials.roomId, result.state)
        ApplicationDependencies.getJobManager().add(CallLinkUpdateSendJob(credentials.roomId))
      }
    }
  }
}
