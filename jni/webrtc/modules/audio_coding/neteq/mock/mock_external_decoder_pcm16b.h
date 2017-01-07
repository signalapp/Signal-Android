/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXTERNAL_DECODER_PCM16B_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXTERNAL_DECODER_PCM16B_H_

#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"
#include "webrtc/typedefs.h"

namespace webrtc {

using ::testing::_;
using ::testing::Invoke;

// Implement an external version of the PCM16b decoder.
class ExternalPcm16B : public AudioDecoder {
 public:
  explicit ExternalPcm16B(int sample_rate_hz)
      : sample_rate_hz_(sample_rate_hz) {}
  void Reset() override {}

  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override {
    EXPECT_EQ(sample_rate_hz_, sample_rate_hz);
    size_t ret = WebRtcPcm16b_Decode(encoded, encoded_len, decoded);
    *speech_type = ConvertSpeechType(1);
    return static_cast<int>(ret);
  }
  int SampleRateHz() const override { return sample_rate_hz_; }
  size_t Channels() const override { return 1; }

 private:
  const int sample_rate_hz_;
  RTC_DISALLOW_COPY_AND_ASSIGN(ExternalPcm16B);
};

// Create a mock of ExternalPcm16B which delegates all calls to the real object.
// The reason is that we can then track that the correct calls are being made.
class MockExternalPcm16B : public AudioDecoder {
 public:
  explicit MockExternalPcm16B(int sample_rate_hz) : real_(sample_rate_hz) {
    // By default, all calls are delegated to the real object.
    ON_CALL(*this, DecodeInternal(_, _, _, _, _))
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::DecodeInternal));
    ON_CALL(*this, HasDecodePlc())
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::HasDecodePlc));
    ON_CALL(*this, DecodePlc(_, _))
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::DecodePlc));
    ON_CALL(*this, Reset())
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::Reset));
    ON_CALL(*this, IncomingPacket(_, _, _, _, _))
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::IncomingPacket));
    ON_CALL(*this, ErrorCode())
        .WillByDefault(Invoke(&real_, &ExternalPcm16B::ErrorCode));
  }
  virtual ~MockExternalPcm16B() { Die(); }

  MOCK_METHOD0(Die, void());
  MOCK_METHOD5(DecodeInternal,
               int(const uint8_t* encoded,
                   size_t encoded_len,
                   int sample_rate_hz,
                   int16_t* decoded,
                   SpeechType* speech_type));
  MOCK_CONST_METHOD0(HasDecodePlc,
      bool());
  MOCK_METHOD2(DecodePlc,
      size_t(size_t num_frames, int16_t* decoded));
  MOCK_METHOD0(Reset, void());
  MOCK_METHOD5(IncomingPacket,
      int(const uint8_t* payload, size_t payload_len,
          uint16_t rtp_sequence_number, uint32_t rtp_timestamp,
          uint32_t arrival_timestamp));
  MOCK_METHOD0(ErrorCode,
      int());

  int SampleRateHz() const /* override */ { return real_.SampleRateHz(); }
  size_t Channels() const /* override */ { return real_.Channels(); }

 private:
  ExternalPcm16B real_;
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXTERNAL_DECODER_PCM16B_H_
