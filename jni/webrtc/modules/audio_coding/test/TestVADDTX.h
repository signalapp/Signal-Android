/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_TEST_TESTVADDTX_H_
#define WEBRTC_MODULES_AUDIO_CODING_TEST_TESTVADDTX_H_

#include <memory>

#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/test/ACMTest.h"
#include "webrtc/modules/audio_coding/test/Channel.h"

namespace webrtc {

class ActivityMonitor : public ACMVADCallback {
 public:
  ActivityMonitor();
  int32_t InFrameType(FrameType frame_type);
  void PrintStatistics();
  void ResetStatistics();
  void GetStatistics(uint32_t* stats);
 private:
  // 0 - kEmptyFrame
  // 1 - kAudioFrameSpeech
  // 2 - kAudioFrameCN
  // 3 - kVideoFrameKey (not used by audio)
  // 4 - kVideoFrameDelta (not used by audio)
  uint32_t counter_[5];
};


// TestVadDtx is to verify that VAD/DTX perform as they should. It runs through
// an audio file and check if the occurrence of various packet types follows
// expectation. TestVadDtx needs its derived class to implement the Perform()
// to put the test together.
class TestVadDtx : public ACMTest {
 public:
  static const int kOutputFreqHz = 16000;

  TestVadDtx();

  virtual void Perform() = 0;

 protected:
  void RegisterCodec(CodecInst codec_param);

  // Encoding a file and see if the numbers that various packets occur follow
  // the expectation. Saves result to a file.
  // expects[x] means
  // -1 : do not care,
  // 0  : there have been no packets of type |x|,
  // 1  : there have been packets of type |x|,
  // with |x| indicates the following packet types
  // 0 - kEmptyFrame
  // 1 - kAudioFrameSpeech
  // 2 - kAudioFrameCN
  // 3 - kVideoFrameKey (not used by audio)
  // 4 - kVideoFrameDelta (not used by audio)
  void Run(std::string in_filename, int frequency, int channels,
           std::string out_filename, bool append, const int* expects);

  std::unique_ptr<AudioCodingModule> acm_send_;
  std::unique_ptr<AudioCodingModule> acm_receive_;
  std::unique_ptr<Channel> channel_;
  std::unique_ptr<ActivityMonitor> monitor_;
};

// TestWebRtcVadDtx is to verify that the WebRTC VAD/DTX perform as they should.
class TestWebRtcVadDtx final : public TestVadDtx {
 public:
  TestWebRtcVadDtx();

  void Perform() override;

 private:
  void RunTestCases();
  void Test(bool new_outfile);
  void SetVAD(bool enable_dtx, bool enable_vad, ACMVADMode vad_mode);

  bool vad_enabled_;
  bool dtx_enabled_;
  int output_file_num_;
};

// TestOpusDtx is to verify that the Opus DTX performs as it should.
class TestOpusDtx final : public TestVadDtx {
 public:
  void Perform() override;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_TEST_TESTVADDTX_H_
