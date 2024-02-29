/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.toOptional
import org.signal.qr.QrProcessor
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * A collection of functions to help with scanning QR codes for usernames.
 */
object UsernameQrScanRepository {

  /**
   * Given a URL, will attempt to lookup the username, coercing it to a standard set of [QrScanResult]s.
   */
  fun lookupUsernameUrl(url: String): Single<QrScanResult> {
    return UsernameRepository.fetchUsernameAndAciFromLink(url)
      .map { result ->
        when (result) {
          is UsernameRepository.UsernameLinkConversionResult.Success -> QrScanResult.Success(Recipient.externalUsername(result.aci, result.username.toString()))
          is UsernameRepository.UsernameLinkConversionResult.Invalid -> QrScanResult.InvalidData
          is UsernameRepository.UsernameLinkConversionResult.NotFound -> QrScanResult.NotFound(result.username?.toString())
          is UsernameRepository.UsernameLinkConversionResult.NetworkError -> QrScanResult.NetworkError
        }
      }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a URI pointing to an image that may contain a username QR code, this will attempt to lookup the username, coercing it to a standard set of [QrScanResult]s.
   */
  fun scanImageUriForQrCode(context: Context, uri: Uri): Single<QrScanResult> {
    val loadBitmap = Glide.with(context)
      .asBitmap()
      .format(DecodeFormat.PREFER_ARGB_8888)
      .load(uri)
      .submit()

    return Single.fromFuture(loadBitmap)
      .map { QrProcessor().getScannedData(it).toOptional() }
      .flatMap {
        if (it.isPresent) {
          lookupUsernameUrl(it.get())
        } else {
          Single.just(QrScanResult.QrNotFound)
        }
      }
      .subscribeOn(Schedulers.io())
  }
}
