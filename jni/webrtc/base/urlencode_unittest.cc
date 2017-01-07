/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/arraysize.h"
#include "webrtc/base/common.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/urlencode.h"

using rtc::UrlEncode;

TEST(Urlencode, SourceTooLong) {
  char source[] = "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^"
      "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^";
  char dest[1];
  ASSERT_EQ(0, UrlEncode(source, dest, arraysize(dest)));
  ASSERT_EQ('\0', dest[0]);

  dest[0] = 'a';
  ASSERT_EQ(0, UrlEncode(source, dest, 0));
  ASSERT_EQ('a', dest[0]);
}

TEST(Urlencode, OneCharacterConversion) {
  char source[] = "^";
  char dest[4];
  ASSERT_EQ(3, UrlEncode(source, dest, arraysize(dest)));
  ASSERT_STREQ("%5E", dest);
}

TEST(Urlencode, ShortDestinationNoEncoding) {
  // In this case we have a destination that would not be
  // big enough to hold an encoding but is big enough to
  // hold the text given.
  char source[] = "aa";
  char dest[3];
  ASSERT_EQ(2, UrlEncode(source, dest, arraysize(dest)));
  ASSERT_STREQ("aa", dest);
}

TEST(Urlencode, ShortDestinationEncoding) {
  // In this case we have a destination that is not
  // big enough to hold the encoding.
  char source[] = "&";
  char dest[3];
  ASSERT_EQ(0, UrlEncode(source, dest, arraysize(dest)));
  ASSERT_EQ('\0', dest[0]);
}

TEST(Urlencode, Encoding1) {
  char source[] = "A^ ";
  char dest[8];
  ASSERT_EQ(5, UrlEncode(source, dest, arraysize(dest)));
  ASSERT_STREQ("A%5E+", dest);
}

TEST(Urlencode, Encoding2) {
  char source[] = "A^ ";
  char dest[8];
  ASSERT_EQ(7, rtc::UrlEncodeWithoutEncodingSpaceAsPlus(source, dest,
                                                        arraysize(dest)));
  ASSERT_STREQ("A%5E%20", dest);
}

TEST(Urldecode, Decoding1) {
  char source[] = "A%5E+";
  char dest[8];
  ASSERT_EQ(3, rtc::UrlDecode(source, dest));
  ASSERT_STREQ("A^ ", dest);
}

TEST(Urldecode, Decoding2) {
  char source[] = "A%5E+";
  char dest[8];
  ASSERT_EQ(3, rtc::UrlDecodeWithoutEncodingSpaceAsPlus(source, dest));
  ASSERT_STREQ("A^+", dest);
}
