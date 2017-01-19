/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/audio_vector.h"

#include <assert.h>

#include <algorithm>

#include "webrtc/typedefs.h"

namespace webrtc {

void AudioVector::Clear() {
  first_free_ix_ = 0;
}

void AudioVector::CopyFrom(AudioVector* copy_to) const {
  if (copy_to) {
    copy_to->Reserve(Size());
    assert(copy_to->capacity_ >= Size());
    memcpy(copy_to->array_.get(), array_.get(), Size() * sizeof(int16_t));
    copy_to->first_free_ix_ = first_free_ix_;
  }
}

void AudioVector::PushFront(const AudioVector& prepend_this) {
  size_t insert_length = prepend_this.Size();
  Reserve(Size() + insert_length);
  memmove(&array_[insert_length], &array_[0], Size() * sizeof(int16_t));
  memcpy(&array_[0], &prepend_this.array_[0], insert_length * sizeof(int16_t));
  first_free_ix_ += insert_length;
}

void AudioVector::PushFront(const int16_t* prepend_this, size_t length) {
  // Same operation as InsertAt beginning.
  InsertAt(prepend_this, length, 0);
}

void AudioVector::PushBack(const AudioVector& append_this) {
  PushBack(append_this.array_.get(), append_this.Size());
}

void AudioVector::PushBack(const int16_t* append_this, size_t length) {
  Reserve(Size() + length);
  memcpy(&array_[first_free_ix_], append_this, length * sizeof(int16_t));
  first_free_ix_ += length;
}

void AudioVector::PopFront(size_t length) {
  if (length >= Size()) {
    // Remove all elements.
    Clear();
  } else {
    size_t remaining_samples = Size() - length;
    memmove(&array_[0], &array_[length], remaining_samples * sizeof(int16_t));
    first_free_ix_ -= length;
  }
}

void AudioVector::PopBack(size_t length) {
  // Never remove more than what is in the array.
  length = std::min(length, Size());
  first_free_ix_ -= length;
}

void AudioVector::Extend(size_t extra_length) {
  Reserve(Size() + extra_length);
  memset(&array_[first_free_ix_], 0, extra_length * sizeof(int16_t));
  first_free_ix_ += extra_length;
}

void AudioVector::InsertAt(const int16_t* insert_this,
                           size_t length,
                           size_t position) {
  Reserve(Size() + length);
  // Cap the position at the current vector length, to be sure the iterator
  // does not extend beyond the end of the vector.
  position = std::min(Size(), position);
  int16_t* insert_position_ptr = &array_[position];
  size_t samples_to_move = Size() - position;
  memmove(insert_position_ptr + length, insert_position_ptr,
          samples_to_move * sizeof(int16_t));
  memcpy(insert_position_ptr, insert_this, length * sizeof(int16_t));
  first_free_ix_ += length;
}

void AudioVector::InsertZerosAt(size_t length,
                                size_t position) {
  Reserve(Size() + length);
  // Cap the position at the current vector length, to be sure the iterator
  // does not extend beyond the end of the vector.
  position = std::min(capacity_, position);
  int16_t* insert_position_ptr = &array_[position];
  size_t samples_to_move = Size() - position;
  memmove(insert_position_ptr + length, insert_position_ptr,
          samples_to_move * sizeof(int16_t));
  memset(insert_position_ptr, 0, length * sizeof(int16_t));
  first_free_ix_ += length;
}

void AudioVector::OverwriteAt(const int16_t* insert_this,
                              size_t length,
                              size_t position) {
  // Cap the insert position at the current array length.
  position = std::min(Size(), position);
  Reserve(position + length);
  memcpy(&array_[position], insert_this, length * sizeof(int16_t));
  if (position + length > Size()) {
    // Array was expanded.
    first_free_ix_ += position + length - Size();
  }
}

void AudioVector::CrossFade(const AudioVector& append_this,
                            size_t fade_length) {
  // Fade length cannot be longer than the current vector or |append_this|.
  assert(fade_length <= Size());
  assert(fade_length <= append_this.Size());
  fade_length = std::min(fade_length, Size());
  fade_length = std::min(fade_length, append_this.Size());
  size_t position = Size() - fade_length;
  // Cross fade the overlapping regions.
  // |alpha| is the mixing factor in Q14.
  // TODO(hlundin): Consider skipping +1 in the denominator to produce a
  // smoother cross-fade, in particular at the end of the fade.
  int alpha_step = 16384 / (static_cast<int>(fade_length) + 1);
  int alpha = 16384;
  for (size_t i = 0; i < fade_length; ++i) {
    alpha -= alpha_step;
    array_[position + i] = (alpha * array_[position + i] +
        (16384 - alpha) * append_this[i] + 8192) >> 14;
  }
  assert(alpha >= 0);  // Verify that the slope was correct.
  // Append what is left of |append_this|.
  size_t samples_to_push_back = append_this.Size() - fade_length;
  if (samples_to_push_back > 0)
    PushBack(&append_this[fade_length], samples_to_push_back);
}

const int16_t& AudioVector::operator[](size_t index) const {
  return array_[index];
}

int16_t& AudioVector::operator[](size_t index) {
  return array_[index];
}

void AudioVector::Reserve(size_t n) {
  if (capacity_ < n) {
    scoped_ptr<int16_t[]> temp_array(new int16_t[n]);
    memcpy(temp_array.get(), array_.get(), Size() * sizeof(int16_t));
    array_.swap(temp_array);
    capacity_ = n;
  }
}

}  // namespace webrtc
