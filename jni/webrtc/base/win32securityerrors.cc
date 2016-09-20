/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/win32.h"
#include "webrtc/base/logging.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

extern const ConstantLabel SECURITY_ERRORS[];

const ConstantLabel SECURITY_ERRORS[] = {
  KLABEL(SEC_I_COMPLETE_AND_CONTINUE),
  KLABEL(SEC_I_COMPLETE_NEEDED),
  KLABEL(SEC_I_CONTEXT_EXPIRED),
  KLABEL(SEC_I_CONTINUE_NEEDED),
  KLABEL(SEC_I_INCOMPLETE_CREDENTIALS),
  KLABEL(SEC_I_RENEGOTIATE),
  KLABEL(SEC_E_CERT_EXPIRED),
  KLABEL(SEC_E_INCOMPLETE_MESSAGE),
  KLABEL(SEC_E_INSUFFICIENT_MEMORY),
  KLABEL(SEC_E_INTERNAL_ERROR),
  KLABEL(SEC_E_INVALID_HANDLE),
  KLABEL(SEC_E_INVALID_TOKEN),
  KLABEL(SEC_E_LOGON_DENIED),
  KLABEL(SEC_E_NO_AUTHENTICATING_AUTHORITY),
  KLABEL(SEC_E_NO_CREDENTIALS),
  KLABEL(SEC_E_NOT_OWNER),
  KLABEL(SEC_E_OK),
  KLABEL(SEC_E_SECPKG_NOT_FOUND),
  KLABEL(SEC_E_TARGET_UNKNOWN),
  KLABEL(SEC_E_UNKNOWN_CREDENTIALS),
  KLABEL(SEC_E_UNSUPPORTED_FUNCTION),
  KLABEL(SEC_E_UNTRUSTED_ROOT),
  KLABEL(SEC_E_WRONG_PRINCIPAL),
  LASTLABEL
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
