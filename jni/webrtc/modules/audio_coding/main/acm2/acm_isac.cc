/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/modules/audio_coding/main/acm2/acm_isac.h"

#include <assert.h>

#include "webrtc/modules/audio_coding/main/interface/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/trace.h"

#ifdef WEBRTC_CODEC_ISAC
#include "webrtc/modules/audio_coding/codecs/isac/main/interface/isac.h"
#endif

#ifdef WEBRTC_CODEC_ISACFX
#include "webrtc/modules/audio_coding/codecs/isac/fix/interface/isacfix.h"
#endif

#if defined (WEBRTC_CODEC_ISAC) || defined (WEBRTC_CODEC_ISACFX)
#include "webrtc/modules/audio_coding/main/acm2/acm_isac_macros.h"
#endif

namespace webrtc {

namespace acm2 {

// we need this otherwise we cannot use forward declaration
// in the header file
#if (defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX))
struct ACMISACInst {
  ACM_ISAC_STRUCT* inst;
};
#endif

#define ISAC_MIN_RATE 10000
#define ISAC_MAX_RATE 56000

// Tables for bandwidth estimates
#define NR_ISAC_BANDWIDTHS 24
static const int32_t kIsacRatesWb[NR_ISAC_BANDWIDTHS] = {
    10000, 11100, 12300, 13700, 15200, 16900, 18800, 20900, 23300, 25900, 28700,
    31900, 10100, 11200, 12400, 13800, 15300, 17000, 18900, 21000, 23400, 26000,
    28800, 32000};

static const int32_t kIsacRatesSwb[NR_ISAC_BANDWIDTHS] = {
    10000, 11000, 12400, 13800, 15300, 17000, 18900, 21000, 23200, 25400, 27600,
    29800, 32000, 34100, 36300, 38500, 40700, 42900, 45100, 47300, 49500, 51700,
    53900, 56000 };

#if (!defined(WEBRTC_CODEC_ISAC) && !defined(WEBRTC_CODEC_ISACFX))

ACMISAC::ACMISAC(int16_t /* codec_id */)
    : codec_inst_crit_sect_(CriticalSectionWrapper::CreateCriticalSection()),
      codec_inst_ptr_(NULL),
      is_enc_initialized_(false),
      isac_coding_mode_(CHANNEL_INDEPENDENT),
      enforce_frame_size_(false),
      isac_currentBN_(32000),
      samples_in10MsAudio_(160),  // Initiates to 16 kHz mode.
      decoder_initialized_(false) {
}

ACMISAC::~ACMISAC() {
  return;
}

ACMGenericCodec* ACMISAC::CreateInstance(void) { return NULL; }

int16_t ACMISAC::InternalEncode(uint8_t* /* bitstream */,
                                int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMISAC::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

int16_t ACMISAC::InternalInitDecoder(WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

int16_t ACMISAC::InternalCreateEncoder() { return -1; }

void ACMISAC::DestructEncoderSafe() { return; }

void ACMISAC::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

int16_t ACMISAC::Transcode(uint8_t* /* bitstream */,
                           int16_t* /* bitstream_len_byte */,
                           int16_t /* q_bwe */,
                           int32_t /* scale */,
                           bool /* is_red */) {
  return -1;
}

int16_t ACMISAC::SetBitRateSafe(int32_t /* bit_rate */) { return -1; }

int32_t ACMISAC::GetEstimatedBandwidthSafe() { return -1; }

int32_t ACMISAC::SetEstimatedBandwidthSafe(int32_t /* estimated_bandwidth */) {
  return -1;
}

int32_t ACMISAC::GetRedPayloadSafe(uint8_t* /* red_payload */,
                                   int16_t* /* payload_bytes */) {
  return -1;
}

int16_t ACMISAC::UpdateDecoderSampFreq(int16_t /* codec_id */) { return -1; }

int16_t ACMISAC::UpdateEncoderSampFreq(uint16_t /* encoder_samp_freq_hz */) {
  return -1;
}

int16_t ACMISAC::EncoderSampFreq(uint16_t* /* samp_freq_hz */) { return -1; }

int32_t ACMISAC::ConfigISACBandwidthEstimator(
    const uint8_t /* init_frame_size_msec */,
    const uint16_t /* init_rate_bit_per_sec */,
    const bool /* enforce_frame_size  */) {
  return -1;
}

int32_t ACMISAC::SetISACMaxPayloadSize(
    const uint16_t /* max_payload_len_bytes */) {
  return -1;
}

int32_t ACMISAC::SetISACMaxRate(const uint32_t /* max_rate_bit_per_sec */) {
  return -1;
}

void ACMISAC::UpdateFrameLen() { return; }

void ACMISAC::CurrentRate(int32_t* /*rate_bit_per_sec */) { return; }

bool ACMISAC::DecoderParamsSafe(WebRtcACMCodecParams* /* dec_params */,
                                const uint8_t /* payload_type */) {
  return false;
}

int16_t ACMISAC::REDPayloadISAC(const int32_t /* isac_rate */,
                                const int16_t /* isac_bw_estimate */,
                                uint8_t* /* payload */,
                                int16_t* /* payload_len_bytes */) {
  return -1;
}

AudioDecoder* ACMISAC::Decoder(int /* codec_id */) { return NULL; }

#else     //===================== Actual Implementation =======================

#ifdef WEBRTC_CODEC_ISACFX

// How the scaling is computed. iSAC computes a gain based on the
// bottleneck. It follows the following expression for that
//
// G(BN_kbps) = pow(10, (a + b * BN_kbps + c * BN_kbps * BN_kbps) / 20.0)
//              / 3.4641;
//
// Where for 30 ms framelength we have,
//
// a = -23; b = 0.48; c = 0;
//
// As the default encoder is operating at 32kbps we have the scale as
//
// S(BN_kbps) = G(BN_kbps) / G(32);

#define ISAC_NUM_SUPPORTED_RATES 9

static const uint16_t kIsacSuportedRates[ISAC_NUM_SUPPORTED_RATES] = {
    32000, 30000, 26000, 23000, 21000, 19000, 17000, 15000, 12000};

static const float kIsacScale[ISAC_NUM_SUPPORTED_RATES] = {
    1.0f,    0.8954f,  0.7178f, 0.6081f, 0.5445f,
    0.4875f, 0.4365f,  0.3908f, 0.3311f
};

enum IsacSamplingRate {
  kIsacWideband = 16,
  kIsacSuperWideband = 32
};

static float ACMISACFixTranscodingScale(uint16_t rate) {
  // find the scale for transcoding, the scale is rounded
  // downward
  float scale = -1;
  for (int16_t n = 0; n < ISAC_NUM_SUPPORTED_RATES; n++) {
    if (rate >= kIsacSuportedRates[n]) {
      scale = kIsacScale[n];
      break;
    }
  }
  return scale;
}

static void ACMISACFixGetSendBitrate(ACM_ISAC_STRUCT* inst,
                                     int32_t* bottleneck) {
  *bottleneck = WebRtcIsacfix_GetUplinkBw(inst);
}

static int16_t ACMISACFixGetNewBitstream(ACM_ISAC_STRUCT* inst,
                                         int16_t bwe_index,
                                         int16_t /* jitter_index */,
                                         int32_t rate,
                                         int16_t* bitstream,
                                         bool is_red) {
  if (is_red) {
    // RED not supported with iSACFIX
    return -1;
  }
  float scale = ACMISACFixTranscodingScale((uint16_t)rate);
  return WebRtcIsacfix_GetNewBitStream(inst, bwe_index, scale, bitstream);
}

static int16_t ACMISACFixGetSendBWE(ACM_ISAC_STRUCT* inst,
                                    int16_t* rate_index,
                                    int16_t* /* dummy */) {
  int16_t local_rate_index;
  int16_t status = WebRtcIsacfix_GetDownLinkBwIndex(inst, &local_rate_index);
  if (status < 0) {
    return -1;
  } else {
    *rate_index = local_rate_index;
    return 0;
  }
}

static int16_t ACMISACFixControlBWE(ACM_ISAC_STRUCT* inst,
                                    int32_t rate_bps,
                                    int16_t frame_size_ms,
                                    int16_t enforce_frame_size) {
  return WebRtcIsacfix_ControlBwe(
      inst, (int16_t)rate_bps, frame_size_ms, enforce_frame_size);
}

static int16_t ACMISACFixControl(ACM_ISAC_STRUCT* inst,
                                 int32_t rate_bps,
                                 int16_t frame_size_ms) {
  return WebRtcIsacfix_Control(inst, (int16_t)rate_bps, frame_size_ms);
}

// The following two function should have the same signature as their counter
// part in iSAC floating-point, i.e. WebRtcIsac_EncSampRate &
// WebRtcIsac_DecSampRate.
static uint16_t ACMISACFixGetEncSampRate(ACM_ISAC_STRUCT* /* inst */) {
  return 16000;
}

static uint16_t ACMISACFixGetDecSampRate(ACM_ISAC_STRUCT* /* inst */) {
  return 16000;
}

#endif

ACMISAC::ACMISAC(int16_t codec_id)
    : AudioDecoder(ACMCodecDB::neteq_decoders_[codec_id]),
      codec_inst_crit_sect_(CriticalSectionWrapper::CreateCriticalSection()),
      is_enc_initialized_(false),
      isac_coding_mode_(CHANNEL_INDEPENDENT),
      enforce_frame_size_(false),
      isac_current_bn_(32000),
      samples_in_10ms_audio_(160),  // Initiates to 16 kHz mode.
      decoder_initialized_(false) {
  codec_id_ = codec_id;

  // Create codec instance.
  codec_inst_ptr_ = new ACMISACInst;
  if (codec_inst_ptr_ == NULL) {
    return;
  }
  codec_inst_ptr_->inst = NULL;
  state_ = codec_inst_ptr_;
}

ACMISAC::~ACMISAC() {
  if (codec_inst_ptr_ != NULL) {
    if (codec_inst_ptr_->inst != NULL) {
      ACM_ISAC_FREE(codec_inst_ptr_->inst);
      codec_inst_ptr_->inst = NULL;
    }
    delete codec_inst_ptr_;
    codec_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMISAC::InternalInitDecoder(WebRtcACMCodecParams* codec_params) {
  // set decoder sampling frequency.
  if (codec_params->codec_inst.plfreq == 32000 ||
      codec_params->codec_inst.plfreq == 48000) {
    UpdateDecoderSampFreq(ACMCodecDB::kISACSWB);
  } else {
    UpdateDecoderSampFreq(ACMCodecDB::kISAC);
  }

  // in a one-way communication we may never register send-codec.
  // However we like that the BWE to work properly so it has to
  // be initialized. The BWE is initialized when iSAC encoder is initialized.
  // Therefore, we need this.
  if (!encoder_initialized_) {
    // Since we don't require a valid rate or a valid packet size when
    // initializing the decoder, we set valid values before initializing encoder
    codec_params->codec_inst.rate = kIsacWbDefaultRate;
    codec_params->codec_inst.pacsize = kIsacPacSize960;
    if (InternalInitEncoder(codec_params) < 0) {
      return -1;
    }
    encoder_initialized_ = true;
  }

  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_DECODERINIT(codec_inst_ptr_->inst);
}

ACMGenericCodec* ACMISAC::CreateInstance(void) { return NULL; }

int16_t ACMISAC::InternalEncode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte) {
  // ISAC takes 10ms audio every time we call encoder, therefore,
  // it should be treated like codecs with 'basic coding block'
  // non-zero, and the following 'while-loop' should not be necessary.
  // However, due to a mistake in the codec the frame-size might change
  // at the first 10ms pushed in to iSAC if the bit-rate is low, this is
  // sort of a bug in iSAC. to address this we treat iSAC as the
  // following.
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (codec_inst_ptr_ == NULL) {
    return -1;
  }
  *bitstream_len_byte = 0;
  while ((*bitstream_len_byte == 0) && (in_audio_ix_read_ < frame_len_smpl_)) {
    if (in_audio_ix_read_ > in_audio_ix_write_) {
      // something is wrong.
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "The actual frame-size of iSAC appears to be larger that "
                   "expected. All audio pushed in but no bit-stream is "
                   "generated.");
      return -1;
    }
    *bitstream_len_byte = ACM_ISAC_ENCODE(
        codec_inst_ptr_->inst, &in_audio_[in_audio_ix_read_],
        reinterpret_cast<int16_t*>(bitstream));
    // increment the read index this tell the caller that how far
    // we have gone forward in reading the audio buffer
    in_audio_ix_read_ += samples_in_10ms_audio_;
  }
  if (*bitstream_len_byte == 0) {
    WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, unique_id_,
                 "ISAC Has encoded the whole frame but no bit-stream is "
                 "generated.");
  }

  // a packet is generated iSAC, is set in adaptive mode may change
  // the frame length and we like to update the bottleneck value as
  // well, although updating bottleneck is not crucial
  if ((*bitstream_len_byte > 0) && (isac_coding_mode_ == ADAPTIVE)) {
    ACM_ISAC_GETSENDBITRATE(codec_inst_ptr_->inst, &isac_current_bn_);
  }
  UpdateFrameLen();
  return *bitstream_len_byte;
}

int16_t ACMISAC::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  // if rate is set to -1 then iSAC has to be in adaptive mode
  if (codec_params->codec_inst.rate == -1) {
    isac_coding_mode_ = ADAPTIVE;
  } else if ((codec_params->codec_inst.rate >= ISAC_MIN_RATE) &&
             (codec_params->codec_inst.rate <= ISAC_MAX_RATE)) {
    // sanity check that rate is in acceptable range
    isac_coding_mode_ = CHANNEL_INDEPENDENT;
    isac_current_bn_ = codec_params->codec_inst.rate;
  } else {
    return -1;
  }

  // we need to set the encoder sampling frequency.
  if (UpdateEncoderSampFreq((uint16_t)codec_params->codec_inst.plfreq) < 0) {
    return -1;
  }
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (ACM_ISAC_ENCODERINIT(codec_inst_ptr_->inst, isac_coding_mode_) < 0) {
    return -1;
  }

  // apply the frame-size and rate if operating in
  // channel-independent mode
  if (isac_coding_mode_ == CHANNEL_INDEPENDENT) {
    if (ACM_ISAC_CONTROL(codec_inst_ptr_->inst,
                         codec_params->codec_inst.rate,
                         codec_params->codec_inst.pacsize /
                         (codec_params->codec_inst.plfreq / 1000)) < 0) {
      return -1;
    }
  } else {
    // We need this for adaptive case and has to be called
    // after initialization
    ACM_ISAC_GETSENDBITRATE(codec_inst_ptr_->inst, &isac_current_bn_);
  }
  frame_len_smpl_ = ACM_ISAC_GETNEWFRAMELEN(codec_inst_ptr_->inst);
  return 0;
}

int16_t ACMISAC::InternalCreateEncoder() {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (codec_inst_ptr_ == NULL) {
    return -1;
  }
  decoder_initialized_ = false;
  int16_t status = ACM_ISAC_CREATE(&(codec_inst_ptr_->inst));

  if (status < 0)
    codec_inst_ptr_->inst = NULL;
  return status;
}

int16_t ACMISAC::Transcode(uint8_t* bitstream,
                           int16_t* bitstream_len_byte,
                           int16_t q_bwe,
                           int32_t rate,
                           bool is_red) {
  int16_t jitter_info = 0;
  // transcode from a higher rate to lower rate sanity check
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (codec_inst_ptr_ == NULL) {
    return -1;
  }

  *bitstream_len_byte = ACM_ISAC_GETNEWBITSTREAM(
      codec_inst_ptr_->inst, q_bwe, jitter_info, rate,
      reinterpret_cast<int16_t*>(bitstream), (is_red) ? 1 : 0);

  if (*bitstream_len_byte < 0) {
    // error happened
    *bitstream_len_byte = 0;
    return -1;
  } else {
    return *bitstream_len_byte;
  }
}

void ACMISAC::UpdateFrameLen() {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  frame_len_smpl_ = ACM_ISAC_GETNEWFRAMELEN(codec_inst_ptr_->inst);
  encoder_params_.codec_inst.pacsize = frame_len_smpl_;
}

void ACMISAC::DestructEncoderSafe() {
  // codec with shared instance cannot delete.
  encoder_initialized_ = false;
  return;
}

void ACMISAC::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    ACM_ISAC_FREE(static_cast<ACM_ISAC_STRUCT *>(ptr_inst));
  }
  return;
}

int16_t ACMISAC::SetBitRateSafe(int32_t bit_rate) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (codec_inst_ptr_ == NULL) {
    return -1;
  }
  uint16_t encoder_samp_freq;
  EncoderSampFreq(&encoder_samp_freq);
  bool reinit = false;
  // change the BN of iSAC
  if (bit_rate == -1) {
    // ADAPTIVE MODE
    // Check if it was already in adaptive mode
    if (isac_coding_mode_ != ADAPTIVE) {
      // was not in adaptive, then set the mode to adaptive
      // and flag for re-initialization
      isac_coding_mode_ = ADAPTIVE;
      reinit = true;
    }
  } else if ((bit_rate >= ISAC_MIN_RATE) && (bit_rate <= ISAC_MAX_RATE)) {
    // Sanity check if the rate valid
    // check if it was in channel-independent mode before
    if (isac_coding_mode_ != CHANNEL_INDEPENDENT) {
      // was not in channel independent, set the mode to
      // channel-independent and flag for re-initialization
      isac_coding_mode_ = CHANNEL_INDEPENDENT;
      reinit = true;
    }
    // store the bottleneck
    isac_current_bn_ = (uint16_t)bit_rate;
  } else {
    // invlaid rate
    return -1;
  }

  int16_t status = 0;
  if (reinit) {
    // initialize and check if it is successful
    if (ACM_ISAC_ENCODERINIT(codec_inst_ptr_->inst, isac_coding_mode_) < 0) {
      // failed initialization
      return -1;
    }
  }
  if (isac_coding_mode_ == CHANNEL_INDEPENDENT) {
    status = ACM_ISAC_CONTROL(
        codec_inst_ptr_->inst, isac_current_bn_,
        (encoder_samp_freq == 32000 || encoder_samp_freq == 48000) ? 30 :
            (frame_len_smpl_ / 16));
    if (status < 0) {
      status = -1;
    }
  }

  // Update encoder parameters
  encoder_params_.codec_inst.rate = bit_rate;

  UpdateFrameLen();
  return status;
}

int32_t ACMISAC::GetEstimatedBandwidthSafe() {
  int16_t bandwidth_index = 0;
  int16_t delay_index = 0;
  int samp_rate;

  // Get bandwidth information
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  ACM_ISAC_GETSENDBWE(codec_inst_ptr_->inst, &bandwidth_index, &delay_index);

  // Validy check of index
  if ((bandwidth_index < 0) || (bandwidth_index >= NR_ISAC_BANDWIDTHS)) {
    return -1;
  }

  // Check sample frequency
  samp_rate = ACM_ISAC_GETDECSAMPRATE(codec_inst_ptr_->inst);
  if (samp_rate == 16000) {
    return kIsacRatesWb[bandwidth_index];
  } else {
    return kIsacRatesSwb[bandwidth_index];
  }
}

int32_t ACMISAC::SetEstimatedBandwidthSafe(int32_t estimated_bandwidth) {
  int samp_rate;
  int16_t bandwidth_index;

  // Check sample frequency and choose appropriate table
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  samp_rate = ACM_ISAC_GETENCSAMPRATE(codec_inst_ptr_->inst);

  if (samp_rate == 16000) {
    // Search through the WB rate table to find the index
    bandwidth_index = NR_ISAC_BANDWIDTHS / 2 - 1;
    for (int i = 0; i < (NR_ISAC_BANDWIDTHS / 2); i++) {
      if (estimated_bandwidth == kIsacRatesWb[i]) {
        bandwidth_index = i;
        break;
      } else if (estimated_bandwidth
          == kIsacRatesWb[i + NR_ISAC_BANDWIDTHS / 2]) {
        bandwidth_index = i + NR_ISAC_BANDWIDTHS / 2;
        break;
      } else if (estimated_bandwidth < kIsacRatesWb[i]) {
        bandwidth_index = i;
        break;
      }
    }
  } else {
    // Search through the SWB rate table to find the index
    bandwidth_index = NR_ISAC_BANDWIDTHS - 1;
    for (int i = 0; i < NR_ISAC_BANDWIDTHS; i++) {
      if (estimated_bandwidth <= kIsacRatesSwb[i]) {
        bandwidth_index = i;
        break;
      }
    }
  }

  // Set iSAC Bandwidth Estimate
  ACM_ISAC_SETBWE(codec_inst_ptr_->inst, bandwidth_index);

  return 0;
}

int32_t ACMISAC::GetRedPayloadSafe(
#if (!defined(WEBRTC_CODEC_ISAC))
    uint8_t* /* red_payload */,
    int16_t* /* payload_bytes */) {
  return -1;
#else
    uint8_t* red_payload, int16_t* payload_bytes) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  int16_t bytes =
      WebRtcIsac_GetRedPayload(
          codec_inst_ptr_->inst, reinterpret_cast<int16_t*>(red_payload));
  if (bytes < 0) {
    return -1;
  }
  *payload_bytes = bytes;
  return 0;
#endif
}

int16_t ACMISAC::UpdateDecoderSampFreq(
#ifdef WEBRTC_CODEC_ISAC
    int16_t codec_id) {
    // The decoder supports only wideband and super-wideband.
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  if (ACMCodecDB::kISAC == codec_id) {
    return WebRtcIsac_SetDecSampRate(codec_inst_ptr_->inst, 16000);
  } else if (ACMCodecDB::kISACSWB == codec_id ||
             ACMCodecDB::kISACFB == codec_id) {
    return WebRtcIsac_SetDecSampRate(codec_inst_ptr_->inst, 32000);
  } else {
    return -1;
  }
#else
    int16_t /* codec_id */) {
  return 0;
#endif
}

int16_t ACMISAC::UpdateEncoderSampFreq(
#ifdef WEBRTC_CODEC_ISAC
    uint16_t encoder_samp_freq_hz) {
  uint16_t current_samp_rate_hz;
  EncoderSampFreq(&current_samp_rate_hz);

  if (current_samp_rate_hz != encoder_samp_freq_hz) {
    if ((encoder_samp_freq_hz != 16000) && (encoder_samp_freq_hz != 32000) &&
        (encoder_samp_freq_hz != 48000)) {
      return -1;
    } else {
      in_audio_ix_read_ = 0;
      in_audio_ix_write_ = 0;
      in_timestamp_ix_write_ = 0;
      CriticalSectionScoped lock(codec_inst_crit_sect_.get());
      if (WebRtcIsac_SetEncSampRate(codec_inst_ptr_->inst,
                                    encoder_samp_freq_hz) < 0) {
        return -1;
      }
      samples_in_10ms_audio_ = encoder_samp_freq_hz / 100;
      frame_len_smpl_ = ACM_ISAC_GETNEWFRAMELEN(codec_inst_ptr_->inst);
      encoder_params_.codec_inst.pacsize = frame_len_smpl_;
      encoder_params_.codec_inst.plfreq = encoder_samp_freq_hz;
      return 0;
    }
  }
#else
    uint16_t /* codec_id */) {
#endif
  return 0;
}

int16_t ACMISAC::EncoderSampFreq(uint16_t* samp_freq_hz) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  *samp_freq_hz = ACM_ISAC_GETENCSAMPRATE(codec_inst_ptr_->inst);
  return 0;
}

int32_t ACMISAC::ConfigISACBandwidthEstimator(
    const uint8_t init_frame_size_msec,
    const uint16_t init_rate_bit_per_sec,
    const bool enforce_frame_size) {
  int16_t status;
  {
    uint16_t samp_freq_hz;
    EncoderSampFreq(&samp_freq_hz);
    CriticalSectionScoped lock(codec_inst_crit_sect_.get());
    // TODO(turajs): at 32kHz we hardcode calling with 30ms and enforce
    // the frame-size otherwise we might get error. Revise if
    // control-bwe is changed.
    if (samp_freq_hz == 32000 || samp_freq_hz == 48000) {
      status = ACM_ISAC_CONTROL_BWE(codec_inst_ptr_->inst,
                                    init_rate_bit_per_sec, 30, 1);
    } else {
      status = ACM_ISAC_CONTROL_BWE(codec_inst_ptr_->inst,
                                    init_rate_bit_per_sec,
                                    init_frame_size_msec,
                                    enforce_frame_size ? 1 : 0);
    }
  }
  if (status < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "Couldn't config iSAC BWE.");
    return -1;
  }
  {
    WriteLockScoped wl(codec_wrapper_lock_);
    UpdateFrameLen();
  }
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  ACM_ISAC_GETSENDBITRATE(codec_inst_ptr_->inst, &isac_current_bn_);
  return 0;
}

int32_t ACMISAC::SetISACMaxPayloadSize(const uint16_t max_payload_len_bytes) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_SETMAXPAYLOADSIZE(codec_inst_ptr_->inst,
                                    max_payload_len_bytes);
}

int32_t ACMISAC::SetISACMaxRate(const uint32_t max_rate_bit_per_sec) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_SETMAXRATE(codec_inst_ptr_->inst, max_rate_bit_per_sec);
}

void ACMISAC::CurrentRate(int32_t* rate_bit_per_sec) {
  if (isac_coding_mode_ == ADAPTIVE) {
    CriticalSectionScoped lock(codec_inst_crit_sect_.get());
    ACM_ISAC_GETSENDBITRATE(codec_inst_ptr_->inst, rate_bit_per_sec);
  }
}

int16_t ACMISAC::REDPayloadISAC(const int32_t isac_rate,
                                const int16_t isac_bw_estimate,
                                uint8_t* payload,
                                int16_t* payload_len_bytes) {
  int16_t status;
  ReadLockScoped rl(codec_wrapper_lock_);
  status =
      Transcode(payload, payload_len_bytes, isac_bw_estimate, isac_rate, true);
  return status;
}

int ACMISAC::Decode(const uint8_t* encoded,
                    size_t encoded_len,
                    int16_t* decoded,
                    SpeechType* speech_type) {
  int16_t temp_type;
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  int ret =
      ACM_ISAC_DECODE_B(static_cast<ACM_ISAC_STRUCT*>(codec_inst_ptr_->inst),
                        reinterpret_cast<const uint16_t*>(encoded),
                        static_cast<int16_t>(encoded_len),
                        decoded,
                        &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

int ACMISAC::DecodePlc(int num_frames, int16_t* decoded) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_DECODEPLC(
      static_cast<ACM_ISAC_STRUCT*>(codec_inst_ptr_->inst),
      decoded,
      static_cast<int16_t>(num_frames));
}

int ACMISAC::IncomingPacket(const uint8_t* payload,
                            size_t payload_len,
                            uint16_t rtp_sequence_number,
                            uint32_t rtp_timestamp,
                            uint32_t arrival_timestamp) {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_DECODE_BWE(
      static_cast<ACM_ISAC_STRUCT*>(codec_inst_ptr_->inst),
      reinterpret_cast<const uint16_t*>(payload),
      static_cast<uint32_t>(payload_len),
      rtp_sequence_number,
      rtp_timestamp,
      arrival_timestamp);
}

int ACMISAC::DecodeRedundant(const uint8_t* encoded,
                             size_t encoded_len,
                             int16_t* decoded,
                             SpeechType* speech_type) {
  int16_t temp_type = 1;  // Default is speech.
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  int16_t ret =
      ACM_ISAC_DECODERCU(static_cast<ACM_ISAC_STRUCT*>(codec_inst_ptr_->inst),
                         reinterpret_cast<const uint16_t*>(encoded),
                         static_cast<int16_t>(encoded_len),
                         decoded,
                         &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

int ACMISAC::ErrorCode() {
  CriticalSectionScoped lock(codec_inst_crit_sect_.get());
  return ACM_ISAC_GETERRORCODE(
      static_cast<ACM_ISAC_STRUCT*>(codec_inst_ptr_->inst));
}

AudioDecoder* ACMISAC::Decoder(int codec_id) {
  // Create iSAC instance if it does not exist.
  WriteLockScoped wl(codec_wrapper_lock_);
  if (!encoder_exist_) {
    CriticalSectionScoped lock(codec_inst_crit_sect_.get());
    assert(codec_inst_ptr_->inst == NULL);
    encoder_initialized_ = false;
    decoder_initialized_ = false;
    if (ACM_ISAC_CREATE(&(codec_inst_ptr_->inst)) < 0) {
      codec_inst_ptr_->inst = NULL;
      return NULL;
    }
    encoder_exist_ = true;
  }

  WebRtcACMCodecParams codec_params;
  if (!encoder_initialized_ || !decoder_initialized_) {
    ACMCodecDB::Codec(codec_id, &codec_params.codec_inst);
    // The following three values are not used but we set them to valid values.
    codec_params.enable_dtx = false;
    codec_params.enable_vad = false;
    codec_params.vad_mode = VADNormal;
  }

  if (!encoder_initialized_) {
    // Initialize encoder to make sure bandwidth estimator works.
    if (InternalInitEncoder(&codec_params) < 0)
      return NULL;
    encoder_initialized_ = true;
  }

  if (!decoder_initialized_) {
    if (InternalInitDecoder(&codec_params) < 0)
      return NULL;
    decoder_initialized_ = true;
  }

  return this;
}

#endif

}  // namespace acm2

}  // namespace webrtc
