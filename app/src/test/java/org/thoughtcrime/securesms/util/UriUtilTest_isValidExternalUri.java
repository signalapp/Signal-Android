/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class UriUtilTest_isValidExternalUri {

  private final String  input;
  private final boolean output;

  private static final String APPLICATION_ID = "org.thoughtcrime.securesms";

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "content://other.app.package.name.org/path/public.txt",             true  },
        { "file:///sdcard/public.txt",                                        true  },
        {"file:///data/data/" + APPLICATION_ID + "/private.txt",              false },
        {"file:///any/path/with/package/name/" + APPLICATION_ID,              false },
        {"file:///" + APPLICATION_ID + "/any/path/with/package/name",         false },
        { "file:///any/path/../with/back/references/private.txt",             false },
        { "file:///any/path/with/back/references/../private.txt",             false },
        { "file:///../any/path/with/back/references/private.txt",             false },
        { "file:///encoded/back/reference/%2F..%2F..path%2Fto%2Fprivate.txt", false },
        { "file:///public/%2E%2E%2Fprivate%2Fprivate.txt",                    false },
        { "file:///data/no/paths/in/data",                                    false },
    });
  }

  public UriUtilTest_isValidExternalUri(String input, boolean output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    Context context = ApplicationProvider.getApplicationContext();
    Uri     uri     = Uri.parse(input);

    assertEquals(output, UriUtil.isValidExternalUri(context, uri));
  }
}
