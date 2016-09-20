/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/common.h"

namespace rtc {

// Tests for string_match().

TEST(string_matchTest, Matches) {
  EXPECT_TRUE( string_match("A.B.C.D", "a.b.c.d"));
  EXPECT_TRUE( string_match("www.TEST.GOOGLE.COM", "www.*.com"));
  EXPECT_TRUE( string_match("127.0.0.1",  "12*.0.*1"));
  EXPECT_TRUE( string_match("127.1.0.21", "12*.0.*1"));
  EXPECT_FALSE(string_match("127.0.0.0",  "12*.0.*1"));
  EXPECT_FALSE(string_match("127.0.0.0",  "12*.0.*1"));
  EXPECT_FALSE(string_match("127.1.1.21", "12*.0.*1"));
}

// It's not clear if we will ever use wchar_t strings on unix.  In theory,
// all strings should be Utf8 all the time, except when interfacing with Win32
// APIs that require Utf16.

#if defined(WEBRTC_WIN)

// Tests for ascii_string_compare().

// Tests NULL input.
TEST(ascii_string_compareTest, NullInput) {
  // The following results in an access violation in
  // ascii_string_compare.  Is this a bug or by design?  stringutils.h
  // should document the expected behavior in this case.

  // EXPECT_EQ(0, ascii_string_compare(NULL, NULL, 1, identity));
}

// Tests comparing two strings of different lengths.
TEST(ascii_string_compareTest, DifferentLengths) {
  EXPECT_EQ(-1, ascii_string_compare(L"Test", "Test1", 5, identity));
}

// Tests the case where the buffer size is smaller than the string
// lengths.
TEST(ascii_string_compareTest, SmallBuffer) {
  EXPECT_EQ(0, ascii_string_compare(L"Test", "Test1", 3, identity));
}

// Tests the case where the buffer is not full.
TEST(ascii_string_compareTest, LargeBuffer) {
  EXPECT_EQ(0, ascii_string_compare(L"Test", "Test", 10, identity));
}

// Tests comparing two eqaul strings.
TEST(ascii_string_compareTest, Equal) {
  EXPECT_EQ(0, ascii_string_compare(L"Test", "Test", 5, identity));
  EXPECT_EQ(0, ascii_string_compare(L"TeSt", "tEsT", 5, tolowercase));
}

// Tests comparing a smller string to a larger one.
TEST(ascii_string_compareTest, LessThan) {
  EXPECT_EQ(-1, ascii_string_compare(L"abc", "abd", 4, identity));
  EXPECT_EQ(-1, ascii_string_compare(L"ABC", "abD", 5, tolowercase));
}

// Tests comparing a larger string to a smaller one.
TEST(ascii_string_compareTest, GreaterThan) {
  EXPECT_EQ(1, ascii_string_compare(L"xyz", "xy", 5, identity));
  EXPECT_EQ(1, ascii_string_compare(L"abc", "ABB", 5, tolowercase));
}
#endif  // WEBRTC_WIN 

TEST(string_trim_Test, Trimming) {
  EXPECT_EQ("temp", string_trim("\n\r\t temp \n\r\t"));
  EXPECT_EQ("temp\n\r\t temp", string_trim(" temp\n\r\t temp "));
  EXPECT_EQ("temp temp", string_trim("temp temp"));
  EXPECT_EQ("", string_trim(" \r\n\t"));
  EXPECT_EQ("", string_trim(""));
}

TEST(string_startsTest, StartsWith) {
  EXPECT_TRUE(starts_with("foobar", "foo"));
  EXPECT_TRUE(starts_with("foobar", "foobar"));
  EXPECT_TRUE(starts_with("foobar", ""));
  EXPECT_TRUE(starts_with("", ""));
  EXPECT_FALSE(starts_with("foobar", "bar"));
  EXPECT_FALSE(starts_with("foobar", "foobarbaz"));
  EXPECT_FALSE(starts_with("", "f"));
}

TEST(string_endsTest, EndsWith) {
  EXPECT_TRUE(ends_with("foobar", "bar"));
  EXPECT_TRUE(ends_with("foobar", "foobar"));
  EXPECT_TRUE(ends_with("foobar", ""));
  EXPECT_TRUE(ends_with("", ""));
  EXPECT_FALSE(ends_with("foobar", "foo"));
  EXPECT_FALSE(ends_with("foobar", "foobarbaz"));
  EXPECT_FALSE(ends_with("", "f"));
}

} // namespace rtc
