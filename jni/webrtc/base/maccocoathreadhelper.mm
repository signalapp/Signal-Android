/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
// Helper function for using Cocoa with Posix threading.

#import <assert.h>
#import <Foundation/Foundation.h>

#import "webrtc/base/maccocoathreadhelper.h"

namespace rtc {

// Cocoa must be "put into multithreading mode" before Cocoa functionality can
// be used on POSIX threads. The way to do that is to spawn one thread that may
// immediately exit.
void InitCocoaMultiThreading() {
  if ([NSThread isMultiThreaded] == NO) {
    // The sole purpose of this autorelease pool is to avoid a console
    // message on Leopard that tells us we're autoreleasing the thread
    // with no autorelease pool in place.
    // Doing NSAutoreleasePool* hack = [[NSAutoreleasePool alloc] init];
    // causes unused variable error.
    NSAutoreleasePool* hack;
    hack = [[NSAutoreleasePool alloc] init];
    [NSThread detachNewThreadSelector:@selector(class)
                             toTarget:[NSObject class]
                           withObject:nil];
    [hack drain];
  }

  assert([NSThread isMultiThreaded]);
}

}  // namespace rtc
