/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/helpers.h"

#include <limits>
#include <memory>

#if defined(FEATURE_ENABLE_SSL)
#include "webrtc/base/sslconfig.h"
#if defined(SSL_USE_OPENSSL)
#include <openssl/rand.h>
#else
#if defined(WEBRTC_WIN)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <ntsecapi.h>
#endif  // WEBRTC_WIN
#endif  // else
#endif  // FEATURE_ENABLED_SSL

#include "webrtc/base/base64.h"
#include "webrtc/base/basictypes.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/timeutils.h"

// Protect against max macro inclusion.
#undef max

namespace rtc {

// Base class for RNG implementations.
class RandomGenerator {
 public:
  virtual ~RandomGenerator() {}
  virtual bool Init(const void* seed, size_t len) = 0;
  virtual bool Generate(void* buf, size_t len) = 0;
};

#if defined(SSL_USE_OPENSSL)
// The OpenSSL RNG.
class SecureRandomGenerator : public RandomGenerator {
 public:
  SecureRandomGenerator() {}
  ~SecureRandomGenerator() override {}
  bool Init(const void* seed, size_t len) override { return true; }
  bool Generate(void* buf, size_t len) override {
    return (RAND_bytes(reinterpret_cast<unsigned char*>(buf), len) > 0);
  }
};

#elif defined(SSL_USE_NSS_RNG)
// The NSS RNG.
class SecureRandomGenerator : public RandomGenerator {
 public:
  SecureRandomGenerator() {}
  ~SecureRandomGenerator() override {}
  bool Init(const void* seed, size_t len) override { return true; }
  bool Generate(void* buf, size_t len) override {
    return (PK11_GenerateRandom(reinterpret_cast<unsigned char*>(buf),
                                static_cast<int>(len)) == SECSuccess);
  }
};

#else
#if defined(WEBRTC_WIN)
class SecureRandomGenerator : public RandomGenerator {
 public:
  SecureRandomGenerator() : advapi32_(NULL), rtl_gen_random_(NULL) {}
  ~SecureRandomGenerator() {
    FreeLibrary(advapi32_);
  }

  virtual bool Init(const void* seed, size_t seed_len) {
    // We don't do any additional seeding on Win32, we just use the CryptoAPI
    // RNG (which is exposed as a hidden function off of ADVAPI32 so that we
    // don't need to drag in all of CryptoAPI)
    if (rtl_gen_random_) {
      return true;
    }

    advapi32_ = LoadLibrary(L"advapi32.dll");
    if (!advapi32_) {
      return false;
    }

    rtl_gen_random_ = reinterpret_cast<RtlGenRandomProc>(
        GetProcAddress(advapi32_, "SystemFunction036"));
    if (!rtl_gen_random_) {
      FreeLibrary(advapi32_);
      return false;
    }

    return true;
  }
  virtual bool Generate(void* buf, size_t len) {
    if (!rtl_gen_random_ && !Init(NULL, 0)) {
      return false;
    }
    return (rtl_gen_random_(buf, static_cast<int>(len)) != FALSE);
  }

 private:
  typedef BOOL (WINAPI *RtlGenRandomProc)(PVOID, ULONG);
  HINSTANCE advapi32_;
  RtlGenRandomProc rtl_gen_random_;
};

#elif !defined(FEATURE_ENABLE_SSL)

// No SSL implementation -- use rand()
class SecureRandomGenerator : public RandomGenerator {
 public:
  virtual bool Init(const void* seed, size_t len) {
    if (len >= 4) {
      srand(*reinterpret_cast<const int*>(seed));
    } else {
      srand(*reinterpret_cast<const char*>(seed));
    }
    return true;
  }
  virtual bool Generate(void* buf, size_t len) {
    char* bytes = reinterpret_cast<char*>(buf);
    for (size_t i = 0; i < len; ++i) {
      bytes[i] = static_cast<char>(rand());
    }
    return true;
  }
};

#else

#error No SSL implementation has been selected!

#endif  // WEBRTC_WIN
#endif

// A test random generator, for predictable output.
class TestRandomGenerator : public RandomGenerator {
 public:
  TestRandomGenerator() : seed_(7) {
  }
  ~TestRandomGenerator() override {
  }
  bool Init(const void* seed, size_t len) override { return true; }
  bool Generate(void* buf, size_t len) override {
    for (size_t i = 0; i < len; ++i) {
      static_cast<uint8_t*>(buf)[i] = static_cast<uint8_t>(GetRandom());
    }
    return true;
  }

 private:
  int GetRandom() {
    return ((seed_ = seed_ * 214013L + 2531011L) >> 16) & 0x7fff;
  }
  int seed_;
};

namespace {

// TODO: Use Base64::Base64Table instead.
static const char kBase64[64] = {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'};

static const char kHex[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                              '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

static const char kUuidDigit17[4] = {'8', '9', 'a', 'b'};

// This round about way of creating a global RNG is to safe-guard against
// indeterminant static initialization order.
std::unique_ptr<RandomGenerator>& GetGlobalRng() {
  RTC_DEFINE_STATIC_LOCAL(std::unique_ptr<RandomGenerator>, global_rng,
                          (new SecureRandomGenerator()));
  return global_rng;
}

RandomGenerator& Rng() {
  return *GetGlobalRng();
}

}  // namespace

void SetRandomTestMode(bool test) {
  if (!test) {
    GetGlobalRng().reset(new SecureRandomGenerator());
  } else {
    GetGlobalRng().reset(new TestRandomGenerator());
  }
}

bool InitRandom(int seed) {
  return InitRandom(reinterpret_cast<const char*>(&seed), sizeof(seed));
}

bool InitRandom(const char* seed, size_t len) {
  if (!Rng().Init(seed, len)) {
    LOG(LS_ERROR) << "Failed to init random generator!";
    return false;
  }
  return true;
}

std::string CreateRandomString(size_t len) {
  std::string str;
  CreateRandomString(len, &str);
  return str;
}

bool CreateRandomString(size_t len,
                        const char* table, int table_size,
                        std::string* str) {
  str->clear();
  std::unique_ptr<uint8_t[]> bytes(new uint8_t[len]);
  if (!Rng().Generate(bytes.get(), len)) {
    LOG(LS_ERROR) << "Failed to generate random string!";
    return false;
  }
  str->reserve(len);
  for (size_t i = 0; i < len; ++i) {
    str->push_back(table[bytes[i] % table_size]);
  }
  return true;
}

bool CreateRandomString(size_t len, std::string* str) {
  return CreateRandomString(len, kBase64, 64, str);
}

bool CreateRandomString(size_t len, const std::string& table,
                        std::string* str) {
  return CreateRandomString(len, table.c_str(),
                            static_cast<int>(table.size()), str);
}

// Version 4 UUID is of the form:
// xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
// Where 'x' is a hex digit, and 'y' is 8, 9, a or b.
std::string CreateRandomUuid() {
  std::string str;
  std::unique_ptr<uint8_t[]> bytes(new uint8_t[31]);
  if (!Rng().Generate(bytes.get(), 31)) {
    LOG(LS_ERROR) << "Failed to generate random string!";
    return str;
  }
  str.reserve(36);
  for (size_t i = 0; i < 8; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  for (size_t i = 8; i < 12; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  str.push_back('4');
  for (size_t i = 12; i < 15; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  str.push_back(kUuidDigit17[bytes[15] % 4]);
  for (size_t i = 16; i < 19; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  for (size_t i = 19; i < 31; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  return str;
}

uint32_t CreateRandomId() {
  uint32_t id;
  if (!Rng().Generate(&id, sizeof(id))) {
    LOG(LS_ERROR) << "Failed to generate random id!";
  }
  return id;
}

uint64_t CreateRandomId64() {
  return static_cast<uint64_t>(CreateRandomId()) << 32 | CreateRandomId();
}

uint32_t CreateRandomNonZeroId() {
  uint32_t id;
  do {
    id = CreateRandomId();
  } while (id == 0);
  return id;
}

double CreateRandomDouble() {
  return CreateRandomId() / (std::numeric_limits<uint32_t>::max() +
                             std::numeric_limits<double>::epsilon());
}

}  // namespace rtc
