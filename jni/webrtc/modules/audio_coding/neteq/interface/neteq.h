/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_NETEQ_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_NETEQ_H_

#include <string.h>  // Provide access to size_t.

#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declarations.
struct WebRtcRTPHeader;

struct NetEqNetworkStatistics {
  uint16_t current_buffer_size_ms;  // Current jitter buffer size in ms.
  uint16_t preferred_buffer_size_ms;  // Target buffer size in ms.
  uint16_t jitter_peaks_found;  // 1 if adding extra delay due to peaky
                                // jitter; 0 otherwise.
  uint16_t packet_loss_rate;  // Loss rate (network + late) in Q14.
  uint16_t packet_discard_rate;  // Late loss rate in Q14.
  uint16_t expand_rate;  // Fraction (of original stream) of synthesized
                         // speech inserted through expansion (in Q14).
  uint16_t preemptive_rate;  // Fraction of data inserted through pre-emptive
                             // expansion (in Q14).
  uint16_t accelerate_rate;  // Fraction of data removed through acceleration
                             // (in Q14).
  int32_t clockdrift_ppm;  // Average clock-drift in parts-per-million
                           // (positive or negative).
  int added_zero_samples;  // Number of zero samples added in "off" mode.
};

enum NetEqOutputType {
  kOutputNormal,
  kOutputPLC,
  kOutputCNG,
  kOutputPLCtoCNG,
  kOutputVADPassive
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
          max_packets_in_buffer(50),
          // |max_delay_ms| has the same effect as calling SetMaximumDelay().
          max_delay_ms(2000),
          background_noise_mode(kBgnOff) {}

    int sample_rate_hz;  // Initial vale. Will change with input data.
    bool enable_audio_classifier;
    int max_packets_in_buffer;
    int max_delay_ms;
    BackgroundNoiseMode background_noise_mode;
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
  static NetEq* Create(const NetEq::Config& config);

  virtual ~NetEq() {}

  // Inserts a new packet into NetEq. The |receive_timestamp| is an indication
  // of the time when the packet was received, and should be measured with
  // the same tick rate as the RTP timestamp of the current payload.
  // Returns 0 on success, -1 on failure.
  virtual int InsertPacket(const WebRtcRTPHeader& rtp_header,
                           const uint8_t* payload,
                           int length_bytes,
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
  // |output_audio|, which can hold (at least) |max_length| elements.
  // The number of channels that were written to the output is provided in
  // the output variable |num_channels|, and each channel contains
  // |samples_per_channel| elements. If more than one channel is written,
  // the samples are interleaved.
  // The speech type is written to |type|, if |type| is not NULL.
  // Returns kOK on success, or kFail in case of an error.
  virtual int GetAudio(size_t max_length, int16_t* output_audio,
                       int* samples_per_channel, int* num_channels,
                       NetEqOutputType* type) = 0;

  // Associates |rtp_payload_type| with |codec| and stores the information in
  // the codec database. Returns 0 on success, -1 on failure.
  virtual int RegisterPayloadType(enum NetEqDecoder codec,
                                  uint8_t rtp_payload_type) = 0;

  // Provides an externally created decoder object |decoder| to insert in the
  // decoder database. The decoder implements a decoder of type |codec| and
  // associates it with |rtp_payload_type|. Returns kOK on success,
  // kFail on failure.
  virtual int RegisterExternalDecoder(AudioDecoder* decoder,
                                      enum NetEqDecoder codec,
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

  // Not implemented.
  virtual int CurrentDelay() = 0;

  // Sets the playout mode to |mode|.
  virtual void SetPlayoutMode(NetEqPlayoutMode mode) = 0;

  // Returns the current playout mode.
  virtual NetEqPlayoutMode PlayoutMode() const = 0;

  // Writes the current network statistics to |stats|. The statistics are reset
  // after the call.
  virtual int NetworkStatistics(NetEqNetworkStatistics* stats) = 0;

  // Writes the last packet waiting times (in ms) to |waiting_times|. The number
  // of values written is no more than 100, but may be smaller if the interface
  // is polled again before 100 packets has arrived.
  virtual void WaitingTimes(std::vector<int>* waiting_times) = 0;

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

  // Gets the RTP timestamp for the last sample delivered by GetAudio().
  // Returns true if the RTP timestamp is valid, otherwise false.
  virtual bool GetPlayoutTimestamp(uint32_t* timestamp) = 0;

  // Not implemented.
  virtual int SetTargetNumberOfChannels() = 0;

  // Not implemented.
  virtual int SetTargetSampleRate() = 0;

  // Returns the error code for the last occurred error. If no error has
  // occurred, 0 is returned.
  virtual int LastError() = 0;

  // Returns the error code last returned by a decoder (audio or comfort noise).
  // When LastError() returns kDecoderErrorCode or kComfortNoiseErrorCode, check
  // this method to get the decoder's error code.
  virtual int LastDecoderError() = 0;

  // Flushes both the packet buffer and the sync buffer.
  virtual void FlushBuffers() = 0;

  // Current usage of packet-buffer and it's limits.
  virtual void PacketBufferStatistics(int* current_num_packets,
                                      int* max_num_packets) const = 0;

  // Get sequence number and timestamp of the latest RTP.
  // This method is to facilitate NACK.
  virtual int DecodedRtpInfo(int* sequence_number,
                             uint32_t* timestamp) const = 0;

 protected:
  NetEq() {}

 private:
  DISALLOW_COPY_AND_ASSIGN(NetEq);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_NETEQ_H_
