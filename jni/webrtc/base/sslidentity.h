/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Handling of certificates and keypairs for SSLStreamAdapter's peer mode.

#ifndef WEBRTC_BASE_SSLIDENTITY_H_
#define WEBRTC_BASE_SSLIDENTITY_H_

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/buffer.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/messagedigest.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

// Forward declaration due to circular dependency with SSLCertificate.
class SSLCertChain;

// Abstract interface overridden by SSL library specific
// implementations.

// A somewhat opaque type used to encapsulate a certificate.
// Wraps the SSL library's notion of a certificate, with reference counting.
// The SSLCertificate object is pretty much immutable once created.
// (The OpenSSL implementation only does reference counting and
// possibly caching of intermediate results.)
class SSLCertificate {
 public:
  // Parses and builds a certificate from a PEM encoded string.
  // Returns NULL on failure.
  // The length of the string representation of the certificate is
  // stored in *pem_length if it is non-NULL, and only if
  // parsing was successful.
  // Caller is responsible for freeing the returned object.
  static SSLCertificate* FromPEMString(const std::string& pem_string);
  virtual ~SSLCertificate() {}

  // Returns a new SSLCertificate object instance wrapping the same
  // underlying certificate, including its chain if present.
  // Caller is responsible for freeing the returned object.
  virtual SSLCertificate* GetReference() const = 0;

  // Provides the cert chain, or null. The chain includes a copy of each
  // certificate, excluding the leaf.
  virtual std::unique_ptr<SSLCertChain> GetChain() const = 0;

  // Returns a PEM encoded string representation of the certificate.
  virtual std::string ToPEMString() const = 0;

  // Provides a DER encoded binary representation of the certificate.
  virtual void ToDER(Buffer* der_buffer) const = 0;

  // Gets the name of the digest algorithm that was used to compute this
  // certificate's signature.
  virtual bool GetSignatureDigestAlgorithm(std::string* algorithm) const = 0;

  // Compute the digest of the certificate given algorithm
  virtual bool ComputeDigest(const std::string& algorithm,
                             unsigned char* digest,
                             size_t size,
                             size_t* length) const = 0;

  // Returns the time in seconds relative to epoch, 1970-01-01T00:00:00Z (UTC),
  // or -1 if an expiration time could not be retrieved.
  virtual int64_t CertificateExpirationTime() const = 0;
};

// SSLCertChain is a simple wrapper for a vector of SSLCertificates. It serves
// primarily to ensure proper memory management (especially deletion) of the
// SSLCertificate pointers.
class SSLCertChain {
 public:
  // These constructors copy the provided SSLCertificate(s), so the caller
  // retains ownership.
  explicit SSLCertChain(const std::vector<SSLCertificate*>& certs);
  explicit SSLCertChain(const SSLCertificate* cert);
  ~SSLCertChain();

  // Vector access methods.
  size_t GetSize() const { return certs_.size(); }

  // Returns a temporary reference, only valid until the chain is destroyed.
  const SSLCertificate& Get(size_t pos) const { return *(certs_[pos]); }

  // Returns a new SSLCertChain object instance wrapping the same underlying
  // certificate chain.  Caller is responsible for freeing the returned object.
  SSLCertChain* Copy() const {
    return new SSLCertChain(certs_);
  }

 private:
  // Helper function for duplicating a vector of certificates.
  static SSLCertificate* DupCert(const SSLCertificate* cert) {
    return cert->GetReference();
  }

  // Helper function for deleting a vector of certificates.
  static void DeleteCert(SSLCertificate* cert) { delete cert; }

  std::vector<SSLCertificate*> certs_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SSLCertChain);
};

// KT_LAST is intended for vector declarations and loops over all key types;
// it does not represent any key type in itself.
// KT_DEFAULT is used as the default KeyType for KeyParams.
enum KeyType { KT_RSA, KT_ECDSA, KT_LAST, KT_DEFAULT = KT_ECDSA };

static const int kRsaDefaultModSize = 1024;
static const int kRsaDefaultExponent = 0x10001;  // = 2^16+1 = 65537
static const int kRsaMinModSize = 1024;
static const int kRsaMaxModSize = 8192;

// Certificate default validity lifetime.
static const int kDefaultCertificateLifetimeInSeconds =
    60 * 60 * 24 * 30;  // 30 days
// Certificate validity window.
// This is to compensate for slightly incorrect system clocks.
static const int kCertificateWindowInSeconds = -60 * 60 * 24;

struct RSAParams {
  unsigned int mod_size;
  unsigned int pub_exp;
};

enum ECCurve { EC_NIST_P256, /* EC_FANCY, */ EC_LAST };

class KeyParams {
 public:
  // Generate a KeyParams object from a simple KeyType, using default params.
  explicit KeyParams(KeyType key_type = KT_DEFAULT);

  // Generate a a KeyParams for RSA with explicit parameters.
  static KeyParams RSA(int mod_size = kRsaDefaultModSize,
                       int pub_exp = kRsaDefaultExponent);

  // Generate a a KeyParams for ECDSA specifying the curve.
  static KeyParams ECDSA(ECCurve curve = EC_NIST_P256);

  // Check validity of a KeyParams object. Since the factory functions have
  // no way of returning errors, this function can be called after creation
  // to make sure the parameters are OK.
  bool IsValid() const;

  RSAParams rsa_params() const;

  ECCurve ec_curve() const;

  KeyType type() const { return type_; }

 private:
  KeyType type_;
  union {
    RSAParams rsa;
    ECCurve curve;
  } params_;
};

// TODO(hbos): Remove once rtc::KeyType (to be modified) and
// blink::WebRTCKeyType (to be landed) match. By using this function in Chromium
// appropriately we can change KeyType enum -> class without breaking Chromium.
KeyType IntKeyTypeFamilyToKeyType(int key_type_family);

// Parameters for generating a certificate. If |common_name| is non-empty, it
// will be used for the certificate's subject and issuer name, otherwise a
// random string will be used.
struct SSLIdentityParams {
  std::string common_name;
  time_t not_before;  // Absolute time since epoch in seconds.
  time_t not_after;   // Absolute time since epoch in seconds.
  KeyParams key_params;
};

// Our identity in an SSL negotiation: a keypair and certificate (both
// with the same public key).
// This too is pretty much immutable once created.
class SSLIdentity {
 public:
  // Generates an identity (keypair and self-signed certificate). If
  // |common_name| is non-empty, it will be used for the certificate's subject
  // and issuer name, otherwise a random string will be used. The key type and
  // parameters are defined in |key_param|. The certificate's lifetime in
  // seconds from the current time is defined in |certificate_lifetime|; it
  // should be a non-negative number.
  // Returns NULL on failure.
  // Caller is responsible for freeing the returned object.
  static SSLIdentity* GenerateWithExpiration(const std::string& common_name,
                                             const KeyParams& key_param,
                                             time_t certificate_lifetime);
  static SSLIdentity* Generate(const std::string& common_name,
                               const KeyParams& key_param);
  static SSLIdentity* Generate(const std::string& common_name,
                               KeyType key_type);

  // Generates an identity with the specified validity period.
  // TODO(torbjorng): Now that Generate() accepts relevant params, make tests
  // use that instead of this function.
  static SSLIdentity* GenerateForTest(const SSLIdentityParams& params);

  // Construct an identity from a private key and a certificate.
  static SSLIdentity* FromPEMStrings(const std::string& private_key,
                                     const std::string& certificate);

  virtual ~SSLIdentity() {}

  // Returns a new SSLIdentity object instance wrapping the same
  // identity information.
  // Caller is responsible for freeing the returned object.
  // TODO(hbos,torbjorng): Rename to a less confusing name.
  virtual SSLIdentity* GetReference() const = 0;

  // Returns a temporary reference to the certificate.
  virtual const SSLCertificate& certificate() const = 0;
  virtual std::string PrivateKeyToPEMString() const = 0;
  virtual std::string PublicKeyToPEMString() const = 0;

  // Helpers for parsing converting between PEM and DER format.
  static bool PemToDer(const std::string& pem_type,
                       const std::string& pem_string,
                       std::string* der);
  static std::string DerToPem(const std::string& pem_type,
                              const unsigned char* data,
                              size_t length);
};

bool operator==(const SSLIdentity& a, const SSLIdentity& b);
bool operator!=(const SSLIdentity& a, const SSLIdentity& b);

// Convert from ASN1 time as restricted by RFC 5280 to seconds from 1970-01-01
// 00.00 ("epoch").  If the ASN1 time cannot be read, return -1.  The data at
// |s| is not 0-terminated; its char count is defined by |length|.
int64_t ASN1TimeToSec(const unsigned char* s, size_t length, bool long_format);

extern const char kPemTypeCertificate[];
extern const char kPemTypeRsaPrivateKey[];
extern const char kPemTypeEcPrivateKey[];

}  // namespace rtc

#endif  // WEBRTC_BASE_SSLIDENTITY_H_
