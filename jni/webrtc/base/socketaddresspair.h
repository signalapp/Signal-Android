/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SOCKETADDRESSPAIR_H__
#define WEBRTC_BASE_SOCKETADDRESSPAIR_H__

#include "webrtc/base/socketaddress.h"

namespace rtc {

// Records a pair (source,destination) of socket addresses.  The two addresses
// identify a connection between two machines.  (For UDP, this "connection" is
// not maintained explicitly in a socket.)
class SocketAddressPair {
public:
  SocketAddressPair() {}
  SocketAddressPair(const SocketAddress& srs, const SocketAddress& dest);

  const SocketAddress& source() const { return src_; }
  const SocketAddress& destination() const { return dest_; }

  bool operator ==(const SocketAddressPair& r) const;
  bool operator <(const SocketAddressPair& r) const;

  size_t Hash() const;

private:
  SocketAddress src_;
  SocketAddress dest_;
};

} // namespace rtc

#endif // WEBRTC_BASE_SOCKETADDRESSPAIR_H__
