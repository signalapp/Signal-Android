/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <utility>

#include "webrtc/base/checks.h"
#include "webrtc/base/fakesslidentity.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/rtccertificate.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/base/sslidentity.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

namespace {

static const char* kTestCertCommonName = "RTCCertificateTest's certificate";

}  // namespace

class RTCCertificateTest : public testing::Test {
 public:
  RTCCertificateTest() {}
  ~RTCCertificateTest() {}

 protected:
  scoped_refptr<RTCCertificate> GenerateECDSA() {
    std::unique_ptr<SSLIdentity> identity(
        SSLIdentity::Generate(kTestCertCommonName, KeyParams::ECDSA()));
    RTC_CHECK(identity);
    return RTCCertificate::Create(std::move(identity));
  }

  // Timestamp note:
  //   All timestamps in this unittest are expressed in number of seconds since
  // epoch, 1970-01-01T00:00:00Z (UTC). The RTCCertificate interface uses ms,
  // but only seconds-precision is supported by SSLCertificate. To make the
  // tests clearer we convert everything to seconds since the precision matters
  // when generating certificates or comparing timestamps.
  //   As a result, ExpiresSeconds and HasExpiredSeconds are used instead of
  // RTCCertificate::Expires and ::HasExpired for ms -> s conversion.

  uint64_t NowSeconds() const {
    return TimeNanos() / kNumNanosecsPerSec;
  }

  uint64_t ExpiresSeconds(const scoped_refptr<RTCCertificate>& cert) const {
    uint64_t exp_ms = cert->Expires();
    uint64_t exp_s = exp_ms / kNumMillisecsPerSec;
    // Make sure this did not result in loss of precision.
    RTC_CHECK_EQ(exp_s * kNumMillisecsPerSec, exp_ms);
    return exp_s;
  }

  bool HasExpiredSeconds(const scoped_refptr<RTCCertificate>& cert,
                         uint64_t now_s) const {
    return cert->HasExpired(now_s * kNumMillisecsPerSec);
  }

  // An RTC_CHECK ensures that |expires_s| this is in valid range of time_t as
  // is required by SSLIdentityParams. On some 32-bit systems time_t is limited
  // to < 2^31. On such systems this will fail for expiration times of year 2038
  // or later.
  scoped_refptr<RTCCertificate> GenerateCertificateWithExpires(
      uint64_t expires_s) const {
    RTC_CHECK(IsValueInRangeForNumericType<time_t>(expires_s));

    SSLIdentityParams params;
    params.common_name = kTestCertCommonName;
    params.not_before = 0;
    params.not_after = static_cast<time_t>(expires_s);
    // Certificate type does not matter for our purposes, using ECDSA because it
    // is fast to generate.
    params.key_params = KeyParams::ECDSA();

    std::unique_ptr<SSLIdentity> identity(SSLIdentity::GenerateForTest(params));
    return RTCCertificate::Create(std::move(identity));
  }
};

TEST_F(RTCCertificateTest, NewCertificateNotExpired) {
  // Generate a real certificate without specifying the expiration time.
  // Certificate type doesn't matter, using ECDSA because it's fast to generate.
  scoped_refptr<RTCCertificate> certificate = GenerateECDSA();

  uint64_t now = NowSeconds();
  EXPECT_FALSE(HasExpiredSeconds(certificate, now));
  // Even without specifying the expiration time we would expect it to be valid
  // for at least half an hour.
  EXPECT_FALSE(HasExpiredSeconds(certificate, now + 30*60));
}

TEST_F(RTCCertificateTest, UsesExpiresAskedFor) {
  uint64_t now = NowSeconds();
  scoped_refptr<RTCCertificate> certificate =
      GenerateCertificateWithExpires(now);
  EXPECT_EQ(now, ExpiresSeconds(certificate));
}

TEST_F(RTCCertificateTest, ExpiresInOneSecond) {
  // Generate a certificate that expires in 1s.
  uint64_t now = NowSeconds();
  scoped_refptr<RTCCertificate> certificate =
      GenerateCertificateWithExpires(now + 1);
  // Now it should not have expired.
  EXPECT_FALSE(HasExpiredSeconds(certificate, now));
  // In 2s it should have expired.
  EXPECT_TRUE(HasExpiredSeconds(certificate, now + 2));
}

TEST_F(RTCCertificateTest, DifferentCertificatesNotEqual) {
  scoped_refptr<RTCCertificate> a = GenerateECDSA();
  scoped_refptr<RTCCertificate> b = GenerateECDSA();
  EXPECT_TRUE(*a != *b);
}

TEST_F(RTCCertificateTest, CloneWithPEMSerialization) {
  scoped_refptr<RTCCertificate> orig = GenerateECDSA();

  // To PEM.
  RTCCertificatePEM orig_pem = orig->ToPEM();
  // Clone from PEM.
  scoped_refptr<RTCCertificate> clone = RTCCertificate::FromPEM(orig_pem);
  EXPECT_TRUE(clone);
  EXPECT_TRUE(*orig == *clone);
  EXPECT_EQ(orig->Expires(), clone->Expires());
}

}  // namespace rtc
