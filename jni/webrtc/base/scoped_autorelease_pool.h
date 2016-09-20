/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Automatically initialize and and free an autoreleasepool. Never allocate
// an instance of this class using "new" - that will result in a compile-time
// error. Only use it as a stack object.
//
// Note: NSAutoreleasePool docs say that you should not normally need to
// declare an NSAutoreleasePool as a member of an object - but there's nothing
// that indicates it will be a problem, as long as the stack lifetime of the
// pool exactly matches the stack lifetime of the object.

#ifndef WEBRTC_BASE_SCOPED_AUTORELEASE_POOL_H__
#define WEBRTC_BASE_SCOPED_AUTORELEASE_POOL_H__

#if defined(WEBRTC_MAC)

#include "webrtc/base/common.h"
#include "webrtc/base/constructormagic.h"

// This header may be included from Obj-C files or C++ files.
#ifdef __OBJC__
@class NSAutoreleasePool;
#else
class NSAutoreleasePool;
#endif

namespace rtc {

class ScopedAutoreleasePool {
 public:
  ScopedAutoreleasePool();
  ~ScopedAutoreleasePool();

 private:
  // Declaring private overrides of new and delete here enforces the "only use
  // as a stack object" discipline.
  //
  // Note: new is declared as "throw()" to get around a gcc warning about new
  // returning NULL, but this method will never get called and therefore will
  // never actually throw any exception.
  void* operator new(size_t size) throw() { return NULL; }
  void operator delete (void* ptr) {}

  NSAutoreleasePool* pool_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ScopedAutoreleasePool);
};

}  // namespace rtc

#endif  // WEBRTC_MAC
#endif  // WEBRTC_BASE_SCOPED_AUTORELEASE_POOL_H__
