/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_AUDIO_ENCODER_CNG_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_AUDIO_ENCODER_CNG_H_

#include <memory>
#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_audio/vad/include/vad.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/codecs/cng/webrtc_cng.h"

namespace webrtc {

class Vad;

class AudioEncoderCng final : public AudioEncoder {
 public:
  struct Config {
    Config();
    Config(Config&&);
    ~Config();
    bool IsOk() const;

    size_t num_channels = 1;
    int payload_type = 13;
    std::unique_ptr<AudioEncoder> speech_encoder;
    Vad::Aggressiveness vad_mode = Vad::kVadNormal;
    int sid_frame_interval_ms = 100;
    int num_cng_coefficients = 8;
    // The Vad pointer is mainly for testing. If a NULL pointer is passed, the
    // AudioEncoderCng creates (and destroys) a Vad object internally. If an
    // object is passed, the AudioEncoderCng assumes ownership of the Vad
    // object.
    Vad* vad = nullptr;
  };

  explicit AudioEncoderCng(Config&& config);
  ~AudioEncoderCng() override;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  int RtpTimestampRateHz() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;
  void Reset() override;
  bool SetFec(bool enable) override;
  bool SetDtx(bool enable) override;
  bool SetApplication(Application application) override;
  void SetMaxPlaybackRate(int frequency_hz) override;
  void SetProjectedPacketLossRate(double fraction) override;
  void SetTargetBitrate(int target_bps) override;
  rtc::ArrayView<std::unique_ptr<AudioEncoder>> ReclaimContainedEncoders()
      override;

 private:
  EncodedInfo EncodePassive(size_t frames_to_encode,
                            rtc::Buffer* encoded);
  EncodedInfo EncodeActive(size_t frames_to_encode,
                           rtc::Buffer* encoded);
  size_t SamplesPer10msFrame() const;

  std::unique_ptr<AudioEncoder> speech_encoder_;
  const int cng_payload_type_;
  const int num_cng_coefficients_;
  const int sid_frame_interval_ms_;
  std::vector<int16_t> speech_buffer_;
  std::vector<uint32_t> rtp_timestamps_;
  bool last_frame_active_;
  std::unique_ptr<Vad> vad_;
  std::unique_ptr<ComfortNoiseEncoder> cng_encoder_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderCng);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_AUDIO_ENCODER_CNG_H_
