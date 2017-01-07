/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SSLCONFIG_H_
#define WEBRTC_BASE_SSLCONFIG_H_

// If no preference has been indicated, default to SChannel on Windows and
// OpenSSL everywhere else, if it is available.
#if !defined(SSL_USE_SCHANNEL) && !defined(SSL_USE_OPENSSL)
#if defined(WEBRTC_WIN)

#define SSL_USE_SCHANNEL 1

#else  // defined(WEBRTC_WIN)

#if defined(HAVE_OPENSSL_SSL_H)
#define SSL_USE_OPENSSL 1
#endif

#endif  // !defined(WEBRTC_WIN)
#endif

#endif  // WEBRTC_BASE_SSLCONFIG_H_
