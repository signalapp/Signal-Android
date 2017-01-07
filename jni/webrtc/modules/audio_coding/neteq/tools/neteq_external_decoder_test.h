/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_EXTERNAL_DECODER_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_EXTERNAL_DECODER_TEST_H_

#include <memory>
#include <string>

#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {
namespace test {
// This test class provides a way run NetEQ with an external decoder.
class NetEqExternalDecoderTest {
 protected:
  static const uint8_t kPayloadType = 95;
  static const int kOutputLengthMs = 10;

  // The external decoder |decoder| is suppose to be of type |codec|.
  NetEqExternalDecoderTest(NetEqDecoder codec,
                           int sample_rate_hz,
                           AudioDecoder* decoder);

  virtual ~NetEqExternalDecoderTest() { }

  // In Init(), we register the external decoder.
  void Init();

  // Inserts a new packet with |rtp_header| and |payload| of
  // |payload_size_bytes| bytes. The |receive_timestamp| is an indication
  // of the time when the packet was received, and should be measured with
  // the same tick rate as the RTP timestamp of the current payload.
  virtual void InsertPacket(WebRtcRTPHeader rtp_header,
                            rtc::ArrayView<const uint8_t> payload,
                            uint32_t receive_timestamp);

  // Get 10 ms of audio data.
  void GetOutputAudio(AudioFrame* output);

  NetEq* neteq() { return neteq_.get(); }

 private:
  NetEqDecoder codec_;
  std::string name_ = "dummy name";
  AudioDecoder* decoder_;
  int sample_rate_hz_;
  size_t channels_;
  std::unique_ptr<NetEq> neteq_;
};

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_EXTERNAL_DECODER_TEST_H_
