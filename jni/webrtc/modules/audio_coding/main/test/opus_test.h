/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_OPUS_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_OPUS_TEST_H_

#include <math.h>

#include "webrtc/modules/audio_coding/main/acm2/acm_opus.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_resampler.h"
#include "webrtc/modules/audio_coding/main/test/ACMTest.h"
#include "webrtc/modules/audio_coding/main/test/Channel.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/audio_coding/main/test/TestStereo.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

class OpusTest : public ACMTest {
 public:
  OpusTest();
  ~OpusTest();

  void Perform();

 private:
  void Run(TestPackStereo* channel, int channels, int bitrate, int frame_length,
           int percent_loss = 0);

  void OpenOutFile(int test_number);

  scoped_ptr<AudioCodingModule> acm_receiver_;
  TestPackStereo* channel_a2b_;
  PCMFile in_file_stereo_;
  PCMFile in_file_mono_;
  PCMFile out_file_;
  PCMFile out_file_standalone_;
  int counter_;
  uint8_t payload_type_;
  int rtp_timestamp_;
  acm2::ACMResampler resampler_;
  WebRtcOpusEncInst* opus_mono_encoder_;
  WebRtcOpusEncInst* opus_stereo_encoder_;
  WebRtcOpusDecInst* opus_mono_decoder_;
  WebRtcOpusDecInst* opus_stereo_decoder_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_TEST_OPUS_TEST_H_
