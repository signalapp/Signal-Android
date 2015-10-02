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

#define RTP_PAYLOAD_NAME_SIZE 32

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

class InStream
{
public:
    virtual int Read(void *buf,int len) = 0;
    virtual int Rewind() {return -1;}
    virtual ~InStream() {}
protected:
    InStream() {}
};

class OutStream
{
public:
    virtual bool Write(const void *buf,int len) = 0;
    virtual int Rewind() {return -1;}
    virtual ~OutStream() {}
protected:
    OutStream() {}
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
    kFileFormatAviFile        = 3,
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

enum FrameType
{
    kFrameEmpty            = 0,
    kAudioFrameSpeech      = 1,
    kAudioFrameCN          = 2,
    kVideoFrameKey         = 3,    // independent frame
    kVideoFrameDelta       = 4,    // depends on the previus frame
};

// External transport callback interface
class Transport
{
public:
    virtual int SendPacket(int channel, const void *data, int len) = 0;
    virtual int SendRTCPPacket(int channel, const void *data, int len) = 0;

protected:
    virtual ~Transport() {}
    Transport() {}
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

// Callback, called whenever a new rtcp report block is transmitted.
class RtcpStatisticsCallback {
 public:
  virtual ~RtcpStatisticsCallback() {}

  virtual void StatisticsUpdated(const RtcpStatistics& statistics,
                                 uint32_t ssrc) = 0;
};

// Statistics for RTCP packet types.
struct RtcpPacketTypeCounter {
  RtcpPacketTypeCounter()
    : nack_packets(0),
      fir_packets(0),
      pli_packets(0) {}

  void Add(const RtcpPacketTypeCounter& other) {
    nack_packets += other.nack_packets;
    fir_packets += other.fir_packets;
    pli_packets += other.pli_packets;
  }

  uint32_t nack_packets;
  uint32_t fir_packets;
  uint32_t pli_packets;
};

// Data usage statistics for a (rtp) stream
struct StreamDataCounters {
  StreamDataCounters()
   : bytes(0),
     header_bytes(0),
     padding_bytes(0),
     packets(0),
     retransmitted_packets(0),
     fec_packets(0) {}

  // TODO(pbos): Rename bytes -> media_bytes.
  uint32_t bytes;  // Payload bytes, excluding RTP headers and padding.
  uint32_t header_bytes;  // Number of bytes used by RTP headers.
  uint32_t padding_bytes;  // Number of padding bytes.
  uint32_t packets;  // Number of packets.
  uint32_t retransmitted_packets;  // Number of retransmitted packets.
  uint32_t fec_packets;  // Number of redundancy packets.
};

// Callback, called whenever byte/packet counts have been updated.
class StreamDataCountersCallback {
 public:
  virtual ~StreamDataCountersCallback() {}

  virtual void DataCountersUpdated(const StreamDataCounters& counters,
                                   uint32_t ssrc) = 0;
};

// Rate statistics for a stream
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

  virtual void Notify(const BitrateStatistics& stats, uint32_t ssrc) = 0;
};

// Callback, used to notify an observer whenever frame counts have been updated
class FrameCountObserver {
 public:
  virtual ~FrameCountObserver() {}
  virtual void FrameCountUpdated(FrameType frame_type,
                                 uint32_t frame_count,
                                 const unsigned int ssrc) = 0;
};

// Callback, used to notify an observer whenever the send-side delay is updated.
class SendSideDelayObserver {
 public:
  virtual ~SendSideDelayObserver() {}
  virtual void SendSideDelayUpdated(int avg_delay_ms,
                                    int max_delay_ms,
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
  int channels;
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

enum RTPDirections
{
    kRtpIncoming = 0,
    kRtpOutgoing
};

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
    // loss rate (network + late) in percent (in Q14)
    uint16_t currentPacketLossRate;
    // late loss rate in percent (in Q14)
    uint16_t currentDiscardRate;
    // fraction (of original stream) of synthesized speech inserted through
    // expansion (in Q14)
    uint16_t currentExpandRate;
    // fraction of synthesized speech inserted through pre-emptive expansion
    // (in Q14)
    uint16_t currentPreemptiveRate;
    // fraction of data removed through acceleration (in Q14)
    uint16_t currentAccelerateRate;
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
    int addedSamples;
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

  bool operator==(const VideoCodecVP8& other) const {
    return pictureLossIndicationOn == other.pictureLossIndicationOn &&
           feedbackModeOn == other.feedbackModeOn &&
           complexity == other.complexity &&
           resilience == other.resilience &&
           numberOfTemporalLayers == other.numberOfTemporalLayers &&
           denoisingOn == other.denoisingOn &&
           errorConcealmentOn == other.errorConcealmentOn &&
           automaticResizeOn == other.automaticResizeOn &&
           frameDroppingOn == other.frameDroppingOn &&
           keyFrameInterval == other.keyFrameInterval;
  }

  bool operator!=(const VideoCodecVP8& other) const {
    return !(*this == other);
  }
};

// H264 specific.
struct VideoCodecH264
{
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
enum VideoCodecType
{
    kVideoCodecVP8,
    kVideoCodecH264,
    kVideoCodecI420,
    kVideoCodecRED,
    kVideoCodecULPFEC,
    kVideoCodecGeneric,
    kVideoCodecUnknown
};

union VideoCodecUnion
{
    VideoCodecVP8       VP8;
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

  bool operator==(const SimulcastStream& other) const {
    return width == other.width &&
           height == other.height &&
           numberOfTemporalLayers == other.numberOfTemporalLayers &&
           maxBitrate == other.maxBitrate &&
           targetBitrate == other.targetBitrate &&
           minBitrate == other.minBitrate &&
           qpMax == other.qpMax;
  }

  bool operator!=(const SimulcastStream& other) const {
    return !(*this == other);
  }
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

  VideoCodecMode      mode;

  // When using an external encoder/decoder this allows to pass
  // extra options without requiring webrtc to be aware of them.
  Config*  extra_options;

  bool operator==(const VideoCodec& other) const {
    bool ret = codecType == other.codecType &&
               (STR_CASE_CMP(plName, other.plName) == 0) &&
               plType == other.plType &&
               width == other.width &&
               height == other.height &&
               startBitrate == other.startBitrate &&
               maxBitrate == other.maxBitrate &&
               minBitrate == other.minBitrate &&
               targetBitrate == other.targetBitrate &&
               maxFramerate == other.maxFramerate &&
               qpMax == other.qpMax &&
               numberOfSimulcastStreams == other.numberOfSimulcastStreams &&
               mode == other.mode;
    if (ret && codecType == kVideoCodecVP8) {
      ret &= (codecSpecific.VP8 == other.codecSpecific.VP8);
    }

    for (unsigned char i = 0; i < other.numberOfSimulcastStreams && ret; ++i) {
      ret &= (simulcastStream[i] == other.simulcastStream[i]);
    }
    return ret;
  }

  bool operator!=(const VideoCodec& other) const {
    return !(*this == other);
  }
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
        initial_var_noise(50),
        initial_threshold(25.0) {
    initial_e[0][0] = 100;
    initial_e[1][1] = 1e-1;
    initial_e[0][1] = initial_e[1][0] = 0;
    initial_process_noise[0] = 1e-10;
    initial_process_noise[1] = 1e-2;
  }
  double initial_slope;
  double initial_offset;
  double initial_e[2][2];
  double initial_process_noise[2];
  double initial_avg_noise;
  double initial_var_noise;
  double initial_threshold;
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

struct RTPHeaderExtension {
  RTPHeaderExtension()
      : hasTransmissionTimeOffset(false),
        transmissionTimeOffset(0),
        hasAbsoluteSendTime(false),
        absoluteSendTime(0),
        hasAudioLevel(false),
        audioLevel(0) {}

  bool hasTransmissionTimeOffset;
  int32_t transmissionTimeOffset;
  bool hasAbsoluteSendTime;
  uint32_t absoluteSendTime;

  // Audio Level includes both level in dBov and voiced/unvoiced bit. See:
  // https://datatracker.ietf.org/doc/draft-lennox-avt-rtp-audio-level-exthdr/
  bool hasAudioLevel;
  uint8_t audioLevel;
};

struct RTPHeader {
  RTPHeader()
      : markerBit(false),
        payloadType(0),
        sequenceNumber(0),
        timestamp(0),
        ssrc(0),
        numCSRCs(0),
        paddingLength(0),
        headerLength(0),
        payload_type_frequency(0),
        extension() {
    memset(&arrOfCSRCs, 0, sizeof(arrOfCSRCs));
  }

  bool markerBit;
  uint8_t payloadType;
  uint16_t sequenceNumber;
  uint32_t timestamp;
  uint32_t ssrc;
  uint8_t numCSRCs;
  uint32_t arrOfCSRCs[kRtpCsrcSize];
  uint8_t paddingLength;
  uint16_t headerLength;
  int payload_type_frequency;
  RTPHeaderExtension extension;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_TYPES_H_
