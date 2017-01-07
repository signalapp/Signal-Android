/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Stores a collection of pointers that are deleted when the container is
// destructed.

#ifndef WEBRTC_BASE_SCOPEDPTRCOLLECTION_H_
#define WEBRTC_BASE_SCOPEDPTRCOLLECTION_H_

#include <algorithm>
#include <vector>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/constructormagic.h"

namespace rtc {

template<class T>
class ScopedPtrCollection {
 public:
  typedef std::vector<T*> VectorT;

  ScopedPtrCollection() { }
  ~ScopedPtrCollection() {
    for (typename VectorT::iterator it = collection_.begin();
         it != collection_.end(); ++it) {
      delete *it;
    }
  }

  const VectorT& collection() const { return collection_; }
  void Reserve(size_t size) {
    collection_.reserve(size);
  }
  void PushBack(T* t) {
    collection_.push_back(t);
  }

  // Remove |t| from the collection without deleting it.
  void Remove(T* t) {
    collection_.erase(std::remove(collection_.begin(), collection_.end(), t),
                      collection_.end());
  }

 private:
  VectorT collection_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ScopedPtrCollection);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_SCOPEDPTRCOLLECTION_H_
