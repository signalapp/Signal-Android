//
// Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the LICENSE file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS.  All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
//

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FIELD_TRIAL_DEFAULT_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FIELD_TRIAL_DEFAULT_H_

namespace webrtc {
namespace field_trial {

// Optionally initialize field trial from a string.
// This method can be called at most once before any other call into webrtc.
// E.g. before the peer connection factory is constructed.
// Note: trials_string must never be destroyed.
void InitFieldTrialsFromString(const char* trials_string);

const char* GetFieldTrialString();

}  // namespace field_trial
}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FIELD_TRIAL_DEFAULT_H_
