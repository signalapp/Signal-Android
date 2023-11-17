/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.logging

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ScrubberTest(private val input: String, private val expected: String) {
  @Test
  fun scrub() {
    Assert.assertEquals(expected, Scrubber.scrub(input).toString())
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Iterable<Array<Any>> {
      return listOf(
        arrayOf(
          "An E164 number +15551234567",
          "An E164 number +*********67"
        ),
        arrayOf(
          "A UK number +447700900000",
          "A UK number +**********00"
        ),
        arrayOf(
          "A Japanese number 08011112222",
          "A Japanese number 0********22"
        ),
        arrayOf(
          "A Japanese number (08011112222)",
          "A Japanese number (0********22)"
        ),
        arrayOf(
          "Not a Japanese number 08011112222333344445555",
          "Not a Japanese number 08011112222333344445555"
        ),
        arrayOf(
          "Not a Japanese number 1234508011112222",
          "Not a Japanese number 1234508011112222"
        ),
        arrayOf(
          "An avatar filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/%2B447700900099",
          "An avatar filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/%2B**********99"
        ),
        arrayOf(
          "Multiple numbers +447700900001 +447700900002",
          "Multiple numbers +**********01 +**********02"
        ),
        arrayOf(
          "One less than shortest number +155556",
          "One less than shortest number +155556"
        ),
        arrayOf(
          "Shortest number +1555567",
          "Shortest number +*****67"
        ),
        arrayOf(
          "Longest number +155556789012345",
          "Longest number +*************45"
        ),
        arrayOf(
          "One more than longest number +1234567890123456",
          "One more than longest number +*************456"
        ),
        arrayOf(
          "abc@def.com",
          "a...@..."
        ),
        arrayOf(
          "An email abc@def.com",
          "An email a...@..."
        ),
        arrayOf(
          "A short email a@def.com",
          "A short email a...@..."
        ),
        arrayOf(
          "A email with multiple parts before the @ d.c+b.a@mulitpart.domain.com and a multipart domain",
          "A email with multiple parts before the @ d...@... and a multipart domain"
        ),
        arrayOf(
          "An avatar email filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/abc@signal.org",
          "An avatar email filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/a...@..."
        ),
        arrayOf(
          "An email and a number abc@def.com +155556789012345",
          "An email and a number a...@... +*************45"
        ),
        arrayOf(
          "__textsecure_group__!000102030405060708090a0b0c0d0e0f",
          "__...group...0f"
        ),
        arrayOf(
          "A group id __textsecure_group__!000102030405060708090a0b0c0d0e1a surrounded with text",
          "A group id __...group...1a surrounded with text"
        ),
        arrayOf(
          "__signal_group__v2__!0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
          "__...group_v2...ef"
        ),
        arrayOf(
          "A group v2 id __signal_group__v2__!23456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef01 surrounded with text",
          "A group v2 id __...group_v2...01 surrounded with text"
        ),
        arrayOf(
          "a37cb654-c9e0-4c1e-93df-3d11ca3c97f4",
          "********-****-****-****-*********7f4"
        ),
        arrayOf(
          "A UUID a37cb654-c9e0-4c1e-93df-3d11ca3c97f4 surrounded with text",
          "A UUID ********-****-****-****-*********7f4 surrounded with text"
        ),
        arrayOf(
          "JOB::a37cb654-c9e0-4c1e-93df-3d11ca3c97f4",
          "JOB::a37cb654-c9e0-4c1e-93df-3d11ca3c97f4"
        ),
        arrayOf(
          "All patterns in a row __textsecure_group__!abcdefg1234567890 +1234567890123456 abc@def.com a37cb654-c9e0-4c1e-93df-3d11ca3c97f4 nl.motorsport.com 192.168.1.1 with text after",
          "All patterns in a row __...group...90 +*************456 a...@... ********-****-****-****-*********7f4 ***.com ...ipv4... with text after"
        ),
        arrayOf(
          "java.net.UnknownServiceException: CLEARTEXT communication to nl.motorsport.com not permitted by network security policy",
          "java.net.UnknownServiceException: CLEARTEXT communication to ***.com not permitted by network security policy"
        ),
        arrayOf(
          "nl.motorsport.com:443",
          "***.com:443"
        ),
        arrayOf(
          "Failed to resolve chat.signal.org using . Continuing.",
          "Failed to resolve chat.signal.org using . Continuing."
        ),
        arrayOf(
          " Caused by: java.io.IOException: unexpected end of stream on Connection{storage.signal.org:443, proxy=DIRECT hostAddress=storage.signal.org/142.251.32.211:443 cipherSuite=TLS_AES_128_GCM_SHA256 protocol=http/1.1}",
          " Caused by: java.io.IOException: unexpected end of stream on Connection{storage.signal.org:443, proxy=DIRECT hostAddress=storage.signal.org/...ipv4...:443 cipherSuite=TLS_AES_128_GCM_SHA256 protocol=http/1.1}"
        ),
        arrayOf(
          "192.168.1.1",
          "...ipv4..."
        ),
        arrayOf(
          "255.255.255.255",
          "...ipv4..."
        ),
        arrayOf(
          "Text before 255.255.255.255 text after",
          "Text before ...ipv4... text after"
        ),
        arrayOf(
          "Not an ipv4 3.141",
          "Not an ipv4 3.141"
        ),
        arrayOf(
          "A Call Link Root Key BCDF-FGHK-MNPQ-RSTX-ZRQH-BCDF-FGHM-STXZ",
          "A Call Link Root Key BCDF-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"
        ),
        arrayOf(
          "Not a Call Link Root Key (Invalid Characters) BCAF-FGHK-MNPQ-RSTX-ZRQH-BCDF-FGHM-STXZ",
          "Not a Call Link Root Key (Invalid Characters) BCAF-FGHK-MNPQ-RSTX-ZRQH-BCDF-FGHM-STXZ"
        ),
        arrayOf(
          "Not a Call Link Root Key (Missing Quartet) BCAF-FGHK-MNPQ-RSTX-ZRQH-BCDF-STXZ",
          "Not a Call Link Root Key (Missing Quartet) BCAF-FGHK-MNPQ-RSTX-ZRQH-BCDF-STXZ"
        ),
        arrayOf(
          "2345:0425:2CA1:0000:0000:0567:5673:23b5",
          "...ipv6..."
        ),
        arrayOf(
          "2345:425:2CA1:0000:0000:567:5673:23b5",
          "...ipv6..."
        ),
        arrayOf(
          "2345:0425:2CA1:0:0:0567:5673:23b5",
          "...ipv6..."
        ),
        arrayOf(
          "2345:0425:2CA1::0567:5673:23b5",
          "...ipv6..."
        ),
        arrayOf(
          "FF01:0:0:0:0:0:0:1",
          "...ipv6..."
        ),
        arrayOf(
          "2001:db8::a3",
          "...ipv6..."
        ),
        arrayOf(
          "text before 2345:0425:2CA1:0000:0000:0567:5673:23b5 text after",
          "text before ...ipv6... text after"
        ),
        arrayOf(
          "Recipient::1",
          "Recipient::1"
        ),
        arrayOf(
          "Recipient::123",
          "Recipient::123"
        )
      )
    }
  }
}
