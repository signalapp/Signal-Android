/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_KEEP_REF_UNTIL_DONE_H_
#define WEBRTC_BASE_KEEP_REF_UNTIL_DONE_H_

#include "webrtc/base/bind.h"
#include "webrtc/base/callback.h"
#include "webrtc/base/refcount.h"
#include "webrtc/base/scoped_ref_ptr.h"

namespace rtc {

namespace impl {
template <class T>
static inline void DoNothing(const scoped_refptr<T>& object) {}
}  // namespace impl

// KeepRefUntilDone keeps a reference to |object| until the returned
// callback goes out of scope. If the returned callback is copied, the
// reference will be released when the last callback goes out of scope.
template <class ObjectT>
static inline Callback0<void> KeepRefUntilDone(ObjectT* object) {
  return rtc::Bind(&impl::DoNothing<ObjectT>, scoped_refptr<ObjectT>(object));
}

template <class ObjectT>
static inline Callback0<void> KeepRefUntilDone(
    const scoped_refptr<ObjectT>& object) {
  return rtc::Bind(&impl::DoNothing<ObjectT>, object);
}

}  // namespace rtc


#endif  // WEBRTC_BASE_KEEP_REF_UNTIL_DONE_H_
