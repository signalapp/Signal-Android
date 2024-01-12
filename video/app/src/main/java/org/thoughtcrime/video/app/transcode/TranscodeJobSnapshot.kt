/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.net.Uri
import java.util.UUID

data class TranscodeJobSnapshot(val media: Uri, val jobId: UUID)
