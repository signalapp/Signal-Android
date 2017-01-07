/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_AUDIO_DECODER_FACTORY_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_AUDIO_DECODER_FACTORY_H_

#include <memory>
#include <vector>

#include "webrtc/base/atomicops.h"
#include "webrtc/base/refcount.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/codecs/audio_format.h"

namespace webrtc {

// A factory that creates AudioDecoders.
// NOTE: This class is still under development and may change without notice.
class AudioDecoderFactory : public rtc::RefCountInterface {
 public:
  virtual std::vector<SdpAudioFormat> GetSupportedFormats() = 0;

  virtual std::unique_ptr<AudioDecoder> MakeAudioDecoder(
      const SdpAudioFormat& format) = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_AUDIO_DECODER_FACTORY_H_
