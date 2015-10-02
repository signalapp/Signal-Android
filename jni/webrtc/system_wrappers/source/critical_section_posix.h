/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_POSIX_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_POSIX_H_

#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"

#include <pthread.h>

namespace webrtc {

class CriticalSectionPosix : public CriticalSectionWrapper {
 public:
  CriticalSectionPosix();

  virtual ~CriticalSectionPosix();

  virtual void Enter() OVERRIDE;
  virtual void Leave() OVERRIDE;

 private:
  pthread_mutex_t mutex_;
  friend class ConditionVariablePosix;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_POSIX_H_
