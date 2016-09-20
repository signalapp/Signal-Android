#ifndef __WEB_RTC_AUDIO_DECODER_FACTORY_H__
#define __WEB_RTC_AUDIO_DECODER_FACTORY_H__

#include <vector>

#include <base/checks.h>
#include <base/optional.h>
#include <common_types.h>

#include "WebRtcCodec.h"
#include <sys/types.h>
#include <base/scoped_ref_ptr.h>
#include <modules/audio_coding/codecs/audio_decoder.h>
#include <modules/audio_coding/codecs/audio_decoder_factory.h>

class WebRtcAudioDecoderFactory : public webrtc::AudioDecoderFactory {

private:
  AudioCodec &codec;

  std::unique_ptr<webrtc::AudioDecoder> Unique(webrtc::AudioDecoder* d) {
    return std::unique_ptr<webrtc::AudioDecoder>(d);
  }

public:

  WebRtcAudioDecoderFactory(AudioCodec &codec) : codec(codec) {}

  std::vector<webrtc::AudioCodecSpec> GetSupportedDecoders() {
    static std::vector<webrtc::AudioCodecSpec> specs = {
      { { "speex", 8000, 1 }, true },
    };

    return specs;
  }

  std::unique_ptr<webrtc::AudioDecoder> MakeAudioDecoder(const webrtc::SdpAudioFormat& format) {
    if (STR_CASE_CMP(format.name.c_str(), "speex") == 0) {
      std::unique_ptr<webrtc::AudioDecoder> decoder = Unique(new WebRtcCodec(codec));

      return decoder;
    }

    return nullptr;
  }


  static rtc::scoped_refptr<webrtc::AudioDecoderFactory> Create(AudioCodec &codec) {
    return rtc::scoped_refptr<webrtc::AudioDecoderFactory>(new rtc::RefCountedObject<WebRtcAudioDecoderFactory>(codec));
  }

};

#endif