/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_LOOP_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_LOOP_H_

#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

// Class serving as an infinite source of audio, realized by looping an audio
// clip.
class AudioLoop {
 public:
  AudioLoop()
      : next_index_(0),
        loop_length_samples_(0),
        block_length_samples_(0) {
  }

  virtual ~AudioLoop() {}

  // Initializes the AudioLoop by reading from |file_name|. The loop will be no
  // longer than |max_loop_length_samples|, if the length of the file is
  // greater. Otherwise, the loop length is the same as the file length.
  // The audio will be delivered in blocks of |block_length_samples|.
  // Returns false if the initialization failed, otherwise true.
  bool Init(const std::string file_name, size_t max_loop_length_samples,
            size_t block_length_samples);

  // Returns a pointer to the next block of audio. The number given as
  // |block_length_samples| to the Init() function determines how many samples
  // that can be safely read from the pointer.
  const int16_t* GetNextBlock();

 private:
  size_t next_index_;
  size_t loop_length_samples_;
  size_t block_length_samples_;
  scoped_ptr<int16_t[]> audio_array_;

  DISALLOW_COPY_AND_ASSIGN(AudioLoop);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_LOOP_H_
