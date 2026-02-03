/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import org.signal.mediasend.MediaSendActivityContract

/**
 * Activity result contract bound to [MediaSendV3Activity].
 */
class MediaSendV3ActivityContract : MediaSendActivityContract(MediaSendV3Activity::class.java)
