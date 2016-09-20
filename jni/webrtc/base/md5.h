/*
 * This is the header file for the MD5 message-digest algorithm.
 * The algorithm is due to Ron Rivest.  This code was
 * written by Colin Plumb in 1993, no copyright is claimed.
 * This code is in the public domain; do with it what you wish.
 *
 * Equivalent code is available from RSA Data Security, Inc.
 * This code has been tested against that, and is equivalent,
 * except that you don't need to include two pages of legalese
 * with every copy.
 * To compute the message digest of a chunk of bytes, declare an
 * MD5Context structure, pass it to MD5Init, call MD5Update as
 * needed on buffers full of bytes, and then call MD5Final, which
 * will fill a supplied 16-byte array with the digest.
 *
 */

// Changes(fbarchard): Ported to C++ and Google style guide.
// Made context first parameter in MD5Final for consistency with Sha1.
// Changes(hellner): added rtc namespace
// Changes(pbos): Reverted types back to uint32(8)_t with _t suffix.

#ifndef WEBRTC_BASE_MD5_H_
#define WEBRTC_BASE_MD5_H_

#include <stdint.h>
#include <stdlib.h>

namespace rtc {

struct MD5Context {
  uint32_t buf[4];
  uint32_t bits[2];
  uint32_t in[16];
};

void MD5Init(MD5Context* context);
void MD5Update(MD5Context* context, const uint8_t* data, size_t len);
void MD5Final(MD5Context* context, uint8_t digest[16]);
void MD5Transform(uint32_t buf[4], const uint32_t in[16]);

}  // namespace rtc

#endif  // WEBRTC_BASE_MD5_H_
