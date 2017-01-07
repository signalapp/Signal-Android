/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifdef HAVE_DBUS_GLIB

#include "webrtc/base/libdbusglibsymboltable.h"

namespace rtc {

#define LATE_BINDING_SYMBOL_TABLE_CLASS_NAME LIBDBUS_GLIB_CLASS_NAME
#define LATE_BINDING_SYMBOL_TABLE_SYMBOLS_LIST LIBDBUS_GLIB_SYMBOLS_LIST
#define LATE_BINDING_SYMBOL_TABLE_DLL_NAME "libdbus-glib-1.so.2"
#include "webrtc/base/latebindingsymboltable.cc.def"

}  // namespace rtc

#endif  // HAVE_DBUS_GLIB
