/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_REFCOUNT_H_
#define WEBRTC_BASE_REFCOUNT_H_

#include <string.h>
#include <utility>

#include "webrtc/base/atomicops.h"

namespace rtc {

// Reference count interface.
class RefCountInterface {
 public:
  virtual int AddRef() const = 0;
  virtual int Release() const = 0;

 protected:
  virtual ~RefCountInterface() {}
};

template <class T>
class RefCountedObject : public T {
 public:
  RefCountedObject() {}

  template <typename P>
  explicit RefCountedObject(const P& p) : T(p) {}

  template <typename P>
  explicit RefCountedObject(P&& p) : T(std::move(p)) {}

  template <typename P1, typename P2>
  RefCountedObject(P1 p1, P2 p2) : T(p1, p2) {}

  template <typename P1, typename P2, typename P3>
  RefCountedObject(P1 p1, P2 p2, P3 p3) : T(p1, p2, p3) {}

  template <typename P1, typename P2, typename P3, typename P4>
  RefCountedObject(P1 p1, P2 p2, P3 p3, P4 p4) : T(p1, p2, p3, p4) {}

  template <typename P1, typename P2, typename P3, typename P4, typename P5>
  RefCountedObject(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5) : T(p1, p2, p3, p4, p5) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6>
  RefCountedObject(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, P6 p6)
      : T(p1, p2, p3, p4, p5, p6) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6,
            typename P7>
  RefCountedObject(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, P6 p6, P7 p7)
      : T(p1, p2, p3, p4, p5, p6, p7) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6,
            typename P7,
            typename P8>
  RefCountedObject(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, P6 p6, P7 p7, P8 p8)
      : T(p1, p2, p3, p4, p5, p6, p7, p8) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6,
            typename P7,
            typename P8,
            typename P9>
  RefCountedObject(P1 p1,
                   P2 p2,
                   P3 p3,
                   P4 p4,
                   P5 p5,
                   P6 p6,
                   P7 p7,
                   P8 p8,
                   P9 p9)
      : T(p1, p2, p3, p4, p5, p6, p7, p8, p9) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6,
            typename P7,
            typename P8,
            typename P9,
            typename P10>
  RefCountedObject(P1 p1,
                   P2 p2,
                   P3 p3,
                   P4 p4,
                   P5 p5,
                   P6 p6,
                   P7 p7,
                   P8 p8,
                   P9 p9,
                   P10 p10)
      : T(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10) {}

  template <typename P1,
            typename P2,
            typename P3,
            typename P4,
            typename P5,
            typename P6,
            typename P7,
            typename P8,
            typename P9,
            typename P10,
            typename P11>
  RefCountedObject(P1 p1,
                   P2 p2,
                   P3 p3,
                   P4 p4,
                   P5 p5,
                   P6 p6,
                   P7 p7,
                   P8 p8,
                   P9 p9,
                   P10 p10,
                   P11 p11)
      : T(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11) {}

  virtual int AddRef() const { return AtomicOps::Increment(&ref_count_); }

  virtual int Release() const {
    int count = AtomicOps::Decrement(&ref_count_);
    if (!count) {
      delete this;
    }
    return count;
  }

  // Return whether the reference count is one. If the reference count is used
  // in the conventional way, a reference count of 1 implies that the current
  // thread owns the reference and no other thread shares it. This call
  // performs the test for a reference count of one, and performs the memory
  // barrier needed for the owning thread to act on the object, knowing that it
  // has exclusive access to the object.
  virtual bool HasOneRef() const {
    return AtomicOps::AcquireLoad(&ref_count_) == 1;
  }

 protected:
  virtual ~RefCountedObject() {}

  mutable volatile int ref_count_ = 0;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_REFCOUNT_H_
