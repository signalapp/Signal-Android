/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_ASYNCFILE_H__
#define WEBRTC_BASE_ASYNCFILE_H__

#include "webrtc/base/sigslot.h"

namespace rtc {

// Provides the ability to perform file I/O asynchronously.
// TODO: Create a common base class with AsyncSocket.
class AsyncFile {
 public:
  AsyncFile();
  virtual ~AsyncFile();

  // Determines whether the file will receive read events.
  virtual bool readable() = 0;
  virtual void set_readable(bool value) = 0;

  // Determines whether the file will receive write events.
  virtual bool writable() = 0;
  virtual void set_writable(bool value) = 0;

  sigslot::signal1<AsyncFile*> SignalReadEvent;
  sigslot::signal1<AsyncFile*> SignalWriteEvent;
  sigslot::signal2<AsyncFile*, int> SignalCloseEvent;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_ASYNCFILE_H__
