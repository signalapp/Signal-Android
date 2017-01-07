/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_VECTOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_VECTOR_H_

#include "webrtc/modules/audio_coding/neteq/audio_vector.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockAudioVector : public AudioVector {
 public:
  MOCK_METHOD0(Clear,
      void());
  MOCK_CONST_METHOD1(CopyFrom,
      void(AudioVector<T>* copy_to));
  MOCK_METHOD1(PushFront,
      void(const AudioVector<T>& prepend_this));
  MOCK_METHOD2(PushFront,
      void(const T* prepend_this, size_t length));
  MOCK_METHOD1(PushBack,
      void(const AudioVector<T>& append_this));
  MOCK_METHOD2(PushBack,
      void(const T* append_this, size_t length));
  MOCK_METHOD1(PopFront,
      void(size_t length));
  MOCK_METHOD1(PopBack,
      void(size_t length));
  MOCK_METHOD1(Extend,
      void(size_t extra_length));
  MOCK_METHOD3(InsertAt,
      void(const T* insert_this, size_t length, size_t position));
  MOCK_METHOD3(OverwriteAt,
      void(const T* insert_this, size_t length, size_t position));
  MOCK_CONST_METHOD0(Size,
      size_t());
  MOCK_CONST_METHOD0(Empty,
      bool());
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_VECTOR_H_
