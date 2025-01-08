/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.s3

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.IOException
import org.junit.Test

@Suppress("ClassName")
class S3Test_getS3Url {
  @Test
  fun validS3Urls() {
    assertThat(S3.s3Url("/static/heart.png").toString()).isEqualTo("https://updates2.signal.org/static/heart.png")
    assertThat(S3.s3Url("/static/heart.png?weee=1").toString()).isEqualTo("https://updates2.signal.org/static/heart.png%3Fweee=1")
    assertThat(S3.s3Url("/@signal.org").toString()).isEqualTo("https://updates2.signal.org/@signal.org")
  }

  @Test(expected = IOException::class)
  fun invalid() {
    S3.s3Url("@signal.org")
  }

  @Test(expected = IOException::class)
  fun invalidRelative() {
    S3.s3Url("static/heart.png")
  }
}
