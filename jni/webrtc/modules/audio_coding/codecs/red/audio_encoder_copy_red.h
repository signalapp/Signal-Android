/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_RED_AUDIO_ENCODER_COPY_RED_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_RED_AUDIO_ENCODER_COPY_RED_H_

#include <memory>
#include <vector>

#include "webrtc/base/buffer.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"

namespace webrtc {

// This class implements redundant audio coding. The class object will have an
// underlying AudioEncoder object that performs the actual encodings. The
// current class will gather the two latest encodings from the underlying codec
// into one packet.
class AudioEncoderCopyRed final : public AudioEncoder {
 public:
  struct Config {
    Config();
    Config(Config&&);
    ~Config();
    int payload_type;
    std::unique_ptr<AudioEncoder> speech_encoder;
  };

  explicit AudioEncoderCopyRed(Config&& config);

  ~AudioEncoderCopyRed() override;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  int RtpTimestampRateHz() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  void Reset() override;
  bool SetFec(bool enable) override;
  bool SetDtx(bool enable) override;
  bool SetApplication(Application application) override;
  void SetMaxPlaybackRate(int frequency_hz) override;
  void SetProjectedPacketLossRate(double fraction) override;
  void SetTargetBitrate(int target_bps) override;
  rtc::ArrayView<std::unique_ptr<AudioEncoder>> ReclaimContainedEncoders()
      override;

 protected:
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;

 private:
  std::unique_ptr<AudioEncoder> speech_encoder_;
  int red_payload_type_;
  rtc::Buffer secondary_encoded_;
  EncodedInfoLeaf secondary_info_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderCopyRed);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_RED_AUDIO_ENCODER_COPY_RED_H_
