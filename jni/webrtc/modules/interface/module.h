/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_INTERFACE_MODULE_H_
#define MODULES_INTERFACE_MODULE_H_

#include <assert.h>

#include "webrtc/typedefs.h"

namespace webrtc {

class Module {
 public:
  // TODO(henrika): Remove this when chrome is updated.
  // DEPRICATED Change the unique identifier of this object.
  virtual int32_t ChangeUniqueId(const int32_t id) { return 0; }

  // Returns the number of milliseconds until the module want a worker
  // thread to call Process.
  virtual int32_t TimeUntilNextProcess() = 0;

  // Process any pending tasks such as timeouts.
  virtual int32_t Process() = 0;

 protected:
  virtual ~Module() {}
};

// Reference counted version of the module interface.
class RefCountedModule : public Module {
 public:
  // Increase the reference count by one.
  // Returns the incremented reference count.
  // TODO(perkj): Make this pure virtual when Chromium have implemented  
  // reference counting ADM and Video capture module.
  virtual int32_t AddRef() {
    assert(false && "Not implemented.");
    return 1;
  }

  // Decrease the reference count by one.
  // Returns the decreased reference count.
  // Returns 0 if the last reference was just released.
  // When the reference count reach 0 the object will self-destruct.
  // TODO(perkj): Make this pure virtual when Chromium have implemented  
  // reference counting ADM and Video capture module.
  virtual int32_t Release() {
    assert(false && "Not implemented.");
    return 1;
  }

 protected:
  virtual ~RefCountedModule() {}
};

}  // namespace webrtc

#endif  // MODULES_INTERFACE_MODULE_H_
