/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/fake_decode_from_file.h"

#include "webrtc/base/checks.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/modules/rtp_rtcp/source/byte_io.h"

namespace webrtc {
namespace test {

int FakeDecodeFromFile::DecodeInternal(const uint8_t* encoded,
                                       size_t encoded_len,
                                       int /*sample_rate_hz*/,
                                       int16_t* decoded,
                                       SpeechType* speech_type) {
  RTC_CHECK_GE(encoded_len, 8u);
  uint32_t timestamp_to_decode =
      ByteReader<uint32_t>::ReadLittleEndian(encoded);
  uint32_t samples_to_decode =
      ByteReader<uint32_t>::ReadLittleEndian(&encoded[4]);

  if (next_timestamp_from_input_ &&
      timestamp_to_decode != *next_timestamp_from_input_) {
    // A gap in the timestamp sequence is detected. Skip the same number of
    // samples from the file.
    uint32_t jump = timestamp_to_decode - *next_timestamp_from_input_;
    RTC_CHECK(input_->Seek(jump));
  }

  RTC_CHECK(input_->Read(static_cast<size_t>(samples_to_decode), decoded));
  next_timestamp_from_input_ =
      rtc::Optional<uint32_t>(timestamp_to_decode + samples_to_decode);

  if (stereo_) {
    InputAudioFile::DuplicateInterleaved(decoded, samples_to_decode, 2,
                                         decoded);
    samples_to_decode *= 2;
  }

  *speech_type = kSpeech;
  return samples_to_decode;
}

void FakeDecodeFromFile::PrepareEncoded(uint32_t timestamp,
                                        size_t samples,
                                        rtc::ArrayView<uint8_t> encoded) {
  RTC_CHECK_GE(encoded.size(), 8u);
  ByteWriter<uint32_t>::WriteLittleEndian(&encoded[0], timestamp);
  ByteWriter<uint32_t>::WriteLittleEndian(&encoded[4],
                                          rtc::checked_cast<uint32_t>(samples));
}

}  // namespace test
}  // namespace webrtc
