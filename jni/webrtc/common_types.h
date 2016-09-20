/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_TYPES_H_
#define WEBRTC_COMMON_TYPES_H_

#include <assert.h>
#include <stddef.h>
#include <string.h>

#include <string>
#include <vector>

#include "webrtc/typedefs.h"

#if defined(_MSC_VER)
// Disable "new behavior: elements of array will be default initialized"
// warning. Affects OverUseDetectorOptions.
#pragma warning(disable:4351)
#endif

#ifdef WEBRTC_EXPORT
#define WEBRTC_DLLEXPORT _declspec(dllexport)
#elif WEBRTC_DLL
#define WEBRTC_DLLEXPORT _declspec(dllimport)
#else
#define WEBRTC_DLLEXPORT
#endif

#ifndef NULL
#define NULL 0
#endif

#define RTP_PAYLOAD_NAME_SIZE 32u

#if defined(WEBRTC_WIN) || defined(WIN32)
// Compares two strings without regard to case.
#define STR_CASE_CMP(s1, s2) ::_stricmp(s1, s2)
// Compares characters of two strings without regard to case.
#define STR_NCASE_CMP(s1, s2, n) ::_strnicmp(s1, s2, n)
#else
#define STR_CASE_CMP(s1, s2) ::strcasecmp(s1, s2)
#define STR_NCASE_CMP(s1, s2, n) ::strncasecmp(s1, s2, n)
#endif

namespace webrtc {

class Config;

class RewindableStream {
 public:
  virtual ~RewindableStream() {}
  virtual int Rewind() = 0;
};

class InStream : public RewindableStream {
 public:
  // Reads |len| bytes from file to |buf|. Returns the number of bytes read
  // or -1 on error.
  virtual int Read(void* buf, size_t len) = 0;
};

class OutStream : public RewindableStream {
 public:
  // Writes |len| bytes from |buf| to file. The actual writing may happen
  // some time later. Call Flush() to force a write.
  virtual bool Write(const void* buf, size_t len) = 0;
};

enum TraceModule
{
    kTraceUndefined              = 0,
    // not a module, triggered from the engine code
    kTraceVoice                  = 0x0001,
    // not a module, triggered from the engine code
    kTraceVideo                  = 0x0002,
    // not a module, triggered from the utility code
    kTraceUtility                = 0x0003,
    kTraceRtpRtcp                = 0x0004,
    kTraceTransport              = 0x0005,
    kTraceSrtp                   = 0x0006,
    kTraceAudioCoding            = 0x0007,
    kTraceAudioMixerServer       = 0x0008,
    kTraceAudioMixerClient       = 0x0009,
    kTraceFile                   = 0x000a,
    kTraceAudioProcessing        = 0x000b,
    kTraceVideoCoding            = 0x0010,
    kTraceVideoMixer             = 0x0011,
    kTraceAudioDevice            = 0x0012,
    kTraceVideoRenderer          = 0x0014,
    kTraceVideoCapture           = 0x0015,
    kTraceRemoteBitrateEstimator = 0x0017,
};

enum TraceLevel
{
    kTraceNone               = 0x0000,    // no trace
    kTraceStateInfo          = 0x0001,
    kTraceWarning            = 0x0002,
    kTraceError              = 0x0004,
    kTraceCritical           = 0x0008,
    kTraceApiCall            = 0x0010,
    kTraceDefault            = 0x00ff,

    kTraceModuleCall         = 0x0020,
    kTraceMemory             = 0x0100,   // memory info
    kTraceTimer              = 0x0200,   // timing info
    kTraceStream             = 0x0400,   // "continuous" stream of data

    // used for debug purposes
    kTraceDebug              = 0x0800,  // debug
    kTraceInfo               = 0x1000,  // debug info

    // Non-verbose level used by LS_INFO of logging.h. Do not use directly.
    kTraceTerseInfo          = 0x2000,

    kTraceAll                = 0xffff
};

// External Trace API
class TraceCallback {
 public:
  virtual void Print(TraceLevel level, const char* message, int length) = 0;

 protected:
  virtual ~TraceCallback() {}
  TraceCallback() {}
};

enum FileFormats
{
    kFileFormatWavFile        = 1,
    kFileFormatCompressedFile = 2,
    kFileFormatPreencodedFile = 4,
    kFileFormatPcm16kHzFile   = 7,
    kFileFormatPcm8kHzFile    = 8,
    kFileFormatPcm32kHzFile   = 9
};

enum ProcessingTypes
{
    kPlaybackPerChannel = 0,
    kPlaybackAllChannelsMixed,
    kRecordingPerChannel,
    kRecordingAllChannelsMixed,
    kRecordingPreprocessing
};

enum FrameType {
  kEmptyFrame = 0,
  kAudioFrameSpeech = 1,
  kAudioFrameCN = 2,
  kVideoFrameKey = 3,
  kVideoFrameDelta = 4,
};

// Statistics for an RTCP channel
struct RtcpStatistics {
  RtcpStatistics()
    : fraction_lost(0),
      cumulative_lost(0),
      extended_max_sequence_number(0),
      jitter(0) {}

  uint8_t fraction_lost;
  uint32_t cumulative_lost;
  uint32_t extended_max_sequence_number;
  uint32_t jitter;
};

class RtcpStatisticsCallback {
 public:
  virtual ~RtcpStatisticsCallback() {}

  virtual void StatisticsUpdated(const RtcpStatistics& statistics,
                                 uint32_t ssrc) = 0;
  virtual void CNameChanged(const char* cname, uint32_t ssrc) = 0;
};

// Statistics for RTCP packet types.
struct RtcpPacketTypeCounter {
  RtcpPacketTypeCounter()
    : first_packet_time_ms(-1),
      nack_packets(0),
      fir_packets(0),
      pli_packets(0),
      nack_requests(0),
      unique_nack_requests(0) {}

  void Add(const RtcpPacketTypeCounter& other) {
    nack_packets += other.nack_packets;
    fir_packets += other.fir_packets;
    pli_packets += other.pli_packets;
    nack_requests += other.nack_requests;
    unique_nack_requests += other.unique_nack_requests;
    if (other.first_packet_time_ms != -1 &&
       (other.first_packet_time_ms < first_packet_time_ms ||
        first_packet_time_ms == -1)) {
      // Use oldest time.
      first_packet_time_ms = other.first_packet_time_ms;
    }
  }

  void Subtract(const RtcpPacketTypeCounter& other) {
    nack_packets -= other.nack_packets;
    fir_packets -= other.fir_packets;
    pli_packets -= other.pli_packets;
    nack_requests -= other.nack_requests;
    unique_nack_requests -= other.unique_nack_requests;
    if (other.first_packet_time_ms != -1 &&
        (other.first_packet_time_ms > first_packet_time_ms ||
         first_packet_time_ms == -1)) {
      // Use youngest time.
      first_packet_time_ms = other.first_packet_time_ms;
    }
  }

  int64_t TimeSinceFirstPacketInMs(int64_t now_ms) const {
    return (first_packet_time_ms == -1) ? -1 : (now_ms - first_packet_time_ms);
  }

  int UniqueNackRequestsInPercent() const {
    if (nack_requests == 0) {
      return 0;
    }
    return static_cast<int>(
        (unique_nack_requests * 100.0f / nack_requests) + 0.5f);
  }

  int64_t first_packet_time_ms;  // Time when first packet is sent/received.
  uint32_t nack_packets;   // Number of RTCP NACK packets.
  uint32_t fir_packets;    // Number of RTCP FIR packets.
  uint32_t pli_packets;    // Number of RTCP PLI packets.
  uint32_t nack_requests;  // Number of NACKed RTP packets.
  uint32_t unique_nack_requests;  // Number of unique NACKed RTP packets.
};

class RtcpPacketTypeCounterObserver {
 public:
  virtual ~RtcpPacketTypeCounterObserver() {}
  virtual void RtcpPacketTypesCounterUpdated(
      uint32_t ssrc,
      const RtcpPacketTypeCounter& packet_counter) = 0;
};

// Rate statistics for a stream.
struct BitrateStatistics {
  BitrateStatistics() : bitrate_bps(0), packet_rate(0), timestamp_ms(0) {}

  uint32_t bitrate_bps;   // Bitrate in bits per second.
  uint32_t packet_rate;   // Packet rate in packets per second.
  uint64_t timestamp_ms;  // Ntp timestamp in ms at time of rate estimation.
};

// Callback, used to notify an observer whenever new rates have been estimated.
class BitrateStatisticsObserver {
 public:
  virtual ~BitrateStatisticsObserver() {}

  virtual void Notify(const BitrateStatistics& total_stats,
                      const BitrateStatistics& retransmit_stats,
                      uint32_t ssrc) = 0;
};

struct FrameCounts {
  FrameCounts() : key_frames(0), delta_frames(0) {}
  int key_frames;
  int delta_frames;
};

// Callback, used to notify an observer whenever frame counts have been updated.
class FrameCountObserver {
 public:
  virtual ~FrameCountObserver() {}
  virtual void FrameCountUpdated(const FrameCounts& frame_counts,
                                 uint32_t ssrc) = 0;
};

// Callback, used to notify an observer whenever the send-side delay is updated.
class SendSideDelayObserver {
 public:
  virtual ~SendSideDelayObserver() {}
  virtual void SendSideDelayUpdated(int avg_delay_ms,
                                    int max_delay_ms,
                                    uint32_t ssrc) = 0;
};

// Callback, used to notify an observer whenever a packet is sent to the
// transport.
// TODO(asapersson): This class will remove the need for SendSideDelayObserver.
// Remove SendSideDelayObserver once possible.
class SendPacketObserver {
 public:
  virtual ~SendPacketObserver() {}
  virtual void OnSendPacket(uint16_t packet_id,
                            int64_t capture_time_ms,
                            uint32_t ssrc) = 0;
};

// ==================================================================
// Voice specific types
// ==================================================================

// Each codec supported can be described by this structure.
struct CodecInst {
  int pltype;
  char plname[RTP_PAYLOAD_NAME_SIZE];
  int plfreq;
  int pacsize;
  size_t channels;
  int rate;  // bits/sec unlike {start,min,max}Bitrate elsewhere in this file!

  bool operator==(const CodecInst& other) const {
    return pltype == other.pltype &&
           (STR_CASE_CMP(plname, other.plname) == 0) &&
           plfreq == other.plfreq &&
           pacsize == other.pacsize &&
           channels == other.channels &&
           rate == other.rate;
  }

  bool operator!=(const CodecInst& other) const {
    return !(*this == other);
  }
};

// RTP
enum {kRtpCsrcSize = 15}; // RFC 3550 page 13

enum PayloadFrequencies
{
    kFreq8000Hz = 8000,
    kFreq16000Hz = 16000,
    kFreq32000Hz = 32000
};

enum VadModes                 // degree of bandwidth reduction
{
    kVadConventional = 0,      // lowest reduction
    kVadAggressiveLow,
    kVadAggressiveMid,
    kVadAggressiveHigh         // highest reduction
};

struct NetworkStatistics           // NETEQ statistics
{
    // current jitter buffer size in ms
    uint16_t currentBufferSize;
    // preferred (optimal) buffer size in ms
    uint16_t preferredBufferSize;
    // adding extra delay due to "peaky jitter"
    bool jitterPeaksFound;
    // Loss rate (network + late); fraction between 0 and 1, scaled to Q14.
    uint16_t currentPacketLossRate;
    // Late loss rate; fraction between 0 and 1, scaled to Q14.
    uint16_t currentDiscardRate;
    // fraction (of original stream) of synthesized audio inserted through
    // expansion (in Q14)
    uint16_t currentExpandRate;
    // fraction (of original stream) of synthesized speech inserted through
    // expansion (in Q14)
    uint16_t currentSpeechExpandRate;
    // fraction of synthesized speech inserted through pre-emptive expansion
    // (in Q14)
    uint16_t currentPreemptiveRate;
    // fraction of data removed through acceleration (in Q14)
    uint16_t currentAccelerateRate;
    // fraction of data coming from secondary decoding (in Q14)
    uint16_t currentSecondaryDecodedRate;
    // clock-drift in parts-per-million (negative or positive)
    int32_t clockDriftPPM;
    // average packet waiting time in the jitter buffer (ms)
    int meanWaitingTimeMs;
    // median packet waiting time in the jitter buffer (ms)
    int medianWaitingTimeMs;
    // min packet waiting time in the jitter buffer (ms)
    int minWaitingTimeMs;
    // max packet waiting time in the jitter buffer (ms)
    int maxWaitingTimeMs;
    // added samples in off mode due to packet loss
    size_t addedSamples;
};

// Statistics for calls to AudioCodingModule::PlayoutData10Ms().
struct AudioDecodingCallStats {
  AudioDecodingCallStats()
      : calls_to_silence_generator(0),
        calls_to_neteq(0),
        decoded_normal(0),
        decoded_plc(0),
        decoded_cng(0),
        decoded_plc_cng(0) {}

  int calls_to_silence_generator;  // Number of calls where silence generated,
                                   // and NetEq was disengaged from decoding.
  int calls_to_neteq;  // Number of calls to NetEq.
  int decoded_normal;  // Number of calls where audio RTP packet decoded.
  int decoded_plc;  // Number of calls resulted in PLC.
  int decoded_cng;  // Number of calls where comfort noise generated due to DTX.
  int decoded_plc_cng;  // Number of calls resulted where PLC faded to CNG.
};

typedef struct
{
    int min;              // minumum
    int max;              // maximum
    int average;          // average
} StatVal;

typedef struct           // All levels are reported in dBm0
{
    StatVal speech_rx;   // long-term speech levels on receiving side
    StatVal speech_tx;   // long-term speech levels on transmitting side
    StatVal noise_rx;    // long-term noise/silence levels on receiving side
    StatVal noise_tx;    // long-term noise/silence levels on transmitting side
} LevelStatistics;

typedef struct        // All levels are reported in dB
{
    StatVal erl;      // Echo Return Loss
    StatVal erle;     // Echo Return Loss Enhancement
    StatVal rerl;     // RERL = ERL + ERLE
    // Echo suppression inside EC at the point just before its NLP
    StatVal a_nlp;
} EchoStatistics;

enum NsModes    // type of Noise Suppression
{
    kNsUnchanged = 0,   // previously set mode
    kNsDefault,         // platform default
    kNsConference,      // conferencing default
    kNsLowSuppression,  // lowest suppression
    kNsModerateSuppression,
    kNsHighSuppression,
    kNsVeryHighSuppression,     // highest suppression
};

enum AgcModes                  // type of Automatic Gain Control
{
    kAgcUnchanged = 0,        // previously set mode
    kAgcDefault,              // platform default
    // adaptive mode for use when analog volume control exists (e.g. for
    // PC softphone)
    kAgcAdaptiveAnalog,
    // scaling takes place in the digital domain (e.g. for conference servers
    // and embedded devices)
    kAgcAdaptiveDigital,
    // can be used on embedded devices where the capture signal level
    // is predictable
    kAgcFixedDigital
};

// EC modes
enum EcModes                   // type of Echo Control
{
    kEcUnchanged = 0,          // previously set mode
    kEcDefault,                // platform default
    kEcConference,             // conferencing default (aggressive AEC)
    kEcAec,                    // Acoustic Echo Cancellation
    kEcAecm,                   // AEC mobile
};

// AECM modes
enum AecmModes                 // mode of AECM
{
    kAecmQuietEarpieceOrHeadset = 0,
                               // Quiet earpiece or headset use
    kAecmEarpiece,             // most earpiece use
    kAecmLoudEarpiece,         // Loud earpiece or quiet speakerphone use
    kAecmSpeakerphone,         // most speakerphone use (default)
    kAecmLoudSpeakerphone      // Loud speakerphone
};

// AGC configuration
typedef struct
{
    unsigned short targetLeveldBOv;
    unsigned short digitalCompressionGaindB;
    bool           limiterEnable;
} AgcConfig;                  // AGC configuration parameters

enum StereoChannel
{
    kStereoLeft = 0,
    kStereoRight,
    kStereoBoth
};

// Audio device layers
enum AudioLayers
{
    kAudioPlatformDefault = 0,
    kAudioWindowsWave = 1,
    kAudioWindowsCore = 2,
    kAudioLinuxAlsa = 3,
    kAudioLinuxPulse = 4
};

// TODO(henrika): to be removed.
enum NetEqModes             // NetEQ playout configurations
{
    // Optimized trade-off between low delay and jitter robustness for two-way
    // communication.
    kNetEqDefault = 0,
    // Improved jitter robustness at the cost of increased delay. Can be
    // used in one-way communication.
    kNetEqStreaming = 1,
    // Optimzed for decodability of fax signals rather than for perceived audio
    // quality.
    kNetEqFax = 2,
    // Minimal buffer management. Inserts zeros for lost packets and during
    // buffer increases.
    kNetEqOff = 3,
};

// TODO(henrika): to be removed.
enum OnHoldModes            // On Hold direction
{
    kHoldSendAndPlay = 0,    // Put both sending and playing in on-hold state.
    kHoldSendOnly,           // Put only sending in on-hold state.
    kHoldPlayOnly            // Put only playing in on-hold state.
};

// TODO(henrika): to be removed.
enum AmrMode
{
    kRfc3267BwEfficient = 0,
    kRfc3267OctetAligned = 1,
    kRfc3267FileStorage = 2,
};

// ==================================================================
// Video specific types
// ==================================================================

// Raw video types
enum RawVideoType
{
    kVideoI420     = 0,
    kVideoYV12     = 1,
    kVideoYUY2     = 2,
    kVideoUYVY     = 3,
    kVideoIYUV     = 4,
    kVideoARGB     = 5,
    kVideoRGB24    = 6,
    kVideoRGB565   = 7,
    kVideoARGB4444 = 8,
    kVideoARGB1555 = 9,
    kVideoMJPEG    = 10,
    kVideoNV12     = 11,
    kVideoNV21     = 12,
    kVideoBGRA     = 13,
    kVideoUnknown  = 99
};

// Video codec
enum { kConfigParameterSize = 128};
enum { kPayloadNameSize = 32};
enum { kMaxSimulcastStreams = 4};
enum { kMaxSpatialLayers = 5 };
enum { kMaxTemporalStreams = 4};

enum VideoCodecComplexity
{
    kComplexityNormal = 0,
    kComplexityHigh    = 1,
    kComplexityHigher  = 2,
    kComplexityMax     = 3
};

enum VideoCodecProfile
{
    kProfileBase = 0x00,
    kProfileMain = 0x01
};

enum VP8ResilienceMode {
  kResilienceOff,    // The stream produced by the encoder requires a
                     // recovery frame (typically a key frame) to be
                     // decodable after a packet loss.
  kResilientStream,  // A stream produced by the encoder is resilient to
                     // packet losses, but packets within a frame subsequent
                     // to a loss can't be decoded.
  kResilientFrames   // Same as kResilientStream but with added resilience
                     // within a frame.
};

class TemporalLayersFactory;
// VP8 specific
struct VideoCodecVP8 {
  bool                 pictureLossIndicationOn;
  bool                 feedbackModeOn;
  VideoCodecComplexity complexity;
  VP8ResilienceMode    resilience;
  unsigned char        numberOfTemporalLayers;
  bool                 denoisingOn;
  bool                 errorConcealmentOn;
  bool                 automaticResizeOn;
  bool                 frameDroppingOn;
  int                  keyFrameInterval;
  const TemporalLayersFactory* tl_factory;
};

// VP9 specific.
struct VideoCodecVP9 {
  VideoCodecComplexity complexity;
  int                  resilience;
  unsigned char        numberOfTemporalLayers;
  bool                 denoisingOn;
  bool                 frameDroppingOn;
  int                  keyFrameInterval;
  bool                 adaptiveQpMode;
  bool                 automaticResizeOn;
  unsigned char        numberOfSpatialLayers;
  bool                 flexibleMode;
};

// H264 specific.
struct VideoCodecH264 {
  VideoCodecProfile profile;
  bool           frameDroppingOn;
  int            keyFrameInterval;
  // These are NULL/0 if not externally negotiated.
  const uint8_t* spsData;
  size_t         spsLen;
  const uint8_t* ppsData;
  size_t         ppsLen;
};

// Video codec types
enum VideoCodecType {
  kVideoCodecVP8,
  kVideoCodecVP9,
  kVideoCodecH264,
  kVideoCodecI420,
  kVideoCodecRED,
  kVideoCodecULPFEC,
  kVideoCodecGeneric,
  kVideoCodecUnknown
};

union VideoCodecUnion {
  VideoCodecVP8       VP8;
  VideoCodecVP9       VP9;
  VideoCodecH264      H264;
};


// Simulcast is when the same stream is encoded multiple times with different
// settings such as resolution.
struct SimulcastStream {
  unsigned short      width;
  unsigned short      height;
  unsigned char       numberOfTemporalLayers;
  unsigned int        maxBitrate;  // kilobits/sec.
  unsigned int        targetBitrate;  // kilobits/sec.
  unsigned int        minBitrate;  // kilobits/sec.
  unsigned int        qpMax; // minimum quality
};

struct SpatialLayer {
  int scaling_factor_num;
  int scaling_factor_den;
  int target_bitrate_bps;
  // TODO(ivica): Add max_quantizer and min_quantizer?
};

enum VideoCodecMode {
  kRealtimeVideo,
  kScreensharing
};

// Common video codec properties
struct VideoCodec {
  VideoCodecType      codecType;
  char                plName[kPayloadNameSize];
  unsigned char       plType;

  unsigned short      width;
  unsigned short      height;

  unsigned int        startBitrate;  // kilobits/sec.
  unsigned int        maxBitrate;  // kilobits/sec.
  unsigned int        minBitrate;  // kilobits/sec.
  unsigned int        targetBitrate;  // kilobits/sec.

  unsigned char       maxFramerate;

  VideoCodecUnion     codecSpecific;

  unsigned int        qpMax;
  unsigned char       numberOfSimulcastStreams;
  SimulcastStream     simulcastStream[kMaxSimulcastStreams];
  SpatialLayer spatialLayers[kMaxSpatialLayers];

  VideoCodecMode      mode;
  bool                expect_encode_from_texture;

  bool operator==(const VideoCodec& other) const = delete;
  bool operator!=(const VideoCodec& other) const = delete;
};

// Bandwidth over-use detector options.  These are used to drive
// experimentation with bandwidth estimation parameters.
// See modules/remote_bitrate_estimator/overuse_detector.h
struct OverUseDetectorOptions {
  OverUseDetectorOptions()
      : initial_slope(8.0/512.0),
        initial_offset(0),
        initial_e(),
        initial_process_noise(),
        initial_avg_noise(0.0),
        initial_var_noise(50) {
    initial_e[0][0] = 100;
    initial_e[1][1] = 1e-1;
    initial_e[0][1] = initial_e[1][0] = 0;
    initial_process_noise[0] = 1e-13;
    initial_process_noise[1] = 1e-3;
  }
  double initial_slope;
  double initial_offset;
  double initial_e[2][2];
  double initial_process_noise[2];
  double initial_avg_noise;
  double initial_var_noise;
};

// This structure will have the information about when packet is actually
// received by socket.
struct PacketTime {
  PacketTime() : timestamp(-1), not_before(-1) {}
  PacketTime(int64_t timestamp, int64_t not_before)
      : timestamp(timestamp), not_before(not_before) {
  }

  int64_t timestamp;   // Receive time after socket delivers the data.
  int64_t not_before;  // Earliest possible time the data could have arrived,
                       // indicating the potential error in the |timestamp|
                       // value,in case the system is busy.
                       // For example, the time of the last select() call.
                       // If unknown, this value will be set to zero.
};

// Minimum and maximum playout delay values from capture to render.
// These are best effort values.
//
// A value < 0 indicates no change from previous valid value.
//
// min = max = 0 indicates that the receiver should try and render
// frame as soon as possible.
//
// min = x, max = y indicates that the receiver is free to adapt
// in the range (x, y) based on network jitter.
//
// Note: Given that this gets embedded in a union, it is up-to the owner to
// initialize these values.
struct PlayoutDelay {
  int min_ms;
  int max_ms;
};

struct RTPHeaderExtension {
  RTPHeaderExtension();

  bool hasTransmissionTimeOffset;
  int32_t transmissionTimeOffset;
  bool hasAbsoluteSendTime;
  uint32_t absoluteSendTime;
  bool hasTransportSequenceNumber;
  uint16_t transportSequenceNumber;

  // Audio Level includes both level in dBov and voiced/unvoiced bit. See:
  // https://datatracker.ietf.org/doc/draft-lennox-avt-rtp-audio-level-exthdr/
  bool hasAudioLevel;
  bool voiceActivity;
  uint8_t audioLevel;

  // For Coordination of Video Orientation. See
  // http://www.etsi.org/deliver/etsi_ts/126100_126199/126114/12.07.00_60/
  // ts_126114v120700p.pdf
  bool hasVideoRotation;
  uint8_t videoRotation;

  PlayoutDelay playout_delay = {-1, -1};
};

struct RTPHeader {
  RTPHeader();

  bool markerBit;
  uint8_t payloadType;
  uint16_t sequenceNumber;
  uint32_t timestamp;
  uint32_t ssrc;
  uint8_t numCSRCs;
  uint32_t arrOfCSRCs[kRtpCsrcSize];
  size_t paddingLength;
  size_t headerLength;
  int payload_type_frequency;
  RTPHeaderExtension extension;
};

struct RtpPacketCounter {
  RtpPacketCounter()
    : header_bytes(0),
      payload_bytes(0),
      padding_bytes(0),
      packets(0) {}

  void Add(const RtpPacketCounter& other) {
    header_bytes += other.header_bytes;
    payload_bytes += other.payload_bytes;
    padding_bytes += other.padding_bytes;
    packets += other.packets;
  }

  void Subtract(const RtpPacketCounter& other) {
    assert(header_bytes >= other.header_bytes);
    header_bytes -= other.header_bytes;
    assert(payload_bytes >= other.payload_bytes);
    payload_bytes -= other.payload_bytes;
    assert(padding_bytes >= other.padding_bytes);
    padding_bytes -= other.padding_bytes;
    assert(packets >= other.packets);
    packets -= other.packets;
  }

  void AddPacket(size_t packet_length, const RTPHeader& header) {
    ++packets;
    header_bytes += header.headerLength;
    padding_bytes += header.paddingLength;
    payload_bytes +=
        packet_length - (header.headerLength + header.paddingLength);
  }

  size_t TotalBytes() const {
    return header_bytes + payload_bytes + padding_bytes;
  }

  size_t header_bytes;   // Number of bytes used by RTP headers.
  size_t payload_bytes;  // Payload bytes, excluding RTP headers and padding.
  size_t padding_bytes;  // Number of padding bytes.
  uint32_t packets;      // Number of packets.
};

// Data usage statistics for a (rtp) stream.
struct StreamDataCounters {
  StreamDataCounters();

  void Add(const StreamDataCounters& other) {
    transmitted.Add(other.transmitted);
    retransmitted.Add(other.retransmitted);
    fec.Add(other.fec);
    if (other.first_packet_time_ms != -1 &&
       (other.first_packet_time_ms < first_packet_time_ms ||
        first_packet_time_ms == -1)) {
      // Use oldest time.
      first_packet_time_ms = other.first_packet_time_ms;
    }
  }

  void Subtract(const StreamDataCounters& other) {
    transmitted.Subtract(other.transmitted);
    retransmitted.Subtract(other.retransmitted);
    fec.Subtract(other.fec);
    if (other.first_packet_time_ms != -1 &&
        (other.first_packet_time_ms > first_packet_time_ms ||
         first_packet_time_ms == -1)) {
      // Use youngest time.
      first_packet_time_ms = other.first_packet_time_ms;
    }
  }

  int64_t TimeSinceFirstPacketInMs(int64_t now_ms) const {
    return (first_packet_time_ms == -1) ? -1 : (now_ms - first_packet_time_ms);
  }

  // Returns the number of bytes corresponding to the actual media payload (i.e.
  // RTP headers, padding, retransmissions and fec packets are excluded).
  // Note this function does not have meaning for an RTX stream.
  size_t MediaPayloadBytes() const {
    return transmitted.payload_bytes - retransmitted.payload_bytes -
           fec.payload_bytes;
  }

  int64_t first_packet_time_ms;  // Time when first packet is sent/received.
  RtpPacketCounter transmitted;  // Number of transmitted packets/bytes.
  RtpPacketCounter retransmitted;  // Number of retransmitted packets/bytes.
  RtpPacketCounter fec;  // Number of redundancy packets/bytes.
};

// Callback, called whenever byte/packet counts have been updated.
class StreamDataCountersCallback {
 public:
  virtual ~StreamDataCountersCallback() {}

  virtual void DataCountersUpdated(const StreamDataCounters& counters,
                                   uint32_t ssrc) = 0;
};

// RTCP mode to use. Compound mode is described by RFC 4585 and reduced-size
// RTCP mode is described by RFC 5506.
enum class RtcpMode { kOff, kCompound, kReducedSize };

enum NetworkState {
  kNetworkUp,
  kNetworkDown,
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_TYPES_H_
