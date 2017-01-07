/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/messagedigest.h"
#include "webrtc/base/stringencode.h"

namespace rtc {

// Test vectors from RFC 1321.
TEST(MessageDigestTest, TestMd5Digest) {
  // Test the string versions of the APIs.
  EXPECT_EQ("d41d8cd98f00b204e9800998ecf8427e",
      ComputeDigest(DIGEST_MD5, ""));
  EXPECT_EQ("900150983cd24fb0d6963f7d28e17f72",
      ComputeDigest(DIGEST_MD5, "abc"));
  EXPECT_EQ("c3fcd3d76192e4007dfb496cca67e13b",
      ComputeDigest(DIGEST_MD5, "abcdefghijklmnopqrstuvwxyz"));

  // Test the raw buffer versions of the APIs; also check output buffer size.
  char output[16];
  EXPECT_EQ(sizeof(output),
      ComputeDigest(DIGEST_MD5, "abc", 3, output, sizeof(output)));
  EXPECT_EQ("900150983cd24fb0d6963f7d28e17f72",
      hex_encode(output, sizeof(output)));
  EXPECT_EQ(0U,
      ComputeDigest(DIGEST_MD5, "abc", 3, output, sizeof(output) - 1));
}

// Test vectors from RFC 3174.
TEST(MessageDigestTest, TestSha1Digest) {
  // Test the string versions of the APIs.
  EXPECT_EQ("da39a3ee5e6b4b0d3255bfef95601890afd80709",
      ComputeDigest(DIGEST_SHA_1, ""));
  EXPECT_EQ("a9993e364706816aba3e25717850c26c9cd0d89d",
      ComputeDigest(DIGEST_SHA_1, "abc"));
  EXPECT_EQ("84983e441c3bd26ebaae4aa1f95129e5e54670f1",
      ComputeDigest(DIGEST_SHA_1,
          "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"));

  // Test the raw buffer versions of the APIs; also check output buffer size.
  char output[20];
  EXPECT_EQ(sizeof(output),
      ComputeDigest(DIGEST_SHA_1, "abc", 3, output, sizeof(output)));
  EXPECT_EQ("a9993e364706816aba3e25717850c26c9cd0d89d",
      hex_encode(output, sizeof(output)));
  EXPECT_EQ(0U,
      ComputeDigest(DIGEST_SHA_1, "abc", 3, output, sizeof(output) - 1));
}

// Test that we fail properly if a bad digest algorithm is specified.
TEST(MessageDigestTest, TestBadDigest) {
  std::string output;
  EXPECT_FALSE(ComputeDigest("sha-9000", "abc", &output));
  EXPECT_EQ("", ComputeDigest("sha-9000", "abc"));
}

// Test vectors from RFC 2202.
TEST(MessageDigestTest, TestMd5Hmac) {
  // Test the string versions of the APIs.
  EXPECT_EQ("9294727a3638bb1c13f48ef8158bfc9d",
      ComputeHmac(DIGEST_MD5, std::string(16, '\x0b'), "Hi There"));
  EXPECT_EQ("750c783e6ab0b503eaa86e310a5db738",
      ComputeHmac(DIGEST_MD5, "Jefe", "what do ya want for nothing?"));
  EXPECT_EQ("56be34521d144c88dbb8c733f0e8b3f6",
      ComputeHmac(DIGEST_MD5, std::string(16, '\xaa'),
          std::string(50, '\xdd')));
  EXPECT_EQ("697eaf0aca3a3aea3a75164746ffaa79",
      ComputeHmac(DIGEST_MD5,
          "\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f"
          "\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19",
          std::string(50, '\xcd')));
  EXPECT_EQ("56461ef2342edc00f9bab995690efd4c",
      ComputeHmac(DIGEST_MD5, std::string(16, '\x0c'),
          "Test With Truncation"));
  EXPECT_EQ("6b1ab7fe4bd7bf8f0b62e6ce61b9d0cd",
      ComputeHmac(DIGEST_MD5, std::string(80, '\xaa'),
          "Test Using Larger Than Block-Size Key - Hash Key First"));
  EXPECT_EQ("6f630fad67cda0ee1fb1f562db3aa53e",
      ComputeHmac(DIGEST_MD5, std::string(80, '\xaa'),
          "Test Using Larger Than Block-Size Key and Larger "
          "Than One Block-Size Data"));

  // Test the raw buffer versions of the APIs; also check output buffer size.
  std::string key(16, '\x0b');
  std::string input("Hi There");
  char output[16];
  EXPECT_EQ(sizeof(output),
      ComputeHmac(DIGEST_MD5, key.c_str(), key.size(),
          input.c_str(), input.size(), output, sizeof(output)));
  EXPECT_EQ("9294727a3638bb1c13f48ef8158bfc9d",
      hex_encode(output, sizeof(output)));
  EXPECT_EQ(0U,
      ComputeHmac(DIGEST_MD5, key.c_str(), key.size(),
          input.c_str(), input.size(), output, sizeof(output) - 1));
}

// Test vectors from RFC 2202.
TEST(MessageDigestTest, TestSha1Hmac) {
  // Test the string versions of the APIs.
  EXPECT_EQ("b617318655057264e28bc0b6fb378c8ef146be00",
      ComputeHmac(DIGEST_SHA_1, std::string(20, '\x0b'), "Hi There"));
  EXPECT_EQ("effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
      ComputeHmac(DIGEST_SHA_1, "Jefe", "what do ya want for nothing?"));
  EXPECT_EQ("125d7342b9ac11cd91a39af48aa17b4f63f175d3",
      ComputeHmac(DIGEST_SHA_1, std::string(20, '\xaa'),
          std::string(50, '\xdd')));
  EXPECT_EQ("4c9007f4026250c6bc8414f9bf50c86c2d7235da",
      ComputeHmac(DIGEST_SHA_1,
          "\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f"
          "\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19",
          std::string(50, '\xcd')));
  EXPECT_EQ("4c1a03424b55e07fe7f27be1d58bb9324a9a5a04",
      ComputeHmac(DIGEST_SHA_1, std::string(20, '\x0c'),
          "Test With Truncation"));
  EXPECT_EQ("aa4ae5e15272d00e95705637ce8a3b55ed402112",
      ComputeHmac(DIGEST_SHA_1, std::string(80, '\xaa'),
          "Test Using Larger Than Block-Size Key - Hash Key First"));
  EXPECT_EQ("e8e99d0f45237d786d6bbaa7965c7808bbff1a91",
      ComputeHmac(DIGEST_SHA_1, std::string(80, '\xaa'),
          "Test Using Larger Than Block-Size Key and Larger "
          "Than One Block-Size Data"));

  // Test the raw buffer versions of the APIs; also check output buffer size.
  std::string key(20, '\x0b');
  std::string input("Hi There");
  char output[20];
  EXPECT_EQ(sizeof(output),
      ComputeHmac(DIGEST_SHA_1, key.c_str(), key.size(),
          input.c_str(), input.size(), output, sizeof(output)));
  EXPECT_EQ("b617318655057264e28bc0b6fb378c8ef146be00",
      hex_encode(output, sizeof(output)));
  EXPECT_EQ(0U,
      ComputeHmac(DIGEST_SHA_1, key.c_str(), key.size(),
          input.c_str(), input.size(), output, sizeof(output) - 1));
}

TEST(MessageDigestTest, TestBadHmac) {
  std::string output;
  EXPECT_FALSE(ComputeHmac("sha-9000", "key", "abc", &output));
  EXPECT_EQ("", ComputeHmac("sha-9000", "key", "abc"));
}

}  // namespace rtc
