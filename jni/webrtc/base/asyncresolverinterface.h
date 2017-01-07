/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_ASYNCRESOLVERINTERFACE_H_
#define WEBRTC_BASE_ASYNCRESOLVERINTERFACE_H_

#include "webrtc/base/sigslot.h"
#include "webrtc/base/socketaddress.h"

namespace rtc {

// This interface defines the methods to resolve the address asynchronously.
class AsyncResolverInterface {
 public:
  AsyncResolverInterface();
  virtual ~AsyncResolverInterface();

  // Start address resolve process.
  virtual void Start(const SocketAddress& addr) = 0;
  // Returns top most resolved address of |family|
  virtual bool GetResolvedAddress(int family, SocketAddress* addr) const = 0;
  // Returns error from resolver.
  virtual int GetError() const = 0;
  // Delete the resolver.
  virtual void Destroy(bool wait) = 0;
  // Returns top most resolved IPv4 address if address is resolved successfully.
  // Otherwise returns address set in SetAddress.
  SocketAddress address() const {
    SocketAddress addr;
    GetResolvedAddress(AF_INET, &addr);
    return addr;
  }

  // This signal is fired when address resolve process is completed.
  sigslot::signal1<AsyncResolverInterface*> SignalDone;
};

}  // namespace rtc

#endif
