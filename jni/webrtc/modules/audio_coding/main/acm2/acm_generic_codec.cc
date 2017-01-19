/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"

#include <assert.h>
#include <string.h>

#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/modules/audio_coding/codecs/cng/include/webrtc_cng.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

namespace acm2 {

// Enum for CNG
enum {
  kMaxPLCParamsCNG = WEBRTC_CNG_MAX_LPC_ORDER,
  kNewCNGNumLPCParams = 8
};

// Interval for sending new CNG parameters (SID frames) is 100 msec.
enum {
  kCngSidIntervalMsec = 100
};

// We set some of the variables to invalid values as a check point
// if a proper initialization has happened. Another approach is
// to initialize to a default codec that we are sure is always included.
ACMGenericCodec::ACMGenericCodec()
    : in_audio_ix_write_(0),
      in_audio_ix_read_(0),
      in_timestamp_ix_write_(0),
      in_audio_(NULL),
      in_timestamp_(NULL),
      frame_len_smpl_(-1),  // invalid value
      num_channels_(1),
      codec_id_(-1),  // invalid value
      num_missed_samples_(0),
      encoder_exist_(false),
      encoder_initialized_(false),
      registered_in_neteq_(false),
      has_internal_dtx_(false),
      ptr_vad_inst_(NULL),
      vad_enabled_(false),
      vad_mode_(VADNormal),
      dtx_enabled_(false),
      ptr_dtx_inst_(NULL),
      num_lpc_params_(kNewCNGNumLPCParams),
      sent_cn_previous_(false),
      prev_frame_cng_(0),
      has_internal_fec_(false),
      codec_wrapper_lock_(*RWLockWrapper::CreateRWLock()),
      last_timestamp_(0xD87F3F9F),
      unique_id_(0) {
  // Initialize VAD vector.
  for (int i = 0; i < MAX_FRAME_SIZE_10MSEC; i++) {
    vad_label_[i] = 0;
  }
  // Nullify memory for encoder and decoder, and set payload type to an
  // invalid value.
  memset(&encoder_params_, 0, sizeof(WebRtcACMCodecParams));
  encoder_params_.codec_inst.pltype = -1;
}

ACMGenericCodec::~ACMGenericCodec() {
  // Check all the members which are pointers, and if they are not NULL
  // delete/free them.
  if (ptr_vad_inst_ != NULL) {
    WebRtcVad_Free(ptr_vad_inst_);
    ptr_vad_inst_ = NULL;
  }
  if (in_audio_ != NULL) {
    delete[] in_audio_;
    in_audio_ = NULL;
  }
  if (in_timestamp_ != NULL) {
    delete[] in_timestamp_;
    in_timestamp_ = NULL;
  }
  if (ptr_dtx_inst_ != NULL) {
    WebRtcCng_FreeEnc(ptr_dtx_inst_);
    ptr_dtx_inst_ = NULL;
  }
  delete &codec_wrapper_lock_;
}

int32_t ACMGenericCodec::Add10MsData(const uint32_t timestamp,
                                     const int16_t* data,
                                     const uint16_t length_smpl,
                                     const uint8_t audio_channel) {
  WriteLockScoped wl(codec_wrapper_lock_);
  return Add10MsDataSafe(timestamp, data, length_smpl, audio_channel);
}

int32_t ACMGenericCodec::Add10MsDataSafe(const uint32_t timestamp,
                                         const int16_t* data,
                                         const uint16_t length_smpl,
                                         const uint8_t audio_channel) {
  // The codec expects to get data in correct sampling rate. Get the sampling
  // frequency of the codec.
  uint16_t plfreq_hz;
  if (EncoderSampFreq(&plfreq_hz) < 0) {
    return -1;
  }

  // Sanity check to make sure the length of the input corresponds to 10 ms.
  if ((plfreq_hz / 100) != length_smpl) {
    // This is not 10 ms of audio, given the sampling frequency of the codec.
    return -1;
  }

  if (last_timestamp_ == timestamp) {
    // Same timestamp as the last time, overwrite.
    if ((in_audio_ix_write_ >= length_smpl * audio_channel) &&
        (in_timestamp_ix_write_ > 0)) {
      in_audio_ix_write_ -= length_smpl * audio_channel;
      assert(in_timestamp_ix_write_ >= 0);

      in_timestamp_ix_write_--;
      assert(in_audio_ix_write_ >= 0);
      WEBRTC_TRACE(webrtc::kTraceDebug, webrtc::kTraceAudioCoding, unique_id_,
                   "Adding 10ms with previous timestamp, overwriting the "
                   "previous 10ms");
    } else {
      WEBRTC_TRACE(webrtc::kTraceDebug, webrtc::kTraceAudioCoding, unique_id_,
                   "Adding 10ms with previous timestamp, this will sound bad");
    }
  }

  last_timestamp_ = timestamp;

  // If the data exceeds the buffer size, we throw away the oldest data and
  // add the newly received 10 msec at the end.
  if ((in_audio_ix_write_ + length_smpl * audio_channel) >
      AUDIO_BUFFER_SIZE_W16) {
    // Get the number of samples to be overwritten.
    int16_t missed_samples = in_audio_ix_write_ + length_smpl * audio_channel -
        AUDIO_BUFFER_SIZE_W16;

    // Move the data (overwrite the old data).
    memmove(in_audio_, in_audio_ + missed_samples,
            (AUDIO_BUFFER_SIZE_W16 - length_smpl * audio_channel) *
            sizeof(int16_t));

    // Copy the new data.
    memcpy(in_audio_ + (AUDIO_BUFFER_SIZE_W16 - length_smpl * audio_channel),
           data, length_smpl * audio_channel * sizeof(int16_t));

    // Get the number of 10 ms blocks which are overwritten.
    int16_t missed_10ms_blocks =static_cast<int16_t>(
        (missed_samples / audio_channel * 100) / plfreq_hz);

    // Move the timestamps.
    memmove(in_timestamp_, in_timestamp_ + missed_10ms_blocks,
            (in_timestamp_ix_write_ - missed_10ms_blocks) * sizeof(uint32_t));
    in_timestamp_ix_write_ -= missed_10ms_blocks;
    assert(in_timestamp_ix_write_ >= 0);

    in_timestamp_[in_timestamp_ix_write_] = timestamp;
    in_timestamp_ix_write_++;
    assert(in_timestamp_ix_write_ < TIMESTAMP_BUFFER_SIZE_W32);

    // Buffer is full.
    in_audio_ix_write_ = AUDIO_BUFFER_SIZE_W16;
    IncreaseNoMissedSamples(missed_samples);
    return -missed_samples;
  }

  // Store the input data in our data buffer.
  memcpy(in_audio_ + in_audio_ix_write_, data,
         length_smpl * audio_channel * sizeof(int16_t));
  in_audio_ix_write_ += length_smpl * audio_channel;
  assert(in_timestamp_ix_write_ < TIMESTAMP_BUFFER_SIZE_W32);

  in_timestamp_[in_timestamp_ix_write_] = timestamp;
  in_timestamp_ix_write_++;
  assert(in_timestamp_ix_write_ < TIMESTAMP_BUFFER_SIZE_W32);
  return 0;
}

bool ACMGenericCodec::HasFrameToEncode() const {
  ReadLockScoped lockCodec(codec_wrapper_lock_);
  if (in_audio_ix_write_ < frame_len_smpl_ * num_channels_)
    return false;
  return true;
}

int16_t ACMGenericCodec::Encode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte,
                                uint32_t* timestamp,
                                WebRtcACMEncodingType* encoding_type) {
  if (!HasFrameToEncode()) {
    // There is not enough audio
    *timestamp = 0;
    *bitstream_len_byte = 0;
    // Doesn't really matter what this parameter set to
    *encoding_type = kNoEncoding;
    return 0;
  }
  WriteLockScoped lockCodec(codec_wrapper_lock_);

  // Not all codecs accept the whole frame to be pushed into encoder at once.
  // Some codecs needs to be feed with a specific number of samples different
  // from the frame size. If this is the case, |myBasicCodingBlockSmpl| will
  // report a number different from 0, and we will loop over calls to encoder
  // further down, until we have encode a complete frame.
  const int16_t my_basic_coding_block_smpl =
      ACMCodecDB::BasicCodingBlock(codec_id_);
  if (my_basic_coding_block_smpl < 0 || !encoder_initialized_ ||
      !encoder_exist_) {
    // This should not happen, but in case it does, report no encoding done.
    *timestamp = 0;
    *bitstream_len_byte = 0;
    *encoding_type = kNoEncoding;
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "EncodeSafe: error, basic coding sample block is negative");
    return -1;
  }
  // This makes the internal encoder read from the beginning of the buffer.
  in_audio_ix_read_ = 0;
  *timestamp = in_timestamp_[0];

  // Process the audio through VAD. The function will set |_vad_labels|.
  // If VAD is disabled all entries in |_vad_labels| are set to ONE (active).
  int16_t status = 0;
  int16_t dtx_processed_samples = 0;
  status = ProcessFrameVADDTX(bitstream, bitstream_len_byte,
                              &dtx_processed_samples);
  if (status < 0) {
    *timestamp = 0;
    *bitstream_len_byte = 0;
    *encoding_type = kNoEncoding;
  } else {
    if (dtx_processed_samples > 0) {
      // Dtx have processed some samples, and even if a bit-stream is generated
      // we should not do any encoding (normally there won't be enough data).

      // Setting the following makes sure that the move of audio data and
      // timestamps done correctly.
      in_audio_ix_read_ = dtx_processed_samples;
      // This will let the owner of ACMGenericCodec to know that the
      // generated bit-stream is DTX to use correct payload type.
      uint16_t samp_freq_hz;
      EncoderSampFreq(&samp_freq_hz);
      if (samp_freq_hz == 8000) {
        *encoding_type = kPassiveDTXNB;
      } else if (samp_freq_hz == 16000) {
        *encoding_type = kPassiveDTXWB;
      } else if (samp_freq_hz == 32000) {
        *encoding_type = kPassiveDTXSWB;
      } else if (samp_freq_hz == 48000) {
        *encoding_type = kPassiveDTXFB;
      } else {
        status = -1;
        WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                     "EncodeSafe: Wrong sampling frequency for DTX.");
      }

      // Transport empty frame if we have an empty bitstream.
      if ((*bitstream_len_byte == 0) &&
          (sent_cn_previous_ ||
          ((in_audio_ix_write_ - in_audio_ix_read_) <= 0))) {
        // Makes sure we transmit an empty frame.
        *bitstream_len_byte = 1;
        *encoding_type = kNoEncoding;
      }
      sent_cn_previous_ = true;
    } else {
      // We should encode the audio frame. Either VAD and/or DTX is off, or the
      // audio was considered "active".

      sent_cn_previous_ = false;
      if (my_basic_coding_block_smpl == 0) {
        // This codec can handle all allowed frame sizes as basic coding block.
        status = InternalEncode(bitstream, bitstream_len_byte);
        if (status < 0) {
          // TODO(tlegrand): Maybe reseting the encoder to be fresh for the next
          // frame.
          WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding,
                       unique_id_, "EncodeSafe: error in internal_encode");
          *bitstream_len_byte = 0;
          *encoding_type = kNoEncoding;
        }
      } else {
        // A basic-coding-block for this codec is defined so we loop over the
        // audio with the steps of the basic-coding-block.
        int16_t tmp_bitstream_len_byte;

        // Reset the variables which will be incremented in the loop.
        *bitstream_len_byte = 0;
        bool done = false;
        while (!done) {
          status = InternalEncode(&bitstream[*bitstream_len_byte],
                                  &tmp_bitstream_len_byte);
          *bitstream_len_byte += tmp_bitstream_len_byte;

          // Guard Against errors and too large payloads.
          if ((status < 0) || (*bitstream_len_byte > MAX_PAYLOAD_SIZE_BYTE)) {
            // Error has happened, and even if we are in the middle of a full
            // frame we have to exit. Before exiting, whatever bits are in the
            // buffer are probably corrupted, so we ignore them.
            *bitstream_len_byte = 0;
            *encoding_type = kNoEncoding;
            // We might have come here because of the second condition.
            status = -1;
            WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding,
                         unique_id_, "EncodeSafe: error in InternalEncode");
            // break from the loop
            break;
          }
          done = in_audio_ix_read_ >= frame_len_smpl_ * num_channels_;
        }
      }
      if (status >= 0) {
        *encoding_type = (vad_label_[0] == 1) ? kActiveNormalEncoded :
            kPassiveNormalEncoded;
        // Transport empty frame if we have an empty bitstream.
        if ((*bitstream_len_byte == 0) &&
            ((in_audio_ix_write_ - in_audio_ix_read_) <= 0)) {
          // Makes sure we transmit an empty frame.
          *bitstream_len_byte = 1;
          *encoding_type = kNoEncoding;
        }
      }
    }
  }

  // Move the timestamp buffer according to the number of 10 ms blocks
  // which are read.
  uint16_t samp_freq_hz;
  EncoderSampFreq(&samp_freq_hz);
  int16_t num_10ms_blocks = static_cast<int16_t>(
      (in_audio_ix_read_ / num_channels_ * 100) / samp_freq_hz);
  if (in_timestamp_ix_write_ > num_10ms_blocks) {
    memmove(in_timestamp_, in_timestamp_ + num_10ms_blocks,
            (in_timestamp_ix_write_ - num_10ms_blocks) * sizeof(int32_t));
  }
  in_timestamp_ix_write_ -= num_10ms_blocks;
  assert(in_timestamp_ix_write_ >= 0);

  // Remove encoded audio and move next audio to be encoded to the beginning
  // of the buffer. Accordingly, adjust the read and write indices.
  if (in_audio_ix_read_ < in_audio_ix_write_) {
    memmove(in_audio_, &in_audio_[in_audio_ix_read_],
            (in_audio_ix_write_ - in_audio_ix_read_) * sizeof(int16_t));
  }
  in_audio_ix_write_ -= in_audio_ix_read_;
  in_audio_ix_read_ = 0;
  return (status < 0) ? (-1) : (*bitstream_len_byte);
}

bool ACMGenericCodec::EncoderInitialized() {
  ReadLockScoped rl(codec_wrapper_lock_);
  return encoder_initialized_;
}

int16_t ACMGenericCodec::EncoderParams(WebRtcACMCodecParams* enc_params) {
  ReadLockScoped rl(codec_wrapper_lock_);
  return EncoderParamsSafe(enc_params);
}

int16_t ACMGenericCodec::EncoderParamsSafe(WebRtcACMCodecParams* enc_params) {
  // Codec parameters are valid only if the encoder is initialized.
  if (encoder_initialized_) {
    int32_t current_rate;
    memcpy(enc_params, &encoder_params_, sizeof(WebRtcACMCodecParams));
    current_rate = enc_params->codec_inst.rate;
    CurrentRate(&current_rate);
    enc_params->codec_inst.rate = current_rate;
    return 0;
  } else {
    enc_params->codec_inst.plname[0] = '\0';
    enc_params->codec_inst.pltype = -1;
    enc_params->codec_inst.pacsize = 0;
    enc_params->codec_inst.rate = 0;
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "EncoderParamsSafe: error, encoder not initialized");
    return -1;
  }
}

int16_t ACMGenericCodec::ResetEncoder() {
  WriteLockScoped lockCodec(codec_wrapper_lock_);
  return ResetEncoderSafe();
}

int16_t ACMGenericCodec::ResetEncoderSafe() {
  if (!encoder_exist_ || !encoder_initialized_) {
    // We don't reset if encoder doesn't exists or isn't initialized yet.
    return 0;
  }

  in_audio_ix_write_ = 0;
  in_audio_ix_read_ = 0;
  in_timestamp_ix_write_ = 0;
  num_missed_samples_ = 0;
  memset(in_audio_, 0, AUDIO_BUFFER_SIZE_W16 * sizeof(int16_t));
  memset(in_timestamp_, 0, TIMESTAMP_BUFFER_SIZE_W32 * sizeof(int32_t));

  // Store DTX/VAD parameters.
  bool enable_vad = vad_enabled_;
  bool enable_dtx = dtx_enabled_;
  ACMVADMode mode = vad_mode_;

  // Reset the encoder.
  if (InternalResetEncoder() < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "ResetEncoderSafe: error in reset encoder");
    return -1;
  }

  // Disable DTX & VAD to delete the states and have a fresh start.
  DisableDTX();
  DisableVAD();

  // Set DTX/VAD.
  int status = SetVADSafe(&enable_dtx, &enable_vad, &mode);
  dtx_enabled_ = enable_dtx;
  vad_enabled_ = enable_vad;
  vad_mode_ = mode;
  return status;
}

int16_t ACMGenericCodec::InternalResetEncoder() {
  // Call the codecs internal encoder initialization/reset function.
  return InternalInitEncoder(&encoder_params_);
}

int16_t ACMGenericCodec::InitEncoder(WebRtcACMCodecParams* codec_params,
                                     bool force_initialization) {
  WriteLockScoped lockCodec(codec_wrapper_lock_);
  return InitEncoderSafe(codec_params, force_initialization);
}

int16_t ACMGenericCodec::InitEncoderSafe(WebRtcACMCodecParams* codec_params,
                                         bool force_initialization) {
  // Check if we got a valid set of parameters.
  int mirrorID;
  int codec_number = ACMCodecDB::CodecNumber(codec_params->codec_inst,
                                             &mirrorID);
  assert(codec_number >= 0);

  // Check if the parameters are for this codec.
  if ((codec_id_ >= 0) && (codec_id_ != codec_number) &&
      (codec_id_ != mirrorID)) {
    // The current codec is not the same as the one given by codec_params.
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "InitEncoderSafe: current codec is not the same as the one "
                 "given by codec_params");
    return -1;
  }

  if (encoder_initialized_ && !force_initialization) {
    // The encoder is already initialized, and we don't want to force
    // initialization.
    return 0;
  }
  int16_t status;
  if (!encoder_exist_) {
    // New encoder, start with creating.
    encoder_initialized_ = false;
    status = CreateEncoder();
    if (status < 0) {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "InitEncoderSafe: cannot create encoder");
      return -1;
    } else {
      encoder_exist_ = true;
    }
  }
  frame_len_smpl_ = codec_params->codec_inst.pacsize;
  num_channels_ = codec_params->codec_inst.channels;
  status = InternalInitEncoder(codec_params);
  if (status < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "InitEncoderSafe: error in init encoder");
    encoder_initialized_ = false;
    return -1;
  } else {
    // TODO(turajs): Move these allocations to the constructor issue 2445.
    // Store encoder parameters.
    memcpy(&encoder_params_, codec_params, sizeof(WebRtcACMCodecParams));
    encoder_initialized_ = true;
    if (in_audio_ == NULL) {
      in_audio_ = new int16_t[AUDIO_BUFFER_SIZE_W16];
    }
    if (in_timestamp_ == NULL) {
      in_timestamp_ = new uint32_t[TIMESTAMP_BUFFER_SIZE_W32];
    }
  }

  // Fresh start of audio buffer.
  memset(in_audio_, 0, sizeof(*in_audio_) * AUDIO_BUFFER_SIZE_W16);
  memset(in_timestamp_, 0, sizeof(*in_timestamp_) * TIMESTAMP_BUFFER_SIZE_W32);
  in_audio_ix_write_ = 0;
  in_audio_ix_read_ = 0;
  in_timestamp_ix_write_ = 0;

  return SetVADSafe(&codec_params->enable_dtx, &codec_params->enable_vad,
                    &codec_params->vad_mode);
}

void ACMGenericCodec::ResetNoMissedSamples() {
  WriteLockScoped cs(codec_wrapper_lock_);
  num_missed_samples_ = 0;
}

void ACMGenericCodec::IncreaseNoMissedSamples(const int16_t num_samples) {
  num_missed_samples_ += num_samples;
}

// Get the number of missed samples, this can be public.
uint32_t ACMGenericCodec::NoMissedSamples() const {
  ReadLockScoped cs(codec_wrapper_lock_);
  return num_missed_samples_;
}

void ACMGenericCodec::DestructEncoder() {
  WriteLockScoped wl(codec_wrapper_lock_);

  // Disable VAD and delete the instance.
  if (ptr_vad_inst_ != NULL) {
    WebRtcVad_Free(ptr_vad_inst_);
    ptr_vad_inst_ = NULL;
  }
  vad_enabled_ = false;
  vad_mode_ = VADNormal;

  // Disable DTX and delete the instance.
  dtx_enabled_ = false;
  if (ptr_dtx_inst_ != NULL) {
    WebRtcCng_FreeEnc(ptr_dtx_inst_);
    ptr_dtx_inst_ = NULL;
  }
  num_lpc_params_ = kNewCNGNumLPCParams;

  DestructEncoderSafe();
}

int16_t ACMGenericCodec::SetBitRate(const int32_t bitrate_bps) {
  WriteLockScoped wl(codec_wrapper_lock_);
  return SetBitRateSafe(bitrate_bps);
}

int16_t ACMGenericCodec::SetBitRateSafe(const int32_t bitrate_bps) {
  // If the codec can change the bit-rate this function is overloaded.
  // Otherwise the only acceptable value is the one that is in the database.
  CodecInst codec_params;
  if (ACMCodecDB::Codec(codec_id_, &codec_params) < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "SetBitRateSafe: error in ACMCodecDB::Codec");
    return -1;
  }
  if (codec_params.rate != bitrate_bps) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "SetBitRateSafe: rate value is not acceptable");
    return -1;
  } else {
    return 0;
  }
}

// iSAC specific functions:
int32_t ACMGenericCodec::GetEstimatedBandwidth() {
  WriteLockScoped wl(codec_wrapper_lock_);
  return GetEstimatedBandwidthSafe();
}

int32_t ACMGenericCodec::GetEstimatedBandwidthSafe() {
  // All codecs but iSAC will return -1.
  return -1;
}

int32_t ACMGenericCodec::SetEstimatedBandwidth(int32_t estimated_bandwidth) {
  WriteLockScoped wl(codec_wrapper_lock_);
  return SetEstimatedBandwidthSafe(estimated_bandwidth);
}

int32_t ACMGenericCodec::SetEstimatedBandwidthSafe(
    int32_t /*estimated_bandwidth*/) {
  // All codecs but iSAC will return -1.
  return -1;
}
// End of iSAC specific functions.

int32_t ACMGenericCodec::GetRedPayload(uint8_t* red_payload,
                                       int16_t* payload_bytes) {
  WriteLockScoped wl(codec_wrapper_lock_);
  return GetRedPayloadSafe(red_payload, payload_bytes);
}

int32_t ACMGenericCodec::GetRedPayloadSafe(uint8_t* /* red_payload */,
                                           int16_t* /* payload_bytes */) {
  return -1;  // Do nothing by default.
}

int16_t ACMGenericCodec::CreateEncoder() {
  int16_t status = 0;
  if (!encoder_exist_) {
    status = InternalCreateEncoder();
    // We just created the codec and obviously it is not initialized.
    encoder_initialized_ = false;
  }
  if (status < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "CreateEncoder: error in internal create encoder");
    encoder_exist_ = false;
  } else {
    encoder_exist_ = true;
  }
  return status;
}

void ACMGenericCodec::DestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WriteLockScoped lockCodec(codec_wrapper_lock_);
    InternalDestructEncoderInst(ptr_inst);
  }
}

uint32_t ACMGenericCodec::EarliestTimestamp() const {
  ReadLockScoped cs(codec_wrapper_lock_);
  return in_timestamp_[0];
}

int16_t ACMGenericCodec::SetVAD(bool* enable_dtx,
                                bool* enable_vad,
                                ACMVADMode* mode) {
  WriteLockScoped cs(codec_wrapper_lock_);
  return SetVADSafe(enable_dtx, enable_vad, mode);
}

int16_t ACMGenericCodec::SetVADSafe(bool* enable_dtx,
                                    bool* enable_vad,
                                    ACMVADMode* mode) {
  if (!STR_CASE_CMP(encoder_params_.codec_inst.plname, "OPUS") ||
      encoder_params_.codec_inst.channels == 2 ) {
    // VAD/DTX is not supported for Opus (even if sending mono), or other
    // stereo codecs.
    DisableDTX();
    DisableVAD();
    *enable_dtx = false;
    *enable_vad = false;
    return 0;
  }

  if (*enable_dtx) {
    // Make G729 AnnexB a special case.
    if (!STR_CASE_CMP(encoder_params_.codec_inst.plname, "G729")
        && !has_internal_dtx_) {
      if (ACMGenericCodec::EnableDTX() < 0) {
        WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                     "SetVADSafe: error in enable DTX");
        *enable_dtx = false;
        *enable_vad = vad_enabled_;
        return -1;
      }
    } else {
      if (EnableDTX() < 0) {
        WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                     "SetVADSafe: error in enable DTX");
        *enable_dtx = false;
        *enable_vad = vad_enabled_;
        return -1;
      }
    }

    // If codec does not have internal DTX (normal case) enabling DTX requires
    // an active VAD. '*enable_dtx == true' overwrites VAD status.
    // If codec has internal DTX, practically we don't need WebRtc VAD, however,
    // we let the user to turn it on if they need call-backs on silence.
    if (!has_internal_dtx_) {
      // DTX is enabled, and VAD will be activated.
      *enable_vad = true;
    }
  } else {
    // Make G729 AnnexB a special case.
    if (!STR_CASE_CMP(encoder_params_.codec_inst.plname, "G729")
        && !has_internal_dtx_) {
      ACMGenericCodec::DisableDTX();
      *enable_dtx = false;
    } else {
      DisableDTX();
      *enable_dtx = false;
    }
  }

  int16_t status = (*enable_vad) ? EnableVAD(*mode) : DisableVAD();
  if (status < 0) {
    // Failed to set VAD, disable DTX.
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
    "SetVADSafe: error in enable VAD");
    DisableDTX();
    *enable_dtx = false;
    *enable_vad = false;
  }
  return status;
}

int16_t ACMGenericCodec::EnableDTX() {
  if (has_internal_dtx_) {
    // We should not be here if we have internal DTX this function should be
    // overloaded by the derived class in this case.
    return -1;
  }
  if (!dtx_enabled_) {
    if (WebRtcCng_CreateEnc(&ptr_dtx_inst_) < 0) {
      ptr_dtx_inst_ = NULL;
      return -1;
    }
    uint16_t freq_hz;
    EncoderSampFreq(&freq_hz);
    if (WebRtcCng_InitEnc(ptr_dtx_inst_, freq_hz, kCngSidIntervalMsec,
                          num_lpc_params_) < 0) {
      // Couldn't initialize, has to return -1, and free the memory.
      WebRtcCng_FreeEnc(ptr_dtx_inst_);
      ptr_dtx_inst_ = NULL;
      return -1;
    }
    dtx_enabled_ = true;
  }
  return 0;
}

int16_t ACMGenericCodec::DisableDTX() {
  if (has_internal_dtx_) {
    // We should not be here if we have internal DTX this function should be
    // overloaded by the derived class in this case.
    return -1;
  }
  if (ptr_dtx_inst_ != NULL) {
    WebRtcCng_FreeEnc(ptr_dtx_inst_);
    ptr_dtx_inst_ = NULL;
  }
  dtx_enabled_ = false;
  return 0;
}

int16_t ACMGenericCodec::EnableVAD(ACMVADMode mode) {
  if ((mode < VADNormal) || (mode > VADVeryAggr)) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "EnableVAD: error in VAD mode range");
    return -1;
  }

  if (!vad_enabled_) {
    if (WebRtcVad_Create(&ptr_vad_inst_) < 0) {
      ptr_vad_inst_ = NULL;
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "EnableVAD: error in create VAD");
      return -1;
    }
    if (WebRtcVad_Init(ptr_vad_inst_) < 0) {
      WebRtcVad_Free(ptr_vad_inst_);
      ptr_vad_inst_ = NULL;
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "EnableVAD: error in init VAD");
      return -1;
    }
  }

  // Set the VAD mode to the given value.
  if (WebRtcVad_set_mode(ptr_vad_inst_, mode) < 0) {
    // We failed to set the mode and we have to return -1. If we already have a
    // working VAD (vad_enabled_ == true) then we leave it to work. Otherwise,
    // the following will be executed.
    if (!vad_enabled_) {
      // We just created the instance but cannot set the mode we have to free
      // the memory.
      WebRtcVad_Free(ptr_vad_inst_);
      ptr_vad_inst_ = NULL;
    }
    WEBRTC_TRACE(webrtc::kTraceDebug, webrtc::kTraceAudioCoding, unique_id_,
                 "EnableVAD: failed to set the VAD mode");
    return -1;
  }
  vad_mode_ = mode;
  vad_enabled_ = true;
  return 0;
}

int16_t ACMGenericCodec::DisableVAD() {
  if (ptr_vad_inst_ != NULL) {
    WebRtcVad_Free(ptr_vad_inst_);
    ptr_vad_inst_ = NULL;
  }
  vad_enabled_ = false;
  return 0;
}

int32_t ACMGenericCodec::ReplaceInternalDTX(const bool replace_internal_dtx) {
  WriteLockScoped cs(codec_wrapper_lock_);
  return ReplaceInternalDTXSafe(replace_internal_dtx);
}

int32_t ACMGenericCodec::ReplaceInternalDTXSafe(
    const bool /* replace_internal_dtx */) {
  return -1;
}

int32_t ACMGenericCodec::IsInternalDTXReplaced(bool* internal_dtx_replaced) {
  WriteLockScoped cs(codec_wrapper_lock_);
  return IsInternalDTXReplacedSafe(internal_dtx_replaced);
}

int32_t ACMGenericCodec::IsInternalDTXReplacedSafe(
    bool* internal_dtx_replaced) {
  *internal_dtx_replaced = false;
  return 0;
}

int16_t ACMGenericCodec::ProcessFrameVADDTX(uint8_t* bitstream,
                                            int16_t* bitstream_len_byte,
                                            int16_t* samples_processed) {
  if (!vad_enabled_) {
    // VAD not enabled, set all |vad_lable_[]| to 1 (speech detected).
    for (int n = 0; n < MAX_FRAME_SIZE_10MSEC; n++) {
      vad_label_[n] = 1;
    }
    *samples_processed = 0;
    return 0;
  }

  uint16_t freq_hz;
  EncoderSampFreq(&freq_hz);

  // Calculate number of samples in 10 ms blocks, and number ms in one frame.
  int16_t samples_in_10ms = static_cast<int16_t>(freq_hz / 100);
  int32_t frame_len_ms = static_cast<int32_t>(frame_len_smpl_) * 1000 / freq_hz;
  int16_t status = -1;

  // Vector for storing maximum 30 ms of mono audio at 48 kHz.
  int16_t audio[1440];

  // Calculate number of VAD-blocks to process, and number of samples in each
  // block.
  int num_samples_to_process[2];
  if (frame_len_ms == 40) {
    // 20 ms in each VAD block.
    num_samples_to_process[0] = num_samples_to_process[1] = 2 * samples_in_10ms;
  } else {
    // For 10-30 ms framesizes, second VAD block will be size zero ms,
    // for 50 and 60 ms first VAD block will be 30 ms.
    num_samples_to_process[0] =
        (frame_len_ms > 30) ? 3 * samples_in_10ms : frame_len_smpl_;
    num_samples_to_process[1] = frame_len_smpl_ - num_samples_to_process[0];
  }

  int offset = 0;
  int loops = (num_samples_to_process[1] > 0) ? 2 : 1;
  for (int i = 0; i < loops; i++) {
    // TODO(turajs): Do we need to care about VAD together with stereo?
    // If stereo, calculate mean of the two channels.
    if (num_channels_ == 2) {
      for (int j = 0; j < num_samples_to_process[i]; j++) {
        audio[j] = (in_audio_[(offset + j) * 2] +
            in_audio_[(offset + j) * 2 + 1]) / 2;
      }
      offset = num_samples_to_process[0];
    } else {
      // Mono, copy data from in_audio_ to continue work on.
      memcpy(audio, in_audio_, sizeof(int16_t) * num_samples_to_process[i]);
    }

    // Call VAD.
    status = static_cast<int16_t>(WebRtcVad_Process(ptr_vad_inst_,
                                                    static_cast<int>(freq_hz),
                                                    audio,
                                                    num_samples_to_process[i]));
    vad_label_[i] = status;

    if (status < 0) {
      // This will force that the data be removed from the buffer.
      *samples_processed += num_samples_to_process[i];
      return -1;
    }

    // If VAD decision non-active, update DTX. NOTE! We only do this if the
    // first part of a frame gets the VAD decision "inactive". Otherwise DTX
    // might say it is time to transmit SID frame, but we will encode the whole
    // frame, because the first part is active.
    *samples_processed = 0;
    if ((status == 0) && (i == 0) && dtx_enabled_ && !has_internal_dtx_) {
      int16_t bitstream_len;
      int num_10ms_frames = num_samples_to_process[i] / samples_in_10ms;
      *bitstream_len_byte = 0;
      for (int n = 0; n < num_10ms_frames; n++) {
        // This block is (passive) && (vad enabled). If first CNG after
        // speech, force SID by setting last parameter to "1".
        status = WebRtcCng_Encode(ptr_dtx_inst_, &audio[n * samples_in_10ms],
                                  samples_in_10ms, bitstream, &bitstream_len,
                                  !prev_frame_cng_);
        if (status < 0) {
          return -1;
        }

        // Update previous frame was CNG.
        prev_frame_cng_ = 1;

        *samples_processed += samples_in_10ms * num_channels_;

        // |bitstream_len_byte| will only be > 0 once per 100 ms.
        *bitstream_len_byte += bitstream_len;
      }

      // Check if all samples got processed by the DTX.
      if (*samples_processed != num_samples_to_process[i] * num_channels_) {
        // Set to zero since something went wrong. Shouldn't happen.
        *samples_processed = 0;
      }
    } else {
      // Update previous frame was not CNG.
      prev_frame_cng_ = 0;
    }

    if (*samples_processed > 0) {
      // The block contains inactive speech, and is processed by DTX.
      // Discontinue running VAD.
      break;
    }
  }

  return status;
}

int16_t ACMGenericCodec::SamplesLeftToEncode() {
  ReadLockScoped rl(codec_wrapper_lock_);
  return (frame_len_smpl_ <= in_audio_ix_write_) ? 0 :
      (frame_len_smpl_ - in_audio_ix_write_);
}

void ACMGenericCodec::SetUniqueID(const uint32_t id) {
  unique_id_ = id;
}

// This function is replaced by codec specific functions for some codecs.
int16_t ACMGenericCodec::EncoderSampFreq(uint16_t* samp_freq_hz) {
  int32_t f;
  f = ACMCodecDB::CodecFreq(codec_id_);
  if (f < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "EncoderSampFreq: codec frequency is negative");
    return -1;
  } else {
    *samp_freq_hz = static_cast<uint16_t>(f);
    return 0;
  }
}

int32_t ACMGenericCodec::ConfigISACBandwidthEstimator(
    const uint8_t /* init_frame_size_msec */,
    const uint16_t /* init_rate_bit_per_sec */,
    const bool /* enforce_frame_size  */) {
  WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, unique_id_,
               "The send-codec is not iSAC, failed to config iSAC bandwidth "
               "estimator.");
  return -1;
}

int32_t ACMGenericCodec::SetISACMaxRate(
    const uint32_t /* max_rate_bit_per_sec */) {
  WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, unique_id_,
               "The send-codec is not iSAC, failed to set iSAC max rate.");
  return -1;
}

int32_t ACMGenericCodec::SetISACMaxPayloadSize(
    const uint16_t /* max_payload_len_bytes */) {
  WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, unique_id_,
               "The send-codec is not iSAC, failed to set iSAC max "
               "payload-size.");
  return -1;
}

int16_t ACMGenericCodec::UpdateEncoderSampFreq(
    uint16_t /* samp_freq_hz */) {
  WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
               "It is asked for a change in smapling frequency while the "
               "current  send-codec supports only one sampling rate.");
  return -1;
}

int16_t ACMGenericCodec::REDPayloadISAC(const int32_t /* isac_rate */,
                                        const int16_t /* isac_bw_estimate */,
                                        uint8_t* /* payload */,
                                        int16_t* /* payload_len_bytes */) {
  WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
               "Error: REDPayloadISAC is an iSAC specific function");
  return -1;
}

int ACMGenericCodec::SetOpusMaxBandwidth(int /* max_bandwidth */) {
  WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, unique_id_,
               "The send-codec is not Opus, failed to set maximum bandwidth.");
  return -1;
}

}  // namespace acm2

}  // namespace webrtc
