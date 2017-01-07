/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/mock/mock_audio_encoder.h"

namespace webrtc {

MockAudioEncoder::FakeEncoding::FakeEncoding(
    const AudioEncoder::EncodedInfo& info)
    : info_(info) { }

MockAudioEncoder::FakeEncoding::FakeEncoding(size_t encoded_bytes) {
  info_.encoded_bytes = encoded_bytes;
}

AudioEncoder::EncodedInfo MockAudioEncoder::FakeEncoding::operator()(
    uint32_t timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  encoded->SetSize(encoded->size() + info_.encoded_bytes);
  return info_;
}

MockAudioEncoder::CopyEncoding::CopyEncoding(
    AudioEncoder::EncodedInfo info,
    rtc::ArrayView<const uint8_t> payload)
    : info_(info), payload_(payload) { }

MockAudioEncoder::CopyEncoding::CopyEncoding(
    rtc::ArrayView<const uint8_t> payload)
    : payload_(payload) {
  info_.encoded_bytes = payload_.size();
}

AudioEncoder::EncodedInfo MockAudioEncoder::CopyEncoding::operator()(
    uint32_t timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  RTC_CHECK(encoded);
  RTC_CHECK_LE(info_.encoded_bytes, payload_.size());
  encoded->AppendData(payload_.data(), info_.encoded_bytes);
  return info_;
}

}  // namespace webrtc
