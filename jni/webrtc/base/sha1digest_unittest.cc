/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/sha1digest.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/stringencode.h"

namespace rtc {

std::string Sha1(const std::string& input) {
  Sha1Digest sha1;
  return ComputeDigest(&sha1, input);
}

TEST(Sha1DigestTest, TestSize) {
  Sha1Digest sha1;
  EXPECT_EQ(20, static_cast<int>(Sha1Digest::kSize));
  EXPECT_EQ(20U, sha1.Size());
}

TEST(Sha1DigestTest, TestBasic) {
  // Test vectors from sha1.c.
  EXPECT_EQ("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha1(""));
  EXPECT_EQ("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1("abc"));
  EXPECT_EQ("84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            Sha1("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"));
  std::string a_million_as(1000000, 'a');
  EXPECT_EQ("34aa973cd4c4daa4f61eeb2bdbad27316534016f", Sha1(a_million_as));
}

TEST(Sha1DigestTest, TestMultipleUpdates) {
  Sha1Digest sha1;
  std::string input =
      "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
  char output[Sha1Digest::kSize];
  for (size_t i = 0; i < input.size(); ++i) {
    sha1.Update(&input[i], 1);
  }
  EXPECT_EQ(sha1.Size(), sha1.Finish(output, sizeof(output)));
  EXPECT_EQ("84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            hex_encode(output, sizeof(output)));
}

TEST(Sha1DigestTest, TestReuse) {
  Sha1Digest sha1;
  std::string input = "abc";
  EXPECT_EQ("a9993e364706816aba3e25717850c26c9cd0d89d",
            ComputeDigest(&sha1, input));
  input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
  EXPECT_EQ("84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            ComputeDigest(&sha1, input));
}

TEST(Sha1DigestTest, TestBufferTooSmall) {
  Sha1Digest sha1;
  std::string input = "abcdefghijklmnopqrstuvwxyz";
  char output[Sha1Digest::kSize - 1];
  sha1.Update(input.c_str(), input.size());
  EXPECT_EQ(0U, sha1.Finish(output, sizeof(output)));
}

TEST(Sha1DigestTest, TestBufferConst) {
  Sha1Digest sha1;
  const int kLongSize = 1000000;
  std::string input(kLongSize, '\0');
  for (int i = 0; i < kLongSize; ++i) {
    input[i] = static_cast<char>(i);
  }
  sha1.Update(input.c_str(), input.size());
  for (int i = 0; i < kLongSize; ++i) {
    EXPECT_EQ(static_cast<char>(i), input[i]);
  }
}

}  // namespace rtc
