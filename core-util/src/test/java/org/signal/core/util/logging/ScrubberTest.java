package org.signal.core.util.logging;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class ScrubberTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{

    { "An E164 number +15551234567",
      "An E164 number +*********67" },

    { "A UK number +447700900000",
      "A UK number +**********00" },

    { "An avatar filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/%2B447700900099",
      "An avatar filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/%2B**********99" },

    { "Multiple numbers +447700900001 +447700900002",
      "Multiple numbers +**********01 +**********02" },

    { "One less than shortest number +155556",
      "One less than shortest number +155556" },

    { "Shortest number +1555567",
      "Shortest number +*****67" },

    { "Longest number +155556789012345",
      "Longest number +*************45" },

    { "One more than longest number +1234567890123456",
      "One more than longest number +*************456" },

    { "abc@def.com",
      "a...@..." },

    { "An email abc@def.com",
      "An email a...@..." },

    { "A short email a@def.com",
      "A short email a...@..." },

    { "A email with multiple parts before the @ d.c+b.a@mulitpart.domain.com and a multipart domain",
      "A email with multiple parts before the @ d...@... and a multipart domain" },

    { "An avatar email filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/abc@signal.org",
      "An avatar email filename: file:///data/user/0/org.thoughtcrime.securesms/files/avatars/a...@..." },

    { "An email and a number abc@def.com +155556789012345",
      "An email and a number a...@... +*************45" },

    { "__textsecure_group__!000102030405060708090a0b0c0d0e0f",
      "__...group...0f" },

    { "A group id __textsecure_group__!000102030405060708090a0b0c0d0e1a surrounded with text",
      "A group id __...group...1a surrounded with text" },

    { "__signal_group__v2__!0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "__...group_v2...ef" },

    { "A group v2 id __signal_group__v2__!23456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef01 surrounded with text",
      "A group v2 id __...group_v2...01 surrounded with text" },

    { "a37cb654-c9e0-4c1e-93df-3d11ca3c97f4",
      "********-****-****-****-*********7f4" },

    { "A UUID a37cb654-c9e0-4c1e-93df-3d11ca3c97f4 surrounded with text",
      "A UUID ********-****-****-****-*********7f4 surrounded with text" },

    { "JOB::a37cb654-c9e0-4c1e-93df-3d11ca3c97f4",
      "JOB::a37cb654-c9e0-4c1e-93df-3d11ca3c97f4" },

    { "All patterns in a row __textsecure_group__!abcdefg1234567890 +1234567890123456 abc@def.com a37cb654-c9e0-4c1e-93df-3d11ca3c97f4 nl.motorsport.com 192.168.1.1 with text after",
      "All patterns in a row __...group...90 +*************456 a...@... ********-****-****-****-*********7f4 ***.com ...ipv4... with text after"
    },

    { "java.net.UnknownServiceException: CLEARTEXT communication to nl.motorsport.com not permitted by network security policy",
      "java.net.UnknownServiceException: CLEARTEXT communication to ***.com not permitted by network security policy"
    },

    { "nl.motorsport.com:443",
      "***.com:443"
    },

    { "Failed to resolve chat.signal.org using . Continuing.",
      "Failed to resolve chat.signal.org using . Continuing."
    },

    { " Caused by: java.io.IOException: unexpected end of stream on Connection{storage.signal.org:443, proxy=DIRECT hostAddress=storage.signal.org/142.251.32.211:443 cipherSuite=TLS_AES_128_GCM_SHA256 protocol=http/1.1}",
      " Caused by: java.io.IOException: unexpected end of stream on Connection{storage.signal.org:443, proxy=DIRECT hostAddress=storage.signal.org/...ipv4...:443 cipherSuite=TLS_AES_128_GCM_SHA256 protocol=http/1.1}"
    },

    { "192.168.1.1",
      "...ipv4..."
    },

    { "255.255.255.255",
      "...ipv4..."
    },

    { "Text before 255.255.255.255 text after",
      "Text before ...ipv4... text after"
    },

    { "Not an ipv4 3.141",
      "Not an ipv4 3.141"
    }

    });
  }

  private final String input;
  private final String expected;

  public ScrubberTest(String input, String expected) {
    this.input    = input;
    this.expected = expected;
  }

  @Test
  public void scrub() {
    assertEquals(expected, Scrubber.scrub(input).toString());
  }
}
