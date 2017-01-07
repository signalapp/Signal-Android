/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_HELPERS_H_
#define WEBRTC_BASE_HELPERS_H_

#include <string>
#include "webrtc/base/basictypes.h"

namespace rtc {

// For testing, we can return predictable data.
void SetRandomTestMode(bool test);

// Initializes the RNG, and seeds it with the specified entropy.
bool InitRandom(int seed);
bool InitRandom(const char* seed, size_t len);

// Generates a (cryptographically) random string of the given length.
// We generate base64 values so that they will be printable.
// WARNING: could silently fail. Use the version below instead.
std::string CreateRandomString(size_t length);

// Generates a (cryptographically) random string of the given length.
// We generate base64 values so that they will be printable.
// Return false if the random number generator failed.
bool CreateRandomString(size_t length, std::string* str);

// Generates a (cryptographically) random string of the given length,
// with characters from the given table. Return false if the random
// number generator failed.
bool CreateRandomString(size_t length, const std::string& table,
                        std::string* str);

// Generates a (cryptographically) random UUID version 4 string.
std::string CreateRandomUuid();

// Generates a random id.
uint32_t CreateRandomId();

// Generates a 64 bit random id.
uint64_t CreateRandomId64();

// Generates a random id > 0.
uint32_t CreateRandomNonZeroId();

// Generates a random double between 0.0 (inclusive) and 1.0 (exclusive).
double CreateRandomDouble();

}  // namespace rtc

#endif  // WEBRTC_BASE_HELPERS_H_
