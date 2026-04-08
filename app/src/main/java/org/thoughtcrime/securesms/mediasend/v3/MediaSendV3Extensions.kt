/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.StorySendRequirements
import org.thoughtcrime.securesms.stories.Stories

private fun contract(): ActivityResultContract<MediaSendActivityContract.Args, MediaSendActivityContract.Result?> = MediaSendActivityContract(MediaSendV3Activity::class.java)

fun Fragment.mediaSendLauncher(callback: (MediaSendActivityContract.Result?) -> Unit = {}): ActivityResultLauncher<MediaSendActivityContract.Args> = registerForActivityResult(contract(), callback)

fun AppCompatActivity.mediaSendLauncher(callback: (MediaSendActivityContract.Result?) -> Unit = {}): ActivityResultLauncher<MediaSendActivityContract.Args> = registerForActivityResult(contract(), callback)

/**
 * Maps the feature-module [StorySendRequirements] to the app-layer [Stories.MediaTransform.SendRequirements].
 */
fun StorySendRequirements.toAppSendRequirements(): Stories.MediaTransform.SendRequirements = when (this) {
  StorySendRequirements.CAN_SEND -> Stories.MediaTransform.SendRequirements.VALID_DURATION
  StorySendRequirements.CAN_NOT_SEND -> Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
  StorySendRequirements.REQUIRES_CROP -> Stories.MediaTransform.SendRequirements.REQUIRES_CLIP
}
