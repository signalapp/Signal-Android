/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_CONDITION_VARIABLE_WRAPPER_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_CONDITION_VARIABLE_WRAPPER_H_

namespace webrtc {

class CriticalSectionWrapper;

class ConditionVariableWrapper {
 public:
  // Factory method, constructor disabled.
  static ConditionVariableWrapper* CreateConditionVariable();

  virtual ~ConditionVariableWrapper() {}

  // Calling thread will atomically release crit_sect and wait until next
  // some other thread calls Wake() or WakeAll().
  virtual void SleepCS(CriticalSectionWrapper& crit_sect) = 0;

  // Same as above but with a timeout.
  virtual bool SleepCS(CriticalSectionWrapper& crit_sect,
                       unsigned long max_time_in_ms) = 0;

  // Wakes one thread calling SleepCS().
  virtual void Wake() = 0;

  // Wakes all threads calling SleepCS().
  virtual void WakeAll() = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_CONDITION_VARIABLE_WRAPPER_H_
