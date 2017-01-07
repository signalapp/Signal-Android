/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/basictypes.h"

#include "webrtc/base/gunit.h"

namespace rtc {

TEST(BasicTypesTest, Endian) {
  uint16_t v16 = 0x1234u;
  uint8_t first_byte = *reinterpret_cast<uint8_t*>(&v16);
#if defined(RTC_ARCH_CPU_LITTLE_ENDIAN)
  EXPECT_EQ(0x34u, first_byte);
#elif defined(RTC_ARCH_CPU_BIG_ENDIAN)
  EXPECT_EQ(0x12u, first_byte);
#endif
}

TEST(BasicTypesTest, SizeOfConstants) {
  EXPECT_EQ(8u, sizeof(INT64_C(0)));
  EXPECT_EQ(8u, sizeof(UINT64_C(0)));
  EXPECT_EQ(8u, sizeof(INT64_C(0x1234567887654321)));
  EXPECT_EQ(8u, sizeof(UINT64_C(0x8765432112345678)));
}

// Test CPU_ macros
#if !defined(CPU_ARM) && defined(__arm__)
#error expected CPU_ARM to be defined.
#endif
#if !defined(CPU_X86) && (defined(WEBRTC_WIN) || defined(WEBRTC_MAC) && !defined(WEBRTC_IOS))
#error expected CPU_X86 to be defined.
#endif
#if !defined(RTC_ARCH_CPU_LITTLE_ENDIAN) && \
  (defined(WEBRTC_WIN) || defined(WEBRTC_MAC) && !defined(WEBRTC_IOS) || defined(CPU_X86))
#error expected RTC_ARCH_CPU_LITTLE_ENDIAN to be defined.
#endif

// TODO(fbarchard): Test all macros in basictypes.h

}  // namespace rtc
