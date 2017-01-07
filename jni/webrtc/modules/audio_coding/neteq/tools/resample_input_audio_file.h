/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RESAMPLE_INPUT_AUDIO_FILE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RESAMPLE_INPUT_AUDIO_FILE_H_

#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_audio/resampler/include/resampler.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

// Class for handling a looping input audio file with resampling.
class ResampleInputAudioFile : public InputAudioFile {
 public:
  ResampleInputAudioFile(const std::string file_name, int file_rate_hz)
      : InputAudioFile(file_name),
        file_rate_hz_(file_rate_hz),
        output_rate_hz_(-1) {}
  ResampleInputAudioFile(const std::string file_name,
                         int file_rate_hz,
                         int output_rate_hz)
      : InputAudioFile(file_name),
        file_rate_hz_(file_rate_hz),
        output_rate_hz_(output_rate_hz) {}

  bool Read(size_t samples, int output_rate_hz, int16_t* destination);
  bool Read(size_t samples, int16_t* destination) override;
  void set_output_rate_hz(int rate_hz);

 private:
  const int file_rate_hz_;
  int output_rate_hz_;
  Resampler resampler_;
  RTC_DISALLOW_COPY_AND_ASSIGN(ResampleInputAudioFile);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RESAMPLE_INPUT_AUDIO_FILE_H_
