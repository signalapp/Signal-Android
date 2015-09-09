/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_INPUT_AUDIO_FILE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_INPUT_AUDIO_FILE_H_

#include <stdio.h>

#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

// Class for handling a looping input audio file.
class InputAudioFile {
 public:
  explicit InputAudioFile(const std::string file_name);

  virtual ~InputAudioFile();

  // Reads |samples| elements from source file to |destination|. Returns true
  // if the read was successful, otherwise false. If the file end is reached,
  // the file is rewound and reading continues from the beginning.
  // The output |destination| must have the capacity to hold |samples| elements.
  bool Read(size_t samples, int16_t* destination);

  // Creates a multi-channel signal from a mono signal. Each sample is repeated
  // |channels| times to create an interleaved multi-channel signal where all
  // channels are identical. The output |destination| must have the capacity to
  // hold samples * channels elements.
  static void DuplicateInterleaved(const int16_t* source, size_t samples,
                                   size_t channels, int16_t* destination);

 private:
  FILE* fp_;
  DISALLOW_COPY_AND_ASSIGN(InputAudioFile);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_INPUT_AUDIO_FILE_H_
