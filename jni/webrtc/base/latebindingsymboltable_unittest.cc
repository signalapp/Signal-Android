/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
#include <math.h>
#endif

#include "webrtc/base/gunit.h"
#include "webrtc/base/latebindingsymboltable.h"

namespace rtc {

#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)

#define LIBM_SYMBOLS_CLASS_NAME LibmTestSymbolTable
#define LIBM_SYMBOLS_LIST \
  X(acosf) \
  X(sinf) \
  X(tanf)


#define LATE_BINDING_SYMBOL_TABLE_CLASS_NAME LIBM_SYMBOLS_CLASS_NAME
#define LATE_BINDING_SYMBOL_TABLE_SYMBOLS_LIST LIBM_SYMBOLS_LIST
#include "webrtc/base/latebindingsymboltable.h.def"

#define LATE_BINDING_SYMBOL_TABLE_CLASS_NAME LIBM_SYMBOLS_CLASS_NAME
#define LATE_BINDING_SYMBOL_TABLE_SYMBOLS_LIST LIBM_SYMBOLS_LIST
#define LATE_BINDING_SYMBOL_TABLE_DLL_NAME "libm.so.6"
#include "webrtc/base/latebindingsymboltable.cc.def"

TEST(LateBindingSymbolTable, libm) {
  LibmTestSymbolTable table;
  EXPECT_FALSE(table.IsLoaded());
  ASSERT_TRUE(table.Load());
  EXPECT_TRUE(table.IsLoaded());
  EXPECT_EQ(table.acosf()(0.5f), acosf(0.5f));
  EXPECT_EQ(table.sinf()(0.5f), sinf(0.5f));
  EXPECT_EQ(table.tanf()(0.5f), tanf(0.5f));
  // It would be nice to check that the addresses are the same, but the nature
  // of dynamic linking and relocation makes them actually be different.
  table.Unload();
  EXPECT_FALSE(table.IsLoaded());
}

#else
#error Not implemented
#endif

}  // namespace rtc
