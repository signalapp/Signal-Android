/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_FORMAT_MACROS_H_
#define WEBRTC_BASE_FORMAT_MACROS_H_

// This file defines the format macros for some integer types and is derived
// from Chromium's base/format_macros.h.

// To print a 64-bit value in a portable way:
//   int64_t value;
//   printf("xyz:%" PRId64, value);
// The "d" in the macro corresponds to %d; you can also use PRIu64 etc.
//
// To print a size_t value in a portable way:
//   size_t size;
//   printf("xyz: %" PRIuS, size);
// The "u" in the macro corresponds to %u, and S is for "size".

#include "webrtc/typedefs.h"

#if defined(WEBRTC_POSIX)

#if (defined(_INTTYPES_H) || defined(_INTTYPES_H_)) && !defined(PRId64)
#error "inttypes.h has already been included before this header file, but "
#error "without __STDC_FORMAT_MACROS defined."
#endif

#if !defined(__STDC_FORMAT_MACROS)
#define __STDC_FORMAT_MACROS
#endif

#include <inttypes.h>

#if !defined(PRIuS)
#define PRIuS "zu"
#endif

// The size of NSInteger and NSUInteger varies between 32-bit and 64-bit
// architectures and Apple does not provides standard format macros and
// recommends casting. This has many drawbacks, so instead define macros
// for formatting those types.
#if defined(WEBRTC_MAC)
#if defined(WEBRTC_ARCH_64_BITS)
#if !defined(PRIdNS)
#define PRIdNS "ld"
#endif
#if !defined(PRIuNS)
#define PRIuNS "lu"
#endif
#if !defined(PRIxNS)
#define PRIxNS "lx"
#endif
#else  // defined(WEBRTC_ARCH_64_BITS)
#if !defined(PRIdNS)
#define PRIdNS "d"
#endif
#if !defined(PRIuNS)
#define PRIuNS "u"
#endif
#if !defined(PRIxNS)
#define PRIxNS "x"
#endif
#endif
#endif  // defined(WEBRTC_MAC)

#else  // WEBRTC_WIN

#include <inttypes.h>

#if !defined(PRId64)
#define PRId64 "I64d"
#endif

#if !defined(PRIu64)
#define PRIu64 "I64u"
#endif

#if !defined(PRIx64)
#define PRIx64 "I64x"
#endif

#if !defined(PRIuS)
#define PRIuS "Iu"
#endif

#endif

#endif  // WEBRTC_BASE_FORMAT_MACROS_H_
