/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.util;

import org.junit.Test;

import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class Rfc5724UriTest {

  @Test public void testInvalidPath() {
    final String[] invalidSchemaUris = {
        "",
        ":",
        "sms:",
        ":sms",
        "sms:?goto=fail",
        "sms:?goto=fail&fail=goto"
    };

    for (String uri : invalidSchemaUris) {
      assertThrows(
          "URISyntaxException should be thrown",
          URISyntaxException.class,
          () -> new Rfc5724Uri(uri)
      );
    }
  }

  @Test public void testGetSchema() throws Exception {
    final String[][] uriTestPairs = {
        {"sms:+15555555555",           "sms"},
        {"sMs:+15555555555",           "sMs"},
        {"smsto:+15555555555?",        "smsto"},
        {"mms:+15555555555?a=b",       "mms"},
        {"mmsto:+15555555555?a=b&c=d", "mmsto"}
    };

    for (String[] uriTestPair : uriTestPairs) {
      final Rfc5724Uri testUri = new Rfc5724Uri(uriTestPair[0]);
      assertEquals(uriTestPair[1], testUri.getSchema());
    }
  }

  @Test public void testGetPath() throws Exception {
    final String[][] uriTestPairs = {
        {"sms:+15555555555",                      "+15555555555"},
        {"sms:%2B555555555",                    "%2B555555555"},
        {"smsto:+15555555555?",                   "+15555555555"},
        {"mms:+15555555555?a=b",                  "+15555555555"},
        {"mmsto:+15555555555?a=b&c=d",            "+15555555555"},
        {"sms:+15555555555,+14444444444",         "+15555555555,+14444444444"},
        {"sms:+15555555555,+14444444444?",        "+15555555555,+14444444444"},
        {"sms:+15555555555,+14444444444?a=b",     "+15555555555,+14444444444"},
        {"sms:+15555555555,+14444444444?a=b&c=d", "+15555555555,+14444444444"}
    };

    for (String[] uriTestPair : uriTestPairs) {
      final Rfc5724Uri testUri = new Rfc5724Uri(uriTestPair[0]);
      assertEquals(uriTestPair[1], testUri.getPath());
    }
  }

  @Test public void testGetQueryParams() throws Exception {
    final String[][] uriTestPairs = {
        {"sms:+15555555555",         "a", null},
        {"mms:+15555555555?b=",      "a", null},
        {"mmsto:+15555555555?a=",    "a", ""},
        {"sms:+15555555555?a=b",     "a", "b"},
        {"sms:+15555555555?a=b&c=d", "a", "b"},
        {"sms:+15555555555?a=b&c=d", "b", null},
        {"sms:+15555555555?a=b&c=d", "c", "d"},
        {"sms:+15555555555?a=b&c=d", "d", null}
    };

    for (String[] uriTestPair : uriTestPairs) {
      final Rfc5724Uri testUri     = new Rfc5724Uri(uriTestPair[0]);
      final String     paramResult = testUri.getQueryParams().get(uriTestPair[1]);
      assertEquals(uriTestPair[2], paramResult);
    }
  }
}
