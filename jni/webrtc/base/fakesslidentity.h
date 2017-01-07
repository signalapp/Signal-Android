/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_FAKESSLIDENTITY_H_
#define WEBRTC_BASE_FAKESSLIDENTITY_H_

#include <algorithm>
#include <memory>
#include <vector>

#include "webrtc/base/common.h"
#include "webrtc/base/messagedigest.h"
#include "webrtc/base/sslidentity.h"

namespace rtc {

class FakeSSLCertificate : public rtc::SSLCertificate {
 public:
  // SHA-1 is the default digest algorithm because it is available in all build
  // configurations used for unit testing.
  explicit FakeSSLCertificate(const std::string& data)
      : data_(data), digest_algorithm_(DIGEST_SHA_1), expiration_time_(-1) {}
  explicit FakeSSLCertificate(const std::vector<std::string>& certs)
      : data_(certs.front()),
        digest_algorithm_(DIGEST_SHA_1),
        expiration_time_(-1) {
    std::vector<std::string>::const_iterator it;
    // Skip certs[0].
    for (it = certs.begin() + 1; it != certs.end(); ++it) {
      certs_.push_back(FakeSSLCertificate(*it));
    }
  }
  FakeSSLCertificate* GetReference() const override {
    return new FakeSSLCertificate(*this);
  }
  std::string ToPEMString() const override {
    return data_;
  }
  void ToDER(Buffer* der_buffer) const override {
    std::string der_string;
    VERIFY(SSLIdentity::PemToDer(kPemTypeCertificate, data_, &der_string));
    der_buffer->SetData(der_string.c_str(), der_string.size());
  }
  int64_t CertificateExpirationTime() const override {
    return expiration_time_;
  }
  void SetCertificateExpirationTime(int64_t expiration_time) {
    expiration_time_ = expiration_time;
  }
  void set_digest_algorithm(const std::string& algorithm) {
    digest_algorithm_ = algorithm;
  }
  bool GetSignatureDigestAlgorithm(std::string* algorithm) const override {
    *algorithm = digest_algorithm_;
    return true;
  }
  bool ComputeDigest(const std::string& algorithm,
                     unsigned char* digest,
                     size_t size,
                     size_t* length) const override {
    *length = rtc::ComputeDigest(algorithm, data_.c_str(), data_.size(),
                                       digest, size);
    return (*length != 0);
  }
  std::unique_ptr<SSLCertChain> GetChain() const override {
    if (certs_.empty())
      return nullptr;
    std::vector<SSLCertificate*> new_certs(certs_.size());
    std::transform(certs_.begin(), certs_.end(), new_certs.begin(), DupCert);
    std::unique_ptr<SSLCertChain> chain(new SSLCertChain(new_certs));
    std::for_each(new_certs.begin(), new_certs.end(), DeleteCert);
    return chain;
  }

 private:
  static FakeSSLCertificate* DupCert(FakeSSLCertificate cert) {
    return cert.GetReference();
  }
  static void DeleteCert(SSLCertificate* cert) { delete cert; }
  std::string data_;
  std::vector<FakeSSLCertificate> certs_;
  std::string digest_algorithm_;
  // Expiration time in seconds relative to epoch, 1970-01-01T00:00:00Z (UTC).
  int64_t expiration_time_;
};

class FakeSSLIdentity : public rtc::SSLIdentity {
 public:
  explicit FakeSSLIdentity(const std::string& data) : cert_(data) {}
  explicit FakeSSLIdentity(const FakeSSLCertificate& cert) : cert_(cert) {}
  virtual FakeSSLIdentity* GetReference() const {
    return new FakeSSLIdentity(*this);
  }
  virtual const FakeSSLCertificate& certificate() const { return cert_; }
  virtual std::string PrivateKeyToPEMString() const {
    RTC_NOTREACHED();  // Not implemented.
    return "";
  }
  virtual std::string PublicKeyToPEMString() const {
    RTC_NOTREACHED();  // Not implemented.
    return "";
  }
  virtual bool operator==(const SSLIdentity& other) const {
    RTC_NOTREACHED();  // Not implemented.
    return false;
  }
 private:
  FakeSSLCertificate cert_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_FAKESSLIDENTITY_H_
