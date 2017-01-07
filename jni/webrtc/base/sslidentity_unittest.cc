/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>

#include "webrtc/base/gunit.h"
#include "webrtc/base/helpers.h"
#include "webrtc/base/ssladapter.h"
#include "webrtc/base/sslidentity.h"

using rtc::SSLIdentity;

const char kTestCertificate[] = "-----BEGIN CERTIFICATE-----\n"
    "MIIB6TCCAVICAQYwDQYJKoZIhvcNAQEEBQAwWzELMAkGA1UEBhMCQVUxEzARBgNV\n"
    "BAgTClF1ZWVuc2xhbmQxGjAYBgNVBAoTEUNyeXB0U29mdCBQdHkgTHRkMRswGQYD\n"
    "VQQDExJUZXN0IENBICgxMDI0IGJpdCkwHhcNMDAxMDE2MjIzMTAzWhcNMDMwMTE0\n"
    "MjIzMTAzWjBjMQswCQYDVQQGEwJBVTETMBEGA1UECBMKUXVlZW5zbGFuZDEaMBgG\n"
    "A1UEChMRQ3J5cHRTb2Z0IFB0eSBMdGQxIzAhBgNVBAMTGlNlcnZlciB0ZXN0IGNl\n"
    "cnQgKDUxMiBiaXQpMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJ+zw4Qnlf8SMVIP\n"
    "Fe9GEcStgOY2Ww/dgNdhjeD8ckUJNP5VZkVDTGiXav6ooKXfX3j/7tdkuD8Ey2//\n"
    "Kv7+ue0CAwEAATANBgkqhkiG9w0BAQQFAAOBgQCT0grFQeZaqYb5EYfk20XixZV4\n"
    "GmyAbXMftG1Eo7qGiMhYzRwGNWxEYojf5PZkYZXvSqZ/ZXHXa4g59jK/rJNnaVGM\n"
    "k+xIX8mxQvlV0n5O9PIha5BX5teZnkHKgL8aKKLKW1BK7YTngsfSzzaeame5iKfz\n"
    "itAE+OjGF+PFKbwX8Q==\n"
    "-----END CERTIFICATE-----\n";

const unsigned char kTestCertSha1[] = {
    0xA6, 0xC8, 0x59, 0xEA, 0xC3, 0x7E, 0x6D, 0x33,
    0xCF, 0xE2, 0x69, 0x9D, 0x74, 0xE6, 0xF6, 0x8A,
    0x9E, 0x47, 0xA7, 0xCA};
const unsigned char kTestCertSha224[] = {
    0xd4, 0xce, 0xc6, 0xcf, 0x28, 0xcb, 0xe9, 0x77,
    0x38, 0x36, 0xcf, 0xb1, 0x3b, 0x4a, 0xd7, 0xbd,
    0xae, 0x24, 0x21, 0x08, 0xcf, 0x6a, 0x44, 0x0d,
    0x3f, 0x94, 0x2a, 0x5b};
const unsigned char kTestCertSha256[] = {
    0x41, 0x6b, 0xb4, 0x93, 0x47, 0x79, 0x77, 0x24,
    0x77, 0x0b, 0x8b, 0x2e, 0xa6, 0x2b, 0xe0, 0xf9,
    0x0a, 0xed, 0x1f, 0x31, 0xa6, 0xf7, 0x5c, 0xa1,
    0x5a, 0xc4, 0xb0, 0xa2, 0xa4, 0x78, 0xb9, 0x76};
const unsigned char kTestCertSha384[] = {
    0x42, 0x31, 0x9a, 0x79, 0x1d, 0xd6, 0x08, 0xbf,
    0x3b, 0xba, 0x36, 0xd8, 0x37, 0x4a, 0x9a, 0x75,
    0xd3, 0x25, 0x6e, 0x28, 0x92, 0xbe, 0x06, 0xb7,
    0xc5, 0xa0, 0x83, 0xe3, 0x86, 0xb1, 0x03, 0xfc,
    0x64, 0x47, 0xd6, 0xd8, 0xaa, 0xd9, 0x36, 0x60,
    0x04, 0xcc, 0xbe, 0x7d, 0x6a, 0xe8, 0x34, 0x49};
const unsigned char kTestCertSha512[] = {
    0x51, 0x1d, 0xec, 0x02, 0x3d, 0x51, 0x45, 0xd3,
    0xd8, 0x1d, 0xa4, 0x9d, 0x43, 0xc9, 0xee, 0x32,
    0x6f, 0x4f, 0x37, 0xee, 0xab, 0x3f, 0x25, 0xdf,
    0x72, 0xfc, 0x61, 0x1a, 0xd5, 0x92, 0xff, 0x6b,
    0x28, 0x71, 0x58, 0xb3, 0xe1, 0x8a, 0x18, 0xcf,
    0x61, 0x33, 0x0e, 0x14, 0xc3, 0x04, 0xaa, 0x07,
    0xf6, 0xa5, 0xda, 0xdc, 0x42, 0x42, 0x22, 0x35,
    0xce, 0x26, 0x58, 0x4a, 0x33, 0x6d, 0xbc, 0xb6};

class SSLIdentityTest : public testing::Test {
 public:
  SSLIdentityTest() {}

  ~SSLIdentityTest() {
  }

  virtual void SetUp() {
    identity_rsa1_.reset(SSLIdentity::Generate("test1", rtc::KT_RSA));
    identity_rsa2_.reset(SSLIdentity::Generate("test2", rtc::KT_RSA));
    identity_ecdsa1_.reset(SSLIdentity::Generate("test3", rtc::KT_ECDSA));
    identity_ecdsa2_.reset(SSLIdentity::Generate("test4", rtc::KT_ECDSA));

    ASSERT_TRUE(identity_rsa1_);
    ASSERT_TRUE(identity_rsa2_);
    ASSERT_TRUE(identity_ecdsa1_);
    ASSERT_TRUE(identity_ecdsa2_);

    test_cert_.reset(rtc::SSLCertificate::FromPEMString(kTestCertificate));
    ASSERT_TRUE(test_cert_);
  }

  void TestGetSignatureDigestAlgorithm() {
    std::string digest_algorithm;

    ASSERT_TRUE(identity_rsa1_->certificate().GetSignatureDigestAlgorithm(
        &digest_algorithm));
    ASSERT_EQ(rtc::DIGEST_SHA_256, digest_algorithm);

    ASSERT_TRUE(identity_rsa2_->certificate().GetSignatureDigestAlgorithm(
        &digest_algorithm));
    ASSERT_EQ(rtc::DIGEST_SHA_256, digest_algorithm);

    ASSERT_TRUE(identity_ecdsa1_->certificate().GetSignatureDigestAlgorithm(
        &digest_algorithm));
    ASSERT_EQ(rtc::DIGEST_SHA_256, digest_algorithm);

    ASSERT_TRUE(identity_ecdsa2_->certificate().GetSignatureDigestAlgorithm(
        &digest_algorithm));
    ASSERT_EQ(rtc::DIGEST_SHA_256, digest_algorithm);

    // The test certificate has an MD5-based signature.
    ASSERT_TRUE(test_cert_->GetSignatureDigestAlgorithm(&digest_algorithm));
    ASSERT_EQ(rtc::DIGEST_MD5, digest_algorithm);
  }

  typedef unsigned char DigestType[rtc::MessageDigest::kMaxSize];

  void TestDigestHelper(DigestType digest,
                        const SSLIdentity* identity,
                        const std::string& algorithm,
                        size_t expected_len) {
    DigestType digest1;
    size_t digest_len;
    bool rv;

    memset(digest, 0, expected_len);
    rv = identity->certificate().ComputeDigest(algorithm, digest,
                                               sizeof(DigestType), &digest_len);
    EXPECT_TRUE(rv);
    EXPECT_EQ(expected_len, digest_len);

    // Repeat digest computation for the identity as a sanity check.
    memset(digest1, 0xff, expected_len);
    rv = identity->certificate().ComputeDigest(algorithm, digest1,
                                               sizeof(DigestType), &digest_len);
    EXPECT_TRUE(rv);
    EXPECT_EQ(expected_len, digest_len);

    EXPECT_EQ(0, memcmp(digest, digest1, expected_len));
  }

  void TestDigestForGeneratedCert(const std::string& algorithm,
                                  size_t expected_len) {
    DigestType digest[4];

    ASSERT_TRUE(expected_len <= sizeof(DigestType));

    TestDigestHelper(digest[0], identity_rsa1_.get(), algorithm, expected_len);
    TestDigestHelper(digest[1], identity_rsa2_.get(), algorithm, expected_len);
    TestDigestHelper(digest[2], identity_ecdsa1_.get(), algorithm,
                     expected_len);
    TestDigestHelper(digest[3], identity_ecdsa2_.get(), algorithm,
                     expected_len);

    // Sanity check that all four digests are unique.  This could theoretically
    // fail, since cryptographic hash collisions have a non-zero probability.
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        if (i != j)
          EXPECT_NE(0, memcmp(digest[i], digest[j], expected_len));
      }
    }
  }

  void TestDigestForFixedCert(const std::string& algorithm,
                              size_t expected_len,
                              const unsigned char* expected_digest) {
    bool rv;
    DigestType digest;
    size_t digest_len;

    ASSERT_TRUE(expected_len <= sizeof(DigestType));

    rv = test_cert_->ComputeDigest(algorithm, digest, sizeof(digest),
                                   &digest_len);
    EXPECT_TRUE(rv);
    EXPECT_EQ(expected_len, digest_len);
    EXPECT_EQ(0, memcmp(digest, expected_digest, expected_len));
  }

  void TestCloningIdentity(const SSLIdentity& identity) {
    // Convert |identity| to PEM strings and create a new identity by converting
    // back from the string format.
    std::string priv_pem = identity.PrivateKeyToPEMString();
    std::string publ_pem = identity.PublicKeyToPEMString();
    std::string cert_pem = identity.certificate().ToPEMString();
    std::unique_ptr<SSLIdentity> clone(
        SSLIdentity::FromPEMStrings(priv_pem, cert_pem));
    EXPECT_TRUE(clone);

    // Make sure the clone is identical to the original.
    EXPECT_TRUE(identity == *clone);
    ASSERT_EQ(identity.certificate().CertificateExpirationTime(),
              clone->certificate().CertificateExpirationTime());

    // At this point we are confident that the identities are identical. To be
    // extra sure, we compare PEM strings of the clone with the original. Note
    // that the PEM strings of two identities are not strictly guaranteed to be
    // equal (they describe structs whose members could be listed in a different
    // order, for example). But because the same function is used to produce
    // both PEMs, its a good enough bet that this comparison will work. If the
    // assumption stops holding in the future we can always remove this from the
    // unittest.
    std::string clone_priv_pem = clone->PrivateKeyToPEMString();
    std::string clone_publ_pem = clone->PublicKeyToPEMString();
    std::string clone_cert_pem = clone->certificate().ToPEMString();
    ASSERT_EQ(priv_pem, clone_priv_pem);
    ASSERT_EQ(publ_pem, clone_publ_pem);
    ASSERT_EQ(cert_pem, clone_cert_pem);
  }

 protected:
  std::unique_ptr<SSLIdentity> identity_rsa1_;
  std::unique_ptr<SSLIdentity> identity_rsa2_;
  std::unique_ptr<SSLIdentity> identity_ecdsa1_;
  std::unique_ptr<SSLIdentity> identity_ecdsa2_;
  std::unique_ptr<rtc::SSLCertificate> test_cert_;
};

TEST_F(SSLIdentityTest, FixedDigestSHA1) {
  TestDigestForFixedCert(rtc::DIGEST_SHA_1, 20, kTestCertSha1);
}

// HASH_AlgSHA224 is not supported in the chromium linux build.
TEST_F(SSLIdentityTest, FixedDigestSHA224) {
  TestDigestForFixedCert(rtc::DIGEST_SHA_224, 28, kTestCertSha224);
}

TEST_F(SSLIdentityTest, FixedDigestSHA256) {
  TestDigestForFixedCert(rtc::DIGEST_SHA_256, 32, kTestCertSha256);
}

TEST_F(SSLIdentityTest, FixedDigestSHA384) {
  TestDigestForFixedCert(rtc::DIGEST_SHA_384, 48, kTestCertSha384);
}

TEST_F(SSLIdentityTest, FixedDigestSHA512) {
  TestDigestForFixedCert(rtc::DIGEST_SHA_512, 64, kTestCertSha512);
}

// HASH_AlgSHA224 is not supported in the chromium linux build.
TEST_F(SSLIdentityTest, DigestSHA224) {
  TestDigestForGeneratedCert(rtc::DIGEST_SHA_224, 28);
}

TEST_F(SSLIdentityTest, DigestSHA256) {
  TestDigestForGeneratedCert(rtc::DIGEST_SHA_256, 32);
}

TEST_F(SSLIdentityTest, DigestSHA384) {
  TestDigestForGeneratedCert(rtc::DIGEST_SHA_384, 48);
}

TEST_F(SSLIdentityTest, DigestSHA512) {
  TestDigestForGeneratedCert(rtc::DIGEST_SHA_512, 64);
}

TEST_F(SSLIdentityTest, IdentityComparison) {
  EXPECT_TRUE(*identity_rsa1_ == *identity_rsa1_);
  EXPECT_FALSE(*identity_rsa1_ == *identity_rsa2_);
  EXPECT_FALSE(*identity_rsa1_ == *identity_ecdsa1_);
  EXPECT_FALSE(*identity_rsa1_ == *identity_ecdsa2_);

  EXPECT_TRUE(*identity_rsa2_ == *identity_rsa2_);
  EXPECT_FALSE(*identity_rsa2_ == *identity_ecdsa1_);
  EXPECT_FALSE(*identity_rsa2_ == *identity_ecdsa2_);

  EXPECT_TRUE(*identity_ecdsa1_ == *identity_ecdsa1_);
  EXPECT_FALSE(*identity_ecdsa1_ == *identity_ecdsa2_);
}

TEST_F(SSLIdentityTest, FromPEMStringsRSA) {
  // These PEM strings were created by generating an identity with
  // |SSLIdentity::Generate| and invoking |identity->PrivateKeyToPEMString()|,
  // |identity->PublicKeyToPEMString()| and
  // |identity->certificate().ToPEMString()|. If the crypto library is updated,
  // and the update changes the string form of the keys, these will have to be
  // updated too.
  static const char kRSA_PRIVATE_KEY_PEM[] =
      "-----BEGIN PRIVATE KEY-----\n"
      "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAMQPqDStRlYeDpkX\n"
      "erRmv+a1naM8vSVSY0gG2plnrnofViWRW3MRqWC+020MsIj3hPZeSAnt/y/FL/nr\n"
      "4Ea7NXcwdRo1/1xEK7U/f/cjSg1aunyvHCHwcFcMr31HLFvHr0ZgcFwbgIuFLNEl\n"
      "7kK5HMO9APz1ntUjek8BmBj8yMl9AgMBAAECgYA8FWBC5GcNtSBcIinkZyigF0A7\n"
      "6j081sa+J/uNz4xUuI257ZXM6biygUhhvuXK06/XoIULJfhyN0fAm1yb0HtNhiUs\n"
      "kMOYeon6b8FqFaPjrQf7Gr9FMiIHXNK19uegTMKztXyPZoUWlX84X0iawY95x0Y3\n"
      "73f6P2rN2UOjlVVjAQJBAOKy3l2w3Zj2w0oAJox0eMwl+RxBNt1C42SHrob2mFUT\n"
      "rytpVVYOasr8CoDI0kjacjI94sLum+buJoXXX6YTGO0CQQDdZwlYIEkoS3ftfxPa\n"
      "Ai0YTBzAWvHJg0r8Gk/TkHo6IM+LSsZ9ZYUv/vBe4BKLw1I4hZ+bQvBiq+f8ROtk\n"
      "+TDRAkAPL3ghwoU1h+IRBO2QHwUwd6K2N9AbBi4BP+168O3HVSg4ujeTKigRLMzv\n"
      "T4R2iNt5bhfQgvdCgtVlxcWMdF8JAkBwDCg3eEdt5BuyjwBt8XH+/O4ED0KUWCTH\n"
      "x00k5dZlupsuhE5Fwe4QpzXg3gekwdnHjyCCQ/NCDHvgOMTkmhQxAkA9V03KRX9b\n"
      "bhvEzY/fu8gEp+EzsER96/D79az5z1BaMGL5OPM2xHBPJATKlswnAa7Lp3QKGZGk\n"
      "TxslfL18J71s\n"
      "-----END PRIVATE KEY-----\n";
  static const char kRSA_PUBLIC_KEY_PEM[] =
      "-----BEGIN PUBLIC KEY-----\n"
      "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDED6g0rUZWHg6ZF3q0Zr/mtZ2j\n"
      "PL0lUmNIBtqZZ656H1YlkVtzEalgvtNtDLCI94T2XkgJ7f8vxS/56+BGuzV3MHUa\n"
      "Nf9cRCu1P3/3I0oNWrp8rxwh8HBXDK99Ryxbx69GYHBcG4CLhSzRJe5CuRzDvQD8\n"
      "9Z7VI3pPAZgY/MjJfQIDAQAB\n"
      "-----END PUBLIC KEY-----\n";
  static const char kCERT_PEM[] =
      "-----BEGIN CERTIFICATE-----\n"
      "MIIBnDCCAQWgAwIBAgIJAOEHLgeWYwrpMA0GCSqGSIb3DQEBCwUAMBAxDjAMBgNV\n"
      "BAMMBXRlc3QxMB4XDTE2MDQyNDE4MTAyMloXDTE2MDUyNTE4MTAyMlowEDEOMAwG\n"
      "A1UEAwwFdGVzdDEwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMQPqDStRlYe\n"
      "DpkXerRmv+a1naM8vSVSY0gG2plnrnofViWRW3MRqWC+020MsIj3hPZeSAnt/y/F\n"
      "L/nr4Ea7NXcwdRo1/1xEK7U/f/cjSg1aunyvHCHwcFcMr31HLFvHr0ZgcFwbgIuF\n"
      "LNEl7kK5HMO9APz1ntUjek8BmBj8yMl9AgMBAAEwDQYJKoZIhvcNAQELBQADgYEA\n"
      "C3ehaZFl+oEYN069C2ht/gMzuC77L854RF/x7xRtNZzkcg9TVgXXdM3auUvJi8dx\n"
      "yTpU3ixErjQvoZew5ngXTEvTY8BSQUijJEaLWh8n6NDKRbEGTdAk8nPAmq9hdCFq\n"
      "e3UkexqNHm3g/VxG4NUC1Y+w29ai0/Rgh+VvgbDwK+Q=\n"
      "-----END CERTIFICATE-----\n";

  std::unique_ptr<SSLIdentity> identity(
      SSLIdentity::FromPEMStrings(kRSA_PRIVATE_KEY_PEM, kCERT_PEM));
  EXPECT_TRUE(identity);
  EXPECT_EQ(kRSA_PRIVATE_KEY_PEM, identity->PrivateKeyToPEMString());
  EXPECT_EQ(kRSA_PUBLIC_KEY_PEM, identity->PublicKeyToPEMString());
  EXPECT_EQ(kCERT_PEM, identity->certificate().ToPEMString());
}

TEST_F(SSLIdentityTest, FromPEMStringsEC) {
  // These PEM strings were created by generating an identity with
  // |SSLIdentity::Generate| and invoking |identity->PrivateKeyToPEMString()|,
  // |identity->PublicKeyToPEMString()| and
  // |identity->certificate().ToPEMString()|. If the crypto library is updated,
  // and the update changes the string form of the keys, these will have to be
  // updated too.
  static const char kECDSA_PRIVATE_KEY_PEM[] =
      "-----BEGIN PRIVATE KEY-----\n"
      "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg/AkEA2hklq7dQ2rN\n"
      "ZxYL6hOUACL4pn7P4FYlA3ZQhIChRANCAAR7YgdO3utP/8IqVRq8G4VZKreMAxeN\n"
      "rUa12twthv4uFjuHAHa9D9oyAjncmn+xvZZRyVmKrA56jRzENcEEHoAg\n"
      "-----END PRIVATE KEY-----\n";
  static const char kECDSA_PUBLIC_KEY_PEM[] =
      "-----BEGIN PUBLIC KEY-----\n"
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEe2IHTt7rT//CKlUavBuFWSq3jAMX\n"
      "ja1GtdrcLYb+LhY7hwB2vQ/aMgI53Jp/sb2WUclZiqwOeo0cxDXBBB6AIA==\n"
      "-----END PUBLIC KEY-----\n";
  static const char kCERT_PEM[] =
      "-----BEGIN CERTIFICATE-----\n"
      "MIIBFDCBu6ADAgECAgkArpkxjw62sW4wCgYIKoZIzj0EAwIwEDEOMAwGA1UEAwwF\n"
      "dGVzdDMwHhcNMTYwNDI0MTgxNDM4WhcNMTYwNTI1MTgxNDM4WjAQMQ4wDAYDVQQD\n"
      "DAV0ZXN0MzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABHtiB07e60//wipVGrwb\n"
      "hVkqt4wDF42tRrXa3C2G/i4WO4cAdr0P2jICOdyaf7G9llHJWYqsDnqNHMQ1wQQe\n"
      "gCAwCgYIKoZIzj0EAwIDSAAwRQIhANyreQ/K5yuPPpirsd0e/4WGLHou6bIOSQks\n"
      "DYzo56NmAiAKOr3u8ol3LmygbUCwEvtWrS8QcJDygxHPACo99hkekw==\n"
      "-----END CERTIFICATE-----\n";

  std::unique_ptr<SSLIdentity> identity(
      SSLIdentity::FromPEMStrings(kECDSA_PRIVATE_KEY_PEM, kCERT_PEM));
  EXPECT_TRUE(identity);
  EXPECT_EQ(kECDSA_PRIVATE_KEY_PEM, identity->PrivateKeyToPEMString());
  EXPECT_EQ(kECDSA_PUBLIC_KEY_PEM, identity->PublicKeyToPEMString());
  EXPECT_EQ(kCERT_PEM, identity->certificate().ToPEMString());
}

TEST_F(SSLIdentityTest, CloneIdentityRSA) {
  TestCloningIdentity(*identity_rsa1_);
  TestCloningIdentity(*identity_rsa2_);
}

TEST_F(SSLIdentityTest, CloneIdentityECDSA) {
  TestCloningIdentity(*identity_ecdsa1_);
  TestCloningIdentity(*identity_ecdsa2_);
}

TEST_F(SSLIdentityTest, PemDerConversion) {
  std::string der;
  EXPECT_TRUE(SSLIdentity::PemToDer("CERTIFICATE", kTestCertificate, &der));

  EXPECT_EQ(kTestCertificate, SSLIdentity::DerToPem(
      "CERTIFICATE",
      reinterpret_cast<const unsigned char*>(der.data()), der.length()));
}

TEST_F(SSLIdentityTest, GetSignatureDigestAlgorithm) {
  TestGetSignatureDigestAlgorithm();
}

class SSLIdentityExpirationTest : public testing::Test {
 public:
  SSLIdentityExpirationTest() {
    // Set use of the test RNG to get deterministic expiration timestamp.
    rtc::SetRandomTestMode(true);
  }
  ~SSLIdentityExpirationTest() {
    // Put it back for the next test.
    rtc::SetRandomTestMode(false);
  }

  void TestASN1TimeToSec() {
    struct asn_example {
      const char* string;
      bool long_format;
      int64_t want;
    } static const data[] = {
      // Valid examples.
      {"19700101000000Z",  true,  0},
      {"700101000000Z",    false, 0},
      {"19700101000001Z",  true,  1},
      {"700101000001Z",    false, 1},
      {"19700101000100Z",  true,  60},
      {"19700101000101Z",  true,  61},
      {"19700101010000Z",  true,  3600},
      {"19700101010001Z",  true,  3601},
      {"19700101010100Z",  true,  3660},
      {"19700101010101Z",  true,  3661},
      {"710911012345Z",    false, 53400225},
      {"20000101000000Z",  true,  946684800},
      {"20000101000000Z",  true,  946684800},
      {"20151130140156Z",  true,  1448892116},
      {"151130140156Z",    false, 1448892116},
      {"20491231235959Z",  true,  2524607999},
      {"491231235959Z",    false, 2524607999},
      {"20500101000000Z",  true,  2524607999+1},
      {"20700101000000Z",  true,  3155760000},
      {"21000101000000Z",  true,  4102444800},
      {"24000101000000Z",  true,  13569465600},

      // Invalid examples.
      {"19700101000000",    true,  -1},  // missing Z long format
      {"19700101000000X",   true,  -1},  // X instead of Z long format
      {"197001010000000",   true,  -1},  // 0 instead of Z long format
      {"1970010100000000Z", true,  -1},  // excess digits long format
      {"700101000000",      false, -1},  // missing Z short format
      {"700101000000X",     false, -1},  // X instead of Z short format
      {"7001010000000",     false, -1},  // 0 instead of Z short format
      {"70010100000000Z",   false, -1},  // excess digits short format
      {":9700101000000Z",   true,  -1},  // invalid character
      {"1:700101000001Z",   true,  -1},  // invalid character
      {"19:00101000100Z",   true,  -1},  // invalid character
      {"197:0101000101Z",   true,  -1},  // invalid character
      {"1970:101010000Z",   true,  -1},  // invalid character
      {"19700:01010001Z",   true,  -1},  // invalid character
      {"197001:1010100Z",   true,  -1},  // invalid character
      {"1970010:010101Z",   true,  -1},  // invalid character
      {"70010100:000Z",     false, -1},  // invalid character
      {"700101000:01Z",     false, -1},  // invalid character
      {"2000010100:000Z",   true,  -1},  // invalid character
      {"21000101000:00Z",   true,  -1},  // invalid character
      {"240001010000:0Z",   true,  -1},  // invalid character
      {"500101000000Z",     false, -1},  // but too old for epoch
      {"691231235959Z",     false, -1},  // too old for epoch
      {"19611118043000Z",   false, -1},  // way too old for epoch
    };

    unsigned char buf[20];

    // Run all examples and check for the expected result.
    for (const auto& entry : data) {
      size_t length = strlen(entry.string);
      memcpy(buf, entry.string, length);    // Copy the ASN1 string...
      buf[length] = rtc::CreateRandomId();  // ...and terminate it with junk.
      int64_t res = rtc::ASN1TimeToSec(buf, length, entry.long_format);
      LOG(LS_VERBOSE) << entry.string;
      ASSERT_EQ(entry.want, res);
    }
    // Run all examples again, but with an invalid length.
    for (const auto& entry : data) {
      size_t length = strlen(entry.string);
      memcpy(buf, entry.string, length);    // Copy the ASN1 string...
      buf[length] = rtc::CreateRandomId();  // ...and terminate it with junk.
      int64_t res = rtc::ASN1TimeToSec(buf, length - 1, entry.long_format);
      LOG(LS_VERBOSE) << entry.string;
      ASSERT_EQ(-1, res);
    }
  }

  void TestExpireTime(int times) {
    // We test just ECDSA here since what we're out to exercise is the
    // interfaces for expiration setting and reading.
    for (int i = 0; i < times; i++) {
      // We limit the time to < 2^31 here, i.e., we stay before 2038, since else
      // we hit time offset limitations in OpenSSL on some 32-bit systems.
      time_t time_before_generation = time(nullptr);
      time_t lifetime =
          rtc::CreateRandomId() % (0x80000000 - time_before_generation);
      rtc::KeyParams key_params = rtc::KeyParams::ECDSA(rtc::EC_NIST_P256);
      SSLIdentity* identity =
          rtc::SSLIdentity::GenerateWithExpiration("", key_params, lifetime);
      time_t time_after_generation = time(nullptr);
      EXPECT_LE(time_before_generation + lifetime,
                identity->certificate().CertificateExpirationTime());
      EXPECT_GE(time_after_generation + lifetime,
                identity->certificate().CertificateExpirationTime());
      delete identity;
    }
  }
};

TEST_F(SSLIdentityExpirationTest, TestASN1TimeToSec) {
  TestASN1TimeToSec();
}

TEST_F(SSLIdentityExpirationTest, TestExpireTime) {
  TestExpireTime(500);
}
