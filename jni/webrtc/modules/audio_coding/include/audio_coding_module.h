/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_
#define WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_

#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/deprecation.h"
#include "webrtc/base/optional.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/include/module.h"
#include "webrtc/system_wrappers/include/clock.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// forward declarations
struct CodecInst;
struct WebRtcRTPHeader;
class AudioDecoder;
class AudioEncoder;
class AudioFrame;
class RTPFragmentationHeader;

#define WEBRTC_10MS_PCM_AUDIO 960  // 16 bits super wideband 48 kHz

// Callback class used for sending data ready to be packetized
class AudioPacketizationCallback {
 public:
  virtual ~AudioPacketizationCallback() {}

  virtual int32_t SendData(FrameType frame_type,
                           uint8_t payload_type,
                           uint32_t timestamp,
                           const uint8_t* payload_data,
                           size_t payload_len_bytes,
                           const RTPFragmentationHeader* fragmentation) = 0;
};

// Callback class used for reporting VAD decision
class ACMVADCallback {
 public:
  virtual ~ACMVADCallback() {}

  virtual int32_t InFrameType(FrameType frame_type) = 0;
};

class AudioCodingModule {
 protected:
  AudioCodingModule() {}

 public:
  struct Config {
    Config() : id(0), neteq_config(), clock(Clock::GetRealTimeClock()) {
      // Post-decode VAD is disabled by default in NetEq, however, Audio
      // Conference Mixer relies on VAD decisions and fails without them.
      neteq_config.enable_post_decode_vad = true;
    }

    int id;
    NetEq::Config neteq_config;
    Clock* clock;
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory;
  };

  ///////////////////////////////////////////////////////////////////////////
  // Creation and destruction of a ACM.
  //
  // The second method is used for testing where a simulated clock can be
  // injected into ACM. ACM will take the ownership of the object clock and
  // delete it when destroyed.
  //
  static AudioCodingModule* Create(int id);
  static AudioCodingModule* Create(int id, Clock* clock);
  static AudioCodingModule* Create(const Config& config);
  virtual ~AudioCodingModule() = default;

  ///////////////////////////////////////////////////////////////////////////
  //   Utility functions
  //

  ///////////////////////////////////////////////////////////////////////////
  // uint8_t NumberOfCodecs()
  // Returns number of supported codecs.
  //
  // Return value:
  //   number of supported codecs.
  ///
  static int NumberOfCodecs();

  ///////////////////////////////////////////////////////////////////////////
  // int32_t Codec()
  // Get supported codec with list number.
  //
  // Input:
  //   -list_id             : list number.
  //
  // Output:
  //   -codec              : a structure where the parameters of the codec,
  //                         given by list number is written to.
  //
  // Return value:
  //   -1 if the list number (list_id) is invalid.
  //    0 if succeeded.
  //
  static int Codec(int list_id, CodecInst* codec);

  ///////////////////////////////////////////////////////////////////////////
  // int32_t Codec()
  // Get supported codec with the given codec name, sampling frequency, and
  // a given number of channels.
  //
  // Input:
  //   -payload_name       : name of the codec.
  //   -sampling_freq_hz   : sampling frequency of the codec. Note! for RED
  //                         a sampling frequency of -1 is a valid input.
  //   -channels           : number of channels ( 1 - mono, 2 - stereo).
  //
  // Output:
  //   -codec              : a structure where the function returns the
  //                         default parameters of the codec.
  //
  // Return value:
  //   -1 if no codec matches the given parameters.
  //    0 if succeeded.
  //
  static int Codec(const char* payload_name, CodecInst* codec,
                   int sampling_freq_hz, size_t channels);

  ///////////////////////////////////////////////////////////////////////////
  // int32_t Codec()
  //
  // Returns the list number of the given codec name, sampling frequency, and
  // a given number of channels.
  //
  // Input:
  //   -payload_name        : name of the codec.
  //   -sampling_freq_hz    : sampling frequency of the codec. Note! for RED
  //                          a sampling frequency of -1 is a valid input.
  //   -channels            : number of channels ( 1 - mono, 2 - stereo).
  //
  // Return value:
  //   if the codec is found, the index of the codec in the list,
  //   -1 if the codec is not found.
  //
  static int Codec(const char* payload_name, int sampling_freq_hz,
                   size_t channels);

  ///////////////////////////////////////////////////////////////////////////
  // bool IsCodecValid()
  // Checks the validity of the parameters of the given codec.
  //
  // Input:
  //   -codec              : the structure which keeps the parameters of the
  //                         codec.
  //
  // Return value:
  //   true if the parameters are valid,
  //   false if any parameter is not valid.
  //
  static bool IsCodecValid(const CodecInst& codec);

  ///////////////////////////////////////////////////////////////////////////
  //   Sender
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t RegisterSendCodec()
  // Registers a codec, specified by |send_codec|, as sending codec.
  // This API can be called multiple of times to register Codec. The last codec
  // registered overwrites the previous ones.
  // The API can also be used to change payload type for CNG and RED, which are
  // registered by default to default payload types.
  // Note that registering CNG and RED won't overwrite speech codecs.
  // This API can be called to set/change the send payload-type, frame-size
  // or encoding rate (if applicable for the codec).
  //
  // Note: If a stereo codec is registered as send codec, VAD/DTX will
  // automatically be turned off, since it is not supported for stereo sending.
  //
  // Note: If a secondary encoder is already registered, and the new send-codec
  // has a sampling rate that does not match the secondary encoder, the
  // secondary encoder will be unregistered.
  //
  // Input:
  //   -send_codec         : Parameters of the codec to be registered, c.f.
  //                         common_types.h for the definition of
  //                         CodecInst.
  //
  // Return value:
  //   -1 if failed to initialize,
  //    0 if succeeded.
  //
  virtual int32_t RegisterSendCodec(const CodecInst& send_codec) = 0;

  // Registers |external_speech_encoder| as encoder. The new encoder will
  // replace any previously registered speech encoder (internal or external).
  virtual void RegisterExternalSendCodec(
      AudioEncoder* external_speech_encoder) = 0;

  // Just like std::function, FunctionView will wrap any callable and hide its
  // actual type, exposing only its signature. But unlike std::function,
  // FunctionView doesn't own its callable---it just points to it. Thus, it's a
  // good choice mainly as a function argument when the callable argument will
  // not be called again once the function has returned.
  template <typename T>
  class FunctionView;  // Undefined.

  template <typename RetT, typename... ArgT>
  class FunctionView<RetT(ArgT...)> final {
   public:
    // This constructor is implicit, so that callers won't have to convert
    // lambdas to FunctionView<Blah(Blah, Blah)> explicitly. This is safe
    // because FunctionView is only a reference to the real callable.
    template <typename F>
    FunctionView(F&& f)
        : f_(&f), call_(Call<typename std::remove_reference<F>::type>) {}

    RetT operator()(ArgT... args) const {
      return call_(f_, std::forward<ArgT>(args)...);
    }

   private:
    template <typename F>
    static RetT Call(void* f, ArgT... args) {
      return (*static_cast<F*>(f))(std::forward<ArgT>(args)...);
    }
    void* f_;
    RetT (*call_)(void* f, ArgT... args);
  };

  // |modifier| is called exactly once with one argument: a pointer to the
  // unique_ptr that holds the current encoder (which is null if there is no
  // current encoder). For the duration of the call, |modifier| has exclusive
  // access to the unique_ptr; it may call the encoder, steal the encoder and
  // replace it with another encoder or with nullptr, etc.
  virtual void ModifyEncoder(
      FunctionView<void(std::unique_ptr<AudioEncoder>*)> modifier) = 0;

  // Utility method for simply replacing the existing encoder with a new one.
  void SetEncoder(std::unique_ptr<AudioEncoder> new_encoder) {
    ModifyEncoder([&](std::unique_ptr<AudioEncoder>* encoder) {
      *encoder = std::move(new_encoder);
    });
  }

  ///////////////////////////////////////////////////////////////////////////
  // int32_t SendCodec()
  // Get parameters for the codec currently registered as send codec.
  //
  // Return value:
  //   The send codec, or nothing if we don't have one
  //
  virtual rtc::Optional<CodecInst> SendCodec() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t SendFrequency()
  // Get the sampling frequency of the current encoder in Hertz.
  //
  // Return value:
  //   positive; sampling frequency [Hz] of the current encoder.
  //   -1 if an error has happened.
  //
  virtual int32_t SendFrequency() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // Sets the bitrate to the specified value in bits/sec. If the value is not
  // supported by the codec, it will choose another appropriate value.
  virtual void SetBitRate(int bitrate_bps) = 0;

  // int32_t RegisterTransportCallback()
  // Register a transport callback which will be called to deliver
  // the encoded buffers whenever Process() is called and a
  // bit-stream is ready.
  //
  // Input:
  //   -transport          : pointer to the callback class
  //                         transport->SendData() is called whenever
  //                         Process() is called and bit-stream is ready
  //                         to deliver.
  //
  // Return value:
  //   -1 if the transport callback could not be registered
  //    0 if registration is successful.
  //
  virtual int32_t RegisterTransportCallback(
      AudioPacketizationCallback* transport) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t Add10MsData()
  // Add 10MS of raw (PCM) audio data and encode it. If the sampling
  // frequency of the audio does not match the sampling frequency of the
  // current encoder ACM will resample the audio. If an encoded packet was
  // produced, it will be delivered via the callback object registered using
  // RegisterTransportCallback, and the return value from this function will
  // be the number of bytes encoded.
  //
  // Input:
  //   -audio_frame        : the input audio frame, containing raw audio
  //                         sampling frequency etc.,
  //                         c.f. module_common_types.h for definition of
  //                         AudioFrame.
  //
  // Return value:
  //   >= 0   number of bytes encoded.
  //     -1   some error occurred.
  //
  virtual int32_t Add10MsData(const AudioFrame& audio_frame) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // (RED) Redundant Coding
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t SetREDStatus()
  // configure RED status i.e. on/off.
  //
  // RFC 2198 describes a solution which has a single payload type which
  // signifies a packet with redundancy. That packet then becomes a container,
  // encapsulating multiple payloads into a single RTP packet.
  // Such a scheme is flexible, since any amount of redundancy may be
  // encapsulated within a single packet.  There is, however, a small overhead
  // since each encapsulated payload must be preceded by a header indicating
  // the type of data enclosed.
  //
  // Input:
  //   -enable_red         : if true RED is enabled, otherwise RED is
  //                         disabled.
  //
  // Return value:
  //   -1 if failed to set RED status,
  //    0 if succeeded.
  //
  virtual int32_t SetREDStatus(bool enable_red) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // bool REDStatus()
  // Get RED status
  //
  // Return value:
  //   true if RED is enabled,
  //   false if RED is disabled.
  //
  virtual bool REDStatus() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // (FEC) Forward Error Correction (codec internal)
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t SetCodecFEC()
  // Configures codec internal FEC status i.e. on/off. No effects on codecs that
  // do not provide internal FEC.
  //
  // Input:
  //   -enable_fec         : if true FEC will be enabled otherwise the FEC is
  //                         disabled.
  //
  // Return value:
  //   -1 if failed, or the codec does not support FEC
  //    0 if succeeded.
  //
  virtual int SetCodecFEC(bool enable_codec_fec) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // bool CodecFEC()
  // Gets status of codec internal FEC.
  //
  // Return value:
  //   true if FEC is enabled,
  //   false if FEC is disabled.
  //
  virtual bool CodecFEC() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int SetPacketLossRate()
  // Sets expected packet loss rate for encoding. Some encoders provide packet
  // loss gnostic encoding to make stream less sensitive to packet losses,
  // through e.g., FEC. No effects on codecs that do not provide such encoding.
  //
  // Input:
  //   -packet_loss_rate   : expected packet loss rate (0 -- 100 inclusive).
  //
  // Return value
  //   -1 if failed to set packet loss rate,
  //   0 if succeeded.
  //
  virtual int SetPacketLossRate(int packet_loss_rate) = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   (VAD) Voice Activity Detection
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t SetVAD()
  // If DTX is enabled & the codec does not have internal DTX/VAD
  // WebRtc VAD will be automatically enabled and |enable_vad| is ignored.
  //
  // If DTX is disabled but VAD is enabled no DTX packets are send,
  // regardless of whether the codec has internal DTX/VAD or not. In this
  // case, WebRtc VAD is running to label frames as active/in-active.
  //
  // NOTE! VAD/DTX is not supported when sending stereo.
  //
  // Inputs:
  //   -enable_dtx         : if true DTX is enabled,
  //                         otherwise DTX is disabled.
  //   -enable_vad         : if true VAD is enabled,
  //                         otherwise VAD is disabled.
  //   -vad_mode           : determines the aggressiveness of VAD. A more
  //                         aggressive mode results in more frames labeled
  //                         as in-active, c.f. definition of
  //                         ACMVADMode in audio_coding_module_typedefs.h
  //                         for valid values.
  //
  // Return value:
  //   -1 if failed to set up VAD/DTX,
  //    0 if succeeded.
  //
  virtual int32_t SetVAD(const bool enable_dtx = true,
                               const bool enable_vad = false,
                               const ACMVADMode vad_mode = VADNormal) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t VAD()
  // Get VAD status.
  //
  // Outputs:
  //   -dtx_enabled        : is set to true if DTX is enabled, otherwise
  //                         is set to false.
  //   -vad_enabled        : is set to true if VAD is enabled, otherwise
  //                         is set to false.
  //   -vad_mode            : is set to the current aggressiveness of VAD.
  //
  // Return value:
  //   -1 if fails to retrieve the setting of DTX/VAD,
  //    0 if succeeded.
  //
  virtual int32_t VAD(bool* dtx_enabled, bool* vad_enabled,
                            ACMVADMode* vad_mode) const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t RegisterVADCallback()
  // Call this method to register a callback function which is called
  // any time that ACM encounters an empty frame. That is a frame which is
  // recognized inactive. Depending on the codec WebRtc VAD or internal codec
  // VAD is employed to identify a frame as active/inactive.
  //
  // Input:
  //   -vad_callback        : pointer to a callback function.
  //
  // Return value:
  //   -1 if failed to register the callback function.
  //    0 if the callback function is registered successfully.
  //
  virtual int32_t RegisterVADCallback(ACMVADCallback* vad_callback) = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   Receiver
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t InitializeReceiver()
  // Any decoder-related state of ACM will be initialized to the
  // same state when ACM is created. This will not interrupt or
  // effect encoding functionality of ACM. ACM would lose all the
  // decoding-related settings by calling this function.
  // For instance, all registered codecs are deleted and have to be
  // registered again.
  //
  // Return value:
  //   -1 if failed to initialize,
  //    0 if succeeded.
  //
  virtual int32_t InitializeReceiver() = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t ReceiveFrequency()
  // Get sampling frequency of the last received payload.
  //
  // Return value:
  //   non-negative the sampling frequency in Hertz.
  //   -1 if an error has occurred.
  //
  virtual int32_t ReceiveFrequency() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t PlayoutFrequency()
  // Get sampling frequency of audio played out.
  //
  // Return value:
  //   the sampling frequency in Hertz.
  //
  virtual int32_t PlayoutFrequency() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t RegisterReceiveCodec()
  // Register possible decoders, can be called multiple times for
  // codecs, CNG-NB, CNG-WB, CNG-SWB, AVT and RED.
  //
  // Input:
  //   -receive_codec      : parameters of the codec to be registered, c.f.
  //                         common_types.h for the definition of
  //                         CodecInst.
  //
  // Return value:
  //   -1 if failed to register the codec
  //    0 if the codec registered successfully.
  //
  virtual int RegisterReceiveCodec(const CodecInst& receive_codec) = 0;

  // Register a decoder; call repeatedly to register multiple decoders. |df| is
  // a decoder factory that returns an iSAC decoder; it will be called once if
  // the decoder being registered is iSAC.
  virtual int RegisterReceiveCodec(
      const CodecInst& receive_codec,
      FunctionView<std::unique_ptr<AudioDecoder>()> isac_factory) = 0;

  // Registers an external decoder. The name is only used to provide information
  // back to the caller about the decoder. Hence, the name is arbitrary, and may
  // be empty.
  virtual int RegisterExternalReceiveCodec(int rtp_payload_type,
                                           AudioDecoder* external_decoder,
                                           int sample_rate_hz,
                                           int num_channels,
                                           const std::string& name) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t UnregisterReceiveCodec()
  // Unregister the codec currently registered with a specific payload type
  // from the list of possible receive codecs.
  //
  // Input:
  //   -payload_type        : The number representing the payload type to
  //                         unregister.
  //
  // Output:
  //   -1 if fails to unregister.
  //    0 if the given codec is successfully unregistered.
  //
  virtual int UnregisterReceiveCodec(
      uint8_t payload_type) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t ReceiveCodec()
  // Get the codec associated with last received payload.
  //
  // Output:
  //   -curr_receive_codec : parameters of the codec associated with the last
  //                         received payload, c.f. common_types.h for
  //                         the definition of CodecInst.
  //
  // Return value:
  //   -1 if failed to retrieve the codec,
  //    0 if the codec is successfully retrieved.
  //
  virtual int32_t ReceiveCodec(CodecInst* curr_receive_codec) const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t IncomingPacket()
  // Call this function to insert a parsed RTP packet into ACM.
  //
  // Inputs:
  //   -incoming_payload   : received payload.
  //   -payload_len_bytes  : the length of payload in bytes.
  //   -rtp_info           : the relevant information retrieved from RTP
  //                         header.
  //
  // Return value:
  //   -1 if failed to push in the payload
  //    0 if payload is successfully pushed in.
  //
  virtual int32_t IncomingPacket(const uint8_t* incoming_payload,
                                 const size_t payload_len_bytes,
                                 const WebRtcRTPHeader& rtp_info) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t IncomingPayload()
  // Call this API to push incoming payloads when there is no rtp-info.
  // The rtp-info will be created in ACM. One usage for this API is when
  // pre-encoded files are pushed in ACM
  //
  // Inputs:
  //   -incoming_payload   : received payload.
  //   -payload_len_byte   : the length, in bytes, of the received payload.
  //   -payload_type       : the payload-type. This specifies which codec has
  //                         to be used to decode the payload.
  //   -timestamp          : send timestamp of the payload. ACM starts with
  //                         a random value and increment it by the
  //                         packet-size, which is given when the codec in
  //                         question is registered by RegisterReceiveCodec().
  //                         Therefore, it is essential to have the timestamp
  //                         if the frame-size differ from the registered
  //                         value or if the incoming payload contains DTX
  //                         packets.
  //
  // Return value:
  //   -1 if failed to push in the payload
  //    0 if payload is successfully pushed in.
  //
  virtual int32_t IncomingPayload(const uint8_t* incoming_payload,
                                  const size_t payload_len_byte,
                                  const uint8_t payload_type,
                                  const uint32_t timestamp = 0) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int SetMinimumPlayoutDelay()
  // Set a minimum for the playout delay, used for lip-sync. NetEq maintains
  // such a delay unless channel condition yields to a higher delay.
  //
  // Input:
  //   -time_ms            : minimum delay in milliseconds.
  //
  // Return value:
  //   -1 if failed to set the delay,
  //    0 if the minimum delay is set.
  //
  virtual int SetMinimumPlayoutDelay(int time_ms) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int SetMaximumPlayoutDelay()
  // Set a maximum for the playout delay
  //
  // Input:
  //   -time_ms            : maximum delay in milliseconds.
  //
  // Return value:
  //   -1 if failed to set the delay,
  //    0 if the maximum delay is set.
  //
  virtual int SetMaximumPlayoutDelay(int time_ms) = 0;

  //
  // The shortest latency, in milliseconds, required by jitter buffer. This
  // is computed based on inter-arrival times and playout mode of NetEq. The
  // actual delay is the maximum of least-required-delay and the minimum-delay
  // specified by SetMinumumPlayoutDelay() API.
  //
  virtual int LeastRequiredDelayMs() const = 0;

  // int32_t PlayoutTimestamp()
  // The send timestamp of an RTP packet is associated with the decoded
  // audio of the packet in question. This function returns the timestamp of
  // the latest audio obtained by calling PlayoutData10ms().
  //
  // Input:
  //   -timestamp          : a reference to a uint32_t to receive the
  //                         timestamp.
  // Return value:
  //    0 if the output is a correct timestamp.
  //   -1 if failed to output the correct timestamp.
  //
  RTC_DEPRECATED virtual int32_t PlayoutTimestamp(uint32_t* timestamp) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t PlayoutTimestamp()
  // The send timestamp of an RTP packet is associated with the decoded
  // audio of the packet in question. This function returns the timestamp of
  // the latest audio obtained by calling PlayoutData10ms(), or empty if no
  // valid timestamp is available.
  //
  virtual rtc::Optional<uint32_t> PlayoutTimestamp() = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int FilteredCurrentDelayMs()
  // Returns the current total delay from NetEq (packet buffer and sync buffer)
  // in ms, with smoothing applied to even out short-time fluctuations due to
  // jitter. The packet buffer part of the delay is not updated during DTX/CNG
  // periods.
  //
  virtual int FilteredCurrentDelayMs() const = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t PlayoutData10Ms(
  // Get 10 milliseconds of raw audio data for playout, at the given sampling
  // frequency. ACM will perform a resampling if required.
  //
  // Input:
  //   -desired_freq_hz    : the desired sampling frequency, in Hertz, of the
  //                         output audio. If set to -1, the function returns
  //                         the audio at the current sampling frequency.
  //
  // Output:
  //   -audio_frame        : output audio frame which contains raw audio data
  //                         and other relevant parameters, c.f.
  //                         module_common_types.h for the definition of
  //                         AudioFrame.
  //   -muted              : if true, the sample data in audio_frame is not
  //                         populated, and must be interpreted as all zero.
  //
  // Return value:
  //   -1 if the function fails,
  //    0 if the function succeeds.
  //
  virtual int32_t PlayoutData10Ms(int32_t desired_freq_hz,
                                  AudioFrame* audio_frame,
                                  bool* muted) = 0;

  /////////////////////////////////////////////////////////////////////////////
  // Same as above, but without the muted parameter. This methods should not be
  // used if enable_fast_accelerate was set to true in NetEq::Config.
  // TODO(henrik.lundin) Remove this method when downstream dependencies are
  // ready.
  virtual int32_t PlayoutData10Ms(int32_t desired_freq_hz,
                                  AudioFrame* audio_frame) = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   Codec specific
  //

  ///////////////////////////////////////////////////////////////////////////
  // int SetOpusApplication()
  // Sets the intended application if current send codec is Opus. Opus uses this
  // to optimize the encoding for applications like VOIP and music. Currently,
  // two modes are supported: kVoip and kAudio.
  //
  // Input:
  //   - application            : intended application.
  //
  // Return value:
  //   -1 if current send codec is not Opus or error occurred in setting the
  //      Opus application mode.
  //    0 if the Opus application mode is successfully set.
  //
  virtual int SetOpusApplication(OpusApplicationMode application) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int SetOpusMaxPlaybackRate()
  // If current send codec is Opus, informs it about maximum playback rate the
  // receiver will render. Opus can use this information to optimize the bit
  // rate and increase the computation efficiency.
  //
  // Input:
  //   -frequency_hz            : maximum playback rate in Hz.
  //
  // Return value:
  //   -1 if current send codec is not Opus or
  //      error occurred in setting the maximum playback rate,
  //    0 if maximum bandwidth is set successfully.
  //
  virtual int SetOpusMaxPlaybackRate(int frequency_hz) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // EnableOpusDtx()
  // Enable the DTX, if current send codec is Opus.
  //
  // Return value:
  //   -1 if current send codec is not Opus or error occurred in enabling the
  //      Opus DTX.
  //    0 if Opus DTX is enabled successfully.
  //
  virtual int EnableOpusDtx() = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int DisableOpusDtx()
  // If current send codec is Opus, disables its internal DTX.
  //
  // Return value:
  //   -1 if current send codec is not Opus or error occurred in disabling DTX.
  //    0 if Opus DTX is disabled successfully.
  //
  virtual int DisableOpusDtx() = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   statistics
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t  GetNetworkStatistics()
  // Get network statistics. Note that the internal statistics of NetEq are
  // reset by this call.
  //
  // Input:
  //   -network_statistics : a structure that contains network statistics.
  //
  // Return value:
  //   -1 if failed to set the network statistics,
  //    0 if statistics are set successfully.
  //
  virtual int32_t GetNetworkStatistics(
      NetworkStatistics* network_statistics) = 0;

  //
  // Enable NACK and set the maximum size of the NACK list. If NACK is already
  // enable then the maximum NACK list size is modified accordingly.
  //
  // If the sequence number of last received packet is N, the sequence numbers
  // of NACK list are in the range of [N - |max_nack_list_size|, N).
  //
  // |max_nack_list_size| should be positive (none zero) and less than or
  // equal to |Nack::kNackListSizeLimit|. Otherwise, No change is applied and -1
  // is returned. 0 is returned at success.
  //
  virtual int EnableNack(size_t max_nack_list_size) = 0;

  // Disable NACK.
  virtual void DisableNack() = 0;

  //
  // Get a list of packets to be retransmitted. |round_trip_time_ms| is an
  // estimate of the round-trip-time (in milliseconds). Missing packets which
  // will be playout in a shorter time than the round-trip-time (with respect
  // to the time this API is called) will not be included in the list.
  //
  // Negative |round_trip_time_ms| results is an error message and empty list
  // is returned.
  //
  virtual std::vector<uint16_t> GetNackList(
      int64_t round_trip_time_ms) const = 0;

  virtual void GetDecodingCallStatistics(
      AudioDecodingCallStats* call_stats) const = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_
