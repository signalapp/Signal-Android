/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_INCLUDE_NETEQ_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_INCLUDE_NETEQ_H_

#include <string.h>  // Provide access to size_t.

#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/optional.h"
#include "webrtc/base/scoped_ref_ptr.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/neteq/audio_decoder_impl.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declarations.
class AudioFrame;
struct WebRtcRTPHeader;
class AudioDecoderFactory;

struct NetEqNetworkStatistics {
  uint16_t current_buffer_size_ms;  // Current jitter buffer size in ms.
  uint16_t preferred_buffer_size_ms;  // Target buffer size in ms.
  uint16_t jitter_peaks_found;  // 1 if adding extra delay due to peaky
                                // jitter; 0 otherwise.
  uint16_t packet_loss_rate;  // Loss rate (network + late) in Q14.
  uint16_t packet_discard_rate;  // Late loss rate in Q14.
  uint16_t expand_rate;  // Fraction (of original stream) of synthesized
                         // audio inserted through expansion (in Q14).
  uint16_t speech_expand_rate;  // Fraction (of original stream) of synthesized
                                // speech inserted through expansion (in Q14).
  uint16_t preemptive_rate;  // Fraction of data inserted through pre-emptive
                             // expansion (in Q14).
  uint16_t accelerate_rate;  // Fraction of data removed through acceleration
                             // (in Q14).
  uint16_t secondary_decoded_rate;  // Fraction of data coming from secondary
                                    // decoding (in Q14).
  int32_t clockdrift_ppm;  // Average clock-drift in parts-per-million
                           // (positive or negative).
  size_t added_zero_samples;  // Number of zero samples added in "off" mode.
  // Statistics for packet waiting times, i.e., the time between a packet
  // arrives until it is decoded.
  int mean_waiting_time_ms;
  int median_waiting_time_ms;
  int min_waiting_time_ms;
  int max_waiting_time_ms;
};

enum NetEqPlayoutMode {
  kPlayoutOn,
  kPlayoutOff,
  kPlayoutFax,
  kPlayoutStreaming
};

// This is the interface class for NetEq.
class NetEq {
 public:
  enum BackgroundNoiseMode {
    kBgnOn,    // Default behavior with eternal noise.
    kBgnFade,  // Noise fades to zero after some time.
    kBgnOff    // Background noise is always zero.
  };

  struct Config {
    Config()
        : sample_rate_hz(16000),
          enable_audio_classifier(false),
          enable_post_decode_vad(false),
          max_packets_in_buffer(50),
          // |max_delay_ms| has the same effect as calling SetMaximumDelay().
          max_delay_ms(2000),
          background_noise_mode(kBgnOff),
          playout_mode(kPlayoutOn),
          enable_fast_accelerate(false) {}

    std::string ToString() const;

    int sample_rate_hz;  // Initial value. Will change with input data.
    bool enable_audio_classifier;
    bool enable_post_decode_vad;
    size_t max_packets_in_buffer;
    int max_delay_ms;
    BackgroundNoiseMode background_noise_mode;
    NetEqPlayoutMode playout_mode;
    bool enable_fast_accelerate;
    bool enable_muted_state = false;
  };

  enum ReturnCodes {
    kOK = 0,
    kFail = -1,
    kNotImplemented = -2
  };

  enum ErrorCodes {
    kNoError = 0,
    kOtherError,
    kInvalidRtpPayloadType,
    kUnknownRtpPayloadType,
    kCodecNotSupported,
    kDecoderExists,
    kDecoderNotFound,
    kInvalidSampleRate,
    kInvalidPointer,
    kAccelerateError,
    kPreemptiveExpandError,
    kComfortNoiseErrorCode,
    kDecoderErrorCode,
    kOtherDecoderError,
    kInvalidOperation,
    kDtmfParameterError,
    kDtmfParsingError,
    kDtmfInsertError,
    kStereoNotSupported,
    kSampleUnderrun,
    kDecodedTooMuch,
    kFrameSplitError,
    kRedundancySplitError,
    kPacketBufferCorruption,
    kSyncPacketNotAccepted
  };

  // Creates a new NetEq object, with parameters set in |config|. The |config|
  // object will only have to be valid for the duration of the call to this
  // method.
  static NetEq* Create(
      const NetEq::Config& config,
      const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory);

  virtual ~NetEq() {}

  // Inserts a new packet into NetEq. The |receive_timestamp| is an indication
  // of the time when the packet was received, and should be measured with
  // the same tick rate as the RTP timestamp of the current payload.
  // Returns 0 on success, -1 on failure.
  virtual int InsertPacket(const WebRtcRTPHeader& rtp_header,
                           rtc::ArrayView<const uint8_t> payload,
                           uint32_t receive_timestamp) = 0;

  // Inserts a sync-packet into packet queue. Sync-packets are decoded to
  // silence and are intended to keep AV-sync intact in an event of long packet
  // losses when Video NACK is enabled but Audio NACK is not. Clients of NetEq
  // might insert sync-packet when they observe that buffer level of NetEq is
  // decreasing below a certain threshold, defined by the application.
  // Sync-packets should have the same payload type as the last audio payload
  // type, i.e. they cannot have DTMF or CNG payload type, nor a codec change
  // can be implied by inserting a sync-packet.
  // Returns kOk on success, kFail on failure.
  virtual int InsertSyncPacket(const WebRtcRTPHeader& rtp_header,
                               uint32_t receive_timestamp) = 0;

  // Instructs NetEq to deliver 10 ms of audio data. The data is written to
  // |audio_frame|. All data in |audio_frame| is wiped; |data_|, |speech_type_|,
  // |num_channels_|, |sample_rate_hz_|, |samples_per_channel_|, and
  // |vad_activity_| are updated upon success. If an error is returned, some
  // fields may not have been updated.
  // If muted state is enabled (through Config::enable_muted_state), |muted|
  // may be set to true after a prolonged expand period. When this happens, the
  // |data_| in |audio_frame| is not written, but should be interpreted as being
  // all zeros.
  // Returns kOK on success, or kFail in case of an error.
  virtual int GetAudio(AudioFrame* audio_frame, bool* muted) = 0;

  // Associates |rtp_payload_type| with |codec| and |codec_name|, and stores the
  // information in the codec database. Returns 0 on success, -1 on failure.
  // The name is only used to provide information back to the caller about the
  // decoders. Hence, the name is arbitrary, and may be empty.
  virtual int RegisterPayloadType(NetEqDecoder codec,
                                  const std::string& codec_name,
                                  uint8_t rtp_payload_type) = 0;

  // Provides an externally created decoder object |decoder| to insert in the
  // decoder database. The decoder implements a decoder of type |codec| and
  // associates it with |rtp_payload_type| and |codec_name|. Returns kOK on
  // success, kFail on failure. The name is only used to provide information
  // back to the caller about the decoders. Hence, the name is arbitrary, and
  // may be empty.
  virtual int RegisterExternalDecoder(AudioDecoder* decoder,
                                      NetEqDecoder codec,
                                      const std::string& codec_name,
                                      uint8_t rtp_payload_type) = 0;

  // Removes |rtp_payload_type| from the codec database. Returns 0 on success,
  // -1 on failure.
  virtual int RemovePayloadType(uint8_t rtp_payload_type) = 0;

  // Sets a minimum delay in millisecond for packet buffer. The minimum is
  // maintained unless a higher latency is dictated by channel condition.
  // Returns true if the minimum is successfully applied, otherwise false is
  // returned.
  virtual bool SetMinimumDelay(int delay_ms) = 0;

  // Sets a maximum delay in milliseconds for packet buffer. The latency will
  // not exceed the given value, even required delay (given the channel
  // conditions) is higher. Calling this method has the same effect as setting
  // the |max_delay_ms| value in the NetEq::Config struct.
  virtual bool SetMaximumDelay(int delay_ms) = 0;

  // The smallest latency required. This is computed bases on inter-arrival
  // time and internal NetEq logic. Note that in computing this latency none of
  // the user defined limits (applied by calling setMinimumDelay() and/or
  // SetMaximumDelay()) are applied.
  virtual int LeastRequiredDelayMs() const = 0;

  // Not implemented.
  virtual int SetTargetDelay() = 0;

  // Not implemented.
  virtual int TargetDelay() = 0;

  // Returns the current total delay (packet buffer and sync buffer) in ms.
  virtual int CurrentDelayMs() const = 0;

  // Returns the current total delay (packet buffer and sync buffer) in ms,
  // with smoothing applied to even out short-time fluctuations due to jitter.
  // The packet buffer part of the delay is not updated during DTX/CNG periods.
  virtual int FilteredCurrentDelayMs() const = 0;

  // Sets the playout mode to |mode|.
  // Deprecated. Set the mode in the Config struct passed to the constructor.
  // TODO(henrik.lundin) Delete.
  virtual void SetPlayoutMode(NetEqPlayoutMode mode) = 0;

  // Returns the current playout mode.
  // Deprecated.
  // TODO(henrik.lundin) Delete.
  virtual NetEqPlayoutMode PlayoutMode() const = 0;

  // Writes the current network statistics to |stats|. The statistics are reset
  // after the call.
  virtual int NetworkStatistics(NetEqNetworkStatistics* stats) = 0;

  // Writes the current RTCP statistics to |stats|. The statistics are reset
  // and a new report period is started with the call.
  virtual void GetRtcpStatistics(RtcpStatistics* stats) = 0;

  // Same as RtcpStatistics(), but does not reset anything.
  virtual void GetRtcpStatisticsNoReset(RtcpStatistics* stats) = 0;

  // Enables post-decode VAD. When enabled, GetAudio() will return
  // kOutputVADPassive when the signal contains no speech.
  virtual void EnableVad() = 0;

  // Disables post-decode VAD.
  virtual void DisableVad() = 0;

  // Returns the RTP timestamp for the last sample delivered by GetAudio().
  // The return value will be empty if no valid timestamp is available.
  virtual rtc::Optional<uint32_t> GetPlayoutTimestamp() const = 0;

  // Returns the sample rate in Hz of the audio produced in the last GetAudio
  // call. If GetAudio has not been called yet, the configured sample rate
  // (Config::sample_rate_hz) is returned.
  virtual int last_output_sample_rate_hz() const = 0;

  // Not implemented.
  virtual int SetTargetNumberOfChannels() = 0;

  // Not implemented.
  virtual int SetTargetSampleRate() = 0;

  // Returns the error code for the last occurred error. If no error has
  // occurred, 0 is returned.
  virtual int LastError() const = 0;

  // Returns the error code last returned by a decoder (audio or comfort noise).
  // When LastError() returns kDecoderErrorCode or kComfortNoiseErrorCode, check
  // this method to get the decoder's error code.
  virtual int LastDecoderError() = 0;

  // Flushes both the packet buffer and the sync buffer.
  virtual void FlushBuffers() = 0;

  // Current usage of packet-buffer and it's limits.
  virtual void PacketBufferStatistics(int* current_num_packets,
                                      int* max_num_packets) const = 0;

  // Enables NACK and sets the maximum size of the NACK list, which should be
  // positive and no larger than Nack::kNackListSizeLimit. If NACK is already
  // enabled then the maximum NACK list size is modified accordingly.
  virtual void EnableNack(size_t max_nack_list_size) = 0;

  virtual void DisableNack() = 0;

  // Returns a list of RTP sequence numbers corresponding to packets to be
  // retransmitted, given an estimate of the round-trip time in milliseconds.
  virtual std::vector<uint16_t> GetNackList(
      int64_t round_trip_time_ms) const = 0;

 protected:
  NetEq() {}

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(NetEq);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_INCLUDE_NETEQ_H_
