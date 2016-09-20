/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_OUTPUT_AUDIO_FILE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_OUTPUT_AUDIO_FILE_H_

#include <assert.h>
#include <stdio.h>
#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_sink.h"

namespace webrtc {
namespace test {

class OutputAudioFile : public AudioSink {
 public:
  // Creates an OutputAudioFile, opening a file named |file_name| for writing.
  // The file format is 16-bit signed host-endian PCM.
  explicit OutputAudioFile(const std::string& file_name) {
    out_file_ = fopen(file_name.c_str(), "wb");
  }

  virtual ~OutputAudioFile() {
    if (out_file_)
      fclose(out_file_);
  }

  bool WriteArray(const int16_t* audio, size_t num_samples) override {
    assert(out_file_);
    return fwrite(audio, sizeof(*audio), num_samples, out_file_) == num_samples;
  }

 private:
  FILE* out_file_;

  RTC_DISALLOW_COPY_AND_ASSIGN(OutputAudioFile);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_OUTPUT_AUDIO_FILE_H_
