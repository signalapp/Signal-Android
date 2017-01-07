/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SSLADAPTER_H_
#define WEBRTC_BASE_SSLADAPTER_H_

#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/sslstreamadapter.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

class SSLAdapter : public AsyncSocketAdapter {
 public:
  explicit SSLAdapter(AsyncSocket* socket)
    : AsyncSocketAdapter(socket), ignore_bad_cert_(false) { }

  bool ignore_bad_cert() const { return ignore_bad_cert_; }
  void set_ignore_bad_cert(bool ignore) { ignore_bad_cert_ = ignore; }

  // Do DTLS or TLS (default is TLS, if unspecified)
  virtual void SetMode(SSLMode mode) = 0;

  // StartSSL returns 0 if successful.
  // If StartSSL is called while the socket is closed or connecting, the SSL
  // negotiation will begin as soon as the socket connects.
  virtual int StartSSL(const char* hostname, bool restartable) = 0;

  // Create the default SSL adapter for this platform. On failure, returns NULL
  // and deletes |socket|. Otherwise, the returned SSLAdapter takes ownership
  // of |socket|.
  static SSLAdapter* Create(AsyncSocket* socket);

 private:
  // If true, the server certificate need not match the configured hostname.
  bool ignore_bad_cert_;
};

///////////////////////////////////////////////////////////////////////////////

typedef bool (*VerificationCallback)(void* cert);

// Call this on the main thread, before using SSL.
// Call CleanupSSLThread when finished with SSL.
bool InitializeSSL(VerificationCallback callback = NULL);

// Call to initialize additional threads.
bool InitializeSSLThread();

// Call to cleanup additional threads, and also the main thread.
bool CleanupSSL();

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_SSLADAPTER_H_
