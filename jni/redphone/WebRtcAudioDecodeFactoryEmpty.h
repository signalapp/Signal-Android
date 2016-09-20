#ifndef __WEB_RTC_AUDIO_DECODER_FACTORY_EMPTY_H__
#define __WEB_RTC_AUDIO_DECODER_FACTORY_EMPTY_H__

#include <vector>

#include <base/checks.h>
#include <base/optional.h>
#include <common_types.h>

#include "WebRtcCodec.h"
#include <sys/types.h>
#include <base/scoped_ref_ptr.h>
#include <modules/audio_coding/codecs/audio_decoder.h>
#include <modules/audio_coding/codecs/audio_decoder_factory.h>

class WebRtcAudioDecoderFactoryEmpty : public webrtc::AudioDecoderFactory {

private:
  std::unique_ptr<webrtc::AudioDecoder> Unique(webrtc::AudioDecoder* d) {
    return std::unique_ptr<webrtc::AudioDecoder>(d);
  }

public:

  WebRtcAudioDecoderFactoryEmpty() {}

  std::vector<webrtc::AudioCodecSpec> GetSupportedDecoders() {
    static std::vector<webrtc::AudioCodecSpec> specs = {
    };

    return specs;
  }

  std::unique_ptr<webrtc::AudioDecoder> MakeAudioDecoder(const webrtc::SdpAudioFormat& format) {
    return nullptr;
  }


  static rtc::scoped_refptr<webrtc::AudioDecoderFactory> Create() {
    return rtc::scoped_refptr<webrtc::AudioDecoderFactory>(new rtc::RefCountedObject<WebRtcAudioDecoderFactoryEmpty>());
  }

};

#endif