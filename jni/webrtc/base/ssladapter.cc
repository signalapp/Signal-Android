/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/ssladapter.h"

#include "webrtc/base/sslconfig.h"

#if SSL_USE_OPENSSL

#include "openssladapter.h"

#endif

///////////////////////////////////////////////////////////////////////////////

namespace rtc {

SSLAdapter*
SSLAdapter::Create(AsyncSocket* socket) {
#if SSL_USE_OPENSSL
  return new OpenSSLAdapter(socket);
#else  // !SSL_USE_OPENSSL
  delete socket;
  return NULL;
#endif  // SSL_USE_OPENSSL
}

///////////////////////////////////////////////////////////////////////////////

#if SSL_USE_OPENSSL

bool InitializeSSL(VerificationCallback callback) {
  return OpenSSLAdapter::InitializeSSL(callback);
}

bool InitializeSSLThread() {
  return OpenSSLAdapter::InitializeSSLThread();
}

bool CleanupSSL() {
  return OpenSSLAdapter::CleanupSSL();
}

#else  // !SSL_USE_OPENSSL

bool InitializeSSL(VerificationCallback callback) {
  return true;
}

bool InitializeSSLThread() {
  return true;
}

bool CleanupSSL() {
  return true;
}

#endif  // SSL_USE_OPENSSL

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
