/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/sslstreamadapter.h"
#include "webrtc/base/sslconfig.h"

#if SSL_USE_OPENSSL

#include "webrtc/base/opensslstreamadapter.h"

#endif  // SSL_USE_OPENSSL

///////////////////////////////////////////////////////////////////////////////

namespace rtc {

// TODO(guoweis): Move this to SDP layer and use int form internally.
// webrtc:5043.
const char CS_AES_CM_128_HMAC_SHA1_80[] = "AES_CM_128_HMAC_SHA1_80";
const char CS_AES_CM_128_HMAC_SHA1_32[] = "AES_CM_128_HMAC_SHA1_32";

std::string SrtpCryptoSuiteToName(int crypto_suite) {
  if (crypto_suite == SRTP_AES128_CM_SHA1_32)
    return CS_AES_CM_128_HMAC_SHA1_32;
  if (crypto_suite == SRTP_AES128_CM_SHA1_80)
    return CS_AES_CM_128_HMAC_SHA1_80;
  return std::string();
}

int SrtpCryptoSuiteFromName(const std::string& crypto_suite) {
  if (crypto_suite == CS_AES_CM_128_HMAC_SHA1_32)
    return SRTP_AES128_CM_SHA1_32;
  if (crypto_suite == CS_AES_CM_128_HMAC_SHA1_80)
    return SRTP_AES128_CM_SHA1_80;
  return SRTP_INVALID_CRYPTO_SUITE;
}

SSLStreamAdapter* SSLStreamAdapter::Create(StreamInterface* stream) {
#if SSL_USE_OPENSSL
  return new OpenSSLStreamAdapter(stream);
#else  // !SSL_USE_OPENSSL
  return NULL;
#endif  // SSL_USE_OPENSSL
}

bool SSLStreamAdapter::GetSslCipherSuite(int* cipher_suite) {
  return false;
}

bool SSLStreamAdapter::ExportKeyingMaterial(const std::string& label,
                                            const uint8_t* context,
                                            size_t context_len,
                                            bool use_context,
                                            uint8_t* result,
                                            size_t result_len) {
  return false;  // Default is unsupported
}

bool SSLStreamAdapter::SetDtlsSrtpCryptoSuites(
    const std::vector<int>& crypto_suites) {
  return false;
}

bool SSLStreamAdapter::GetDtlsSrtpCryptoSuite(int* crypto_suite) {
  return false;
}

#if SSL_USE_OPENSSL
bool SSLStreamAdapter::HaveDtls() {
  return OpenSSLStreamAdapter::HaveDtls();
}
bool SSLStreamAdapter::HaveDtlsSrtp() {
  return OpenSSLStreamAdapter::HaveDtlsSrtp();
}
bool SSLStreamAdapter::HaveExporter() {
  return OpenSSLStreamAdapter::HaveExporter();
}
bool SSLStreamAdapter::IsBoringSsl() {
  return OpenSSLStreamAdapter::IsBoringSsl();
}
bool SSLStreamAdapter::IsAcceptableCipher(int cipher, KeyType key_type) {
  return OpenSSLStreamAdapter::IsAcceptableCipher(cipher, key_type);
}
bool SSLStreamAdapter::IsAcceptableCipher(const std::string& cipher,
                                          KeyType key_type) {
  return OpenSSLStreamAdapter::IsAcceptableCipher(cipher, key_type);
}
std::string SSLStreamAdapter::SslCipherSuiteToName(int cipher_suite) {
  return OpenSSLStreamAdapter::SslCipherSuiteToName(cipher_suite);
}
#endif  // SSL_USE_OPENSSL

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
