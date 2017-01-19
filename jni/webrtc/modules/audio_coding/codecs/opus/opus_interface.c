/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/interface/opus_interface.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_inst.h"

#include <stdlib.h>
#include <string.h>

enum {
  /* Maximum supported frame size in WebRTC is 60 ms. */
  kWebRtcOpusMaxEncodeFrameSizeMs = 60,

  /* The format allows up to 120 ms frames. Since we don't control the other
   * side, we must allow for packets of that size. NetEq is currently limited
   * to 60 ms on the receive side. */
  kWebRtcOpusMaxDecodeFrameSizeMs = 120,

  /* Maximum sample count per channel is 48 kHz * maximum frame size in
   * milliseconds. */
  kWebRtcOpusMaxFrameSizePerChannel = 48 * kWebRtcOpusMaxDecodeFrameSizeMs,

  /* Default frame size, 20 ms @ 48 kHz, in samples (for one channel). */
  kWebRtcOpusDefaultFrameSize = 960,
};

int16_t WebRtcOpus_EncoderCreate(OpusEncInst** inst, int32_t channels) {
  OpusEncInst* state;
  if (inst != NULL) {
    state = (OpusEncInst*) calloc(1, sizeof(OpusEncInst));
    if (state) {
      int error;
      /* Default to VoIP application for mono, and AUDIO for stereo. */
      int application = (channels == 1) ? OPUS_APPLICATION_VOIP :
          OPUS_APPLICATION_AUDIO;

      state->encoder = opus_encoder_create(48000, channels, application,
                                           &error);
      if (error == OPUS_OK && state->encoder != NULL) {
        *inst = state;
        return 0;
      }
      free(state);
    }
  }
  return -1;
}

int16_t WebRtcOpus_EncoderFree(OpusEncInst* inst) {
  if (inst) {
    opus_encoder_destroy(inst->encoder);
    free(inst);
    return 0;
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_Encode(OpusEncInst* inst, int16_t* audio_in, int16_t samples,
                          int16_t length_encoded_buffer, uint8_t* encoded) {
  opus_int16* audio = (opus_int16*) audio_in;
  unsigned char* coded = encoded;
  int res;

  if (samples > 48 * kWebRtcOpusMaxEncodeFrameSizeMs) {
    return -1;
  }

  res = opus_encode(inst->encoder, audio, samples, coded,
                    length_encoded_buffer);

  if (res > 0) {
    return res;
  }
  return -1;
}

int16_t WebRtcOpus_SetBitRate(OpusEncInst* inst, int32_t rate) {
  if (inst) {
    return opus_encoder_ctl(inst->encoder, OPUS_SET_BITRATE(rate));
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_SetPacketLossRate(OpusEncInst* inst, int32_t loss_rate) {
  if (inst) {
    return opus_encoder_ctl(inst->encoder,
                            OPUS_SET_PACKET_LOSS_PERC(loss_rate));
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_SetMaxBandwidth(OpusEncInst* inst, int32_t bandwidth) {
  opus_int32 set_bandwidth;

  if (!inst)
    return -1;

  if (bandwidth <= 4000) {
    set_bandwidth = OPUS_BANDWIDTH_NARROWBAND;
  } else if (bandwidth <= 6000) {
    set_bandwidth = OPUS_BANDWIDTH_MEDIUMBAND;
  } else if (bandwidth <= 8000) {
    set_bandwidth = OPUS_BANDWIDTH_WIDEBAND;
  } else if (bandwidth <= 12000) {
    set_bandwidth = OPUS_BANDWIDTH_SUPERWIDEBAND;
  } else {
    set_bandwidth = OPUS_BANDWIDTH_FULLBAND;
  }
  return opus_encoder_ctl(inst->encoder,
                          OPUS_SET_MAX_BANDWIDTH(set_bandwidth));
}

int16_t WebRtcOpus_EnableFec(OpusEncInst* inst) {
  if (inst) {
    return opus_encoder_ctl(inst->encoder, OPUS_SET_INBAND_FEC(1));
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_DisableFec(OpusEncInst* inst) {
  if (inst) {
    return opus_encoder_ctl(inst->encoder, OPUS_SET_INBAND_FEC(0));
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_SetComplexity(OpusEncInst* inst, int32_t complexity) {
  if (inst) {
    return opus_encoder_ctl(inst->encoder, OPUS_SET_COMPLEXITY(complexity));
  } else {
    return -1;
  }
}

int16_t WebRtcOpus_DecoderCreate(OpusDecInst** inst, int channels) {
  int error_l;
  int error_r;
  OpusDecInst* state;

  if (inst != NULL) {
    /* Create Opus decoder state. */
    state = (OpusDecInst*) calloc(1, sizeof(OpusDecInst));
    if (state == NULL) {
      return -1;
    }

    /* Create new memory for left and right channel, always at 48000 Hz. */
    state->decoder_left = opus_decoder_create(48000, channels, &error_l);
    state->decoder_right = opus_decoder_create(48000, channels, &error_r);
    if (error_l == OPUS_OK && error_r == OPUS_OK && state->decoder_left != NULL
        && state->decoder_right != NULL) {
      /* Creation of memory all ok. */
      state->channels = channels;
      state->prev_decoded_samples = kWebRtcOpusDefaultFrameSize;
      *inst = state;
      return 0;
    }

    /* If memory allocation was unsuccessful, free the entire state. */
    if (state->decoder_left) {
      opus_decoder_destroy(state->decoder_left);
    }
    if (state->decoder_right) {
      opus_decoder_destroy(state->decoder_right);
    }
    free(state);
  }
  return -1;
}

int16_t WebRtcOpus_DecoderFree(OpusDecInst* inst) {
  if (inst) {
    opus_decoder_destroy(inst->decoder_left);
    opus_decoder_destroy(inst->decoder_right);
    free(inst);
    return 0;
  } else {
    return -1;
  }
}

int WebRtcOpus_DecoderChannels(OpusDecInst* inst) {
  return inst->channels;
}

int16_t WebRtcOpus_DecoderInitNew(OpusDecInst* inst) {
  int error = opus_decoder_ctl(inst->decoder_left, OPUS_RESET_STATE);
  if (error == OPUS_OK) {
    return 0;
  }
  return -1;
}

int16_t WebRtcOpus_DecoderInit(OpusDecInst* inst) {
  int error = opus_decoder_ctl(inst->decoder_left, OPUS_RESET_STATE);
  if (error == OPUS_OK) {
    return 0;
  }
  return -1;
}

int16_t WebRtcOpus_DecoderInitSlave(OpusDecInst* inst) {
  int error = opus_decoder_ctl(inst->decoder_right, OPUS_RESET_STATE);
  if (error == OPUS_OK) {
    return 0;
  }
  return -1;
}

/* |frame_size| is set to maximum Opus frame size in the normal case, and
 * is set to the number of samples needed for PLC in case of losses.
 * It is up to the caller to make sure the value is correct. */
static int DecodeNative(OpusDecoder* inst, const int16_t* encoded,
                        int16_t encoded_bytes, int frame_size,
                        int16_t* decoded, int16_t* audio_type) {
  unsigned char* coded = (unsigned char*) encoded;
  opus_int16* audio = (opus_int16*) decoded;

  int res = opus_decode(inst, coded, encoded_bytes, audio, frame_size, 0);

  /* TODO(tlegrand): set to DTX for zero-length packets? */
  *audio_type = 0;

  if (res > 0) {
    return res;
  }
  return -1;
}

static int DecodeFec(OpusDecoder* inst, const int16_t* encoded,
                     int16_t encoded_bytes, int frame_size,
                     int16_t* decoded, int16_t* audio_type) {
  unsigned char* coded = (unsigned char*) encoded;
  opus_int16* audio = (opus_int16*) decoded;

  int res = opus_decode(inst, coded, encoded_bytes, audio, frame_size, 1);

  /* TODO(tlegrand): set to DTX for zero-length packets? */
  *audio_type = 0;

  if (res > 0) {
    return res;
  }
  return -1;
}

int16_t WebRtcOpus_DecodeNew(OpusDecInst* inst, const uint8_t* encoded,
                             int16_t encoded_bytes, int16_t* decoded,
                             int16_t* audio_type) {
  int16_t* coded = (int16_t*)encoded;
  int decoded_samples;

  decoded_samples = DecodeNative(inst->decoder_left, coded, encoded_bytes,
                                 kWebRtcOpusMaxFrameSizePerChannel,
                                 decoded, audio_type);
  if (decoded_samples < 0) {
    return -1;
  }

  /* Update decoded sample memory, to be used by the PLC in case of losses. */
  inst->prev_decoded_samples = decoded_samples;

  return decoded_samples;
}

int16_t WebRtcOpus_Decode(OpusDecInst* inst, const int16_t* encoded,
                          int16_t encoded_bytes, int16_t* decoded,
                          int16_t* audio_type) {
  int decoded_samples;
  int i;

  /* If mono case, just do a regular call to the decoder.
   * If stereo, call to WebRtcOpus_Decode() gives left channel as output, and
   * calls to WebRtcOpus_Decode_slave() give right channel as output.
   * This is to make stereo work with the current setup of NetEQ, which
   * requires two calls to the decoder to produce stereo. */

  decoded_samples = DecodeNative(inst->decoder_left, encoded, encoded_bytes,
                                 kWebRtcOpusMaxFrameSizePerChannel, decoded,
                                 audio_type);
  if (decoded_samples < 0) {
    return -1;
  }
  if (inst->channels == 2) {
    /* The parameter |decoded_samples| holds the number of samples pairs, in
     * case of stereo. Number of samples in |decoded| equals |decoded_samples|
     * times 2. */
    for (i = 0; i < decoded_samples; i++) {
      /* Take every second sample, starting at the first sample. This gives
       * the left channel. */
      decoded[i] = decoded[i * 2];
    }
  }

  /* Update decoded sample memory, to be used by the PLC in case of losses. */
  inst->prev_decoded_samples = decoded_samples;

  return decoded_samples;
}

int16_t WebRtcOpus_DecodeSlave(OpusDecInst* inst, const int16_t* encoded,
                               int16_t encoded_bytes, int16_t* decoded,
                               int16_t* audio_type) {
  int decoded_samples;
  int i;

  decoded_samples = DecodeNative(inst->decoder_right, encoded, encoded_bytes,
                                 kWebRtcOpusMaxFrameSizePerChannel, decoded,
                                 audio_type);
  if (decoded_samples < 0) {
    return -1;
  }
  if (inst->channels == 2) {
    /* The parameter |decoded_samples| holds the number of samples pairs, in
     * case of stereo. Number of samples in |decoded| equals |decoded_samples|
     * times 2. */
    for (i = 0; i < decoded_samples; i++) {
      /* Take every second sample, starting at the second sample. This gives
       * the right channel. */
      decoded[i] = decoded[i * 2 + 1];
    }
  } else {
    /* Decode slave should never be called for mono packets. */
    return -1;
  }

  return decoded_samples;
}

int16_t WebRtcOpus_DecodePlc(OpusDecInst* inst, int16_t* decoded,
                             int16_t number_of_lost_frames) {
  int16_t audio_type = 0;
  int decoded_samples;
  int plc_samples;

  /* The number of samples we ask for is |number_of_lost_frames| times
   * |prev_decoded_samples_|. Limit the number of samples to maximum
   * |kWebRtcOpusMaxFrameSizePerChannel|. */
  plc_samples = number_of_lost_frames * inst->prev_decoded_samples;
  plc_samples = (plc_samples <= kWebRtcOpusMaxFrameSizePerChannel) ?
      plc_samples : kWebRtcOpusMaxFrameSizePerChannel;
  decoded_samples = DecodeNative(inst->decoder_left, NULL, 0, plc_samples,
                                 decoded, &audio_type);
  if (decoded_samples < 0) {
    return -1;
  }

  return decoded_samples;
}

int16_t WebRtcOpus_DecodePlcMaster(OpusDecInst* inst, int16_t* decoded,
                                   int16_t number_of_lost_frames) {
  int decoded_samples;
  int16_t audio_type = 0;
  int plc_samples;
  int i;

  /* If mono case, just do a regular call to the decoder.
   * If stereo, call to WebRtcOpus_DecodePlcMaster() gives left channel as
   * output, and calls to WebRtcOpus_DecodePlcSlave() give right channel as
   * output. This is to make stereo work with the current setup of NetEQ, which
   * requires two calls to the decoder to produce stereo. */

  /* The number of samples we ask for is |number_of_lost_frames| times
   * |prev_decoded_samples_|. Limit the number of samples to maximum
   * |kWebRtcOpusMaxFrameSizePerChannel|. */
  plc_samples = number_of_lost_frames * inst->prev_decoded_samples;
  plc_samples = (plc_samples <= kWebRtcOpusMaxFrameSizePerChannel) ?
      plc_samples : kWebRtcOpusMaxFrameSizePerChannel;
  decoded_samples = DecodeNative(inst->decoder_left, NULL, 0, plc_samples,
                                 decoded, &audio_type);
  if (decoded_samples < 0) {
    return -1;
  }

  if (inst->channels == 2) {
    /* The parameter |decoded_samples| holds the number of sample pairs, in
     * case of stereo. The original number of samples in |decoded| equals
     * |decoded_samples| times 2. */
    for (i = 0; i < decoded_samples; i++) {
      /* Take every second sample, starting at the first sample. This gives
       * the left channel. */
      decoded[i] = decoded[i * 2];
    }
  }

  return decoded_samples;
}

int16_t WebRtcOpus_DecodePlcSlave(OpusDecInst* inst, int16_t* decoded,
                                  int16_t number_of_lost_frames) {
  int decoded_samples;
  int16_t audio_type = 0;
  int plc_samples;
  int i;

  /* Calls to WebRtcOpus_DecodePlcSlave() give right channel as output.
   * The function should never be called in the mono case. */
  if (inst->channels != 2) {
    return -1;
  }

  /* The number of samples we ask for is |number_of_lost_frames| times
   *  |prev_decoded_samples_|. Limit the number of samples to maximum
   *  |kWebRtcOpusMaxFrameSizePerChannel|. */
  plc_samples = number_of_lost_frames * inst->prev_decoded_samples;
  plc_samples = (plc_samples <= kWebRtcOpusMaxFrameSizePerChannel)
      ? plc_samples : kWebRtcOpusMaxFrameSizePerChannel;
  decoded_samples = DecodeNative(inst->decoder_right, NULL, 0, plc_samples,
                                 decoded, &audio_type);
  if (decoded_samples < 0) {
    return -1;
  }

  /* The parameter |decoded_samples| holds the number of sample pairs,
   * The original number of samples in |decoded| equals |decoded_samples|
   * times 2. */
  for (i = 0; i < decoded_samples; i++) {
    /* Take every second sample, starting at the second sample. This gives
     * the right channel. */
    decoded[i] = decoded[i * 2 + 1];
  }

  return decoded_samples;
}

int16_t WebRtcOpus_DecodeFec(OpusDecInst* inst, const uint8_t* encoded,
                             int16_t encoded_bytes, int16_t* decoded,
                             int16_t* audio_type) {
  int16_t* coded = (int16_t*)encoded;
  int decoded_samples;
  int fec_samples;

  if (WebRtcOpus_PacketHasFec(encoded, encoded_bytes) != 1) {
    return 0;
  }

  fec_samples = opus_packet_get_samples_per_frame(encoded, 48000);

  decoded_samples = DecodeFec(inst->decoder_left, coded, encoded_bytes,
                              fec_samples, decoded, audio_type);
  if (decoded_samples < 0) {
    return -1;
  }

  return decoded_samples;
}

int WebRtcOpus_DurationEst(OpusDecInst* inst,
                           const uint8_t* payload,
                           int payload_length_bytes) {
  int frames, samples;
  frames = opus_packet_get_nb_frames(payload, payload_length_bytes);
  if (frames < 0) {
    /* Invalid payload data. */
    return 0;
  }
  samples = frames * opus_packet_get_samples_per_frame(payload, 48000);
  if (samples < 120 || samples > 5760) {
    /* Invalid payload duration. */
    return 0;
  }
  return samples;
}

int WebRtcOpus_FecDurationEst(const uint8_t* payload,
                              int payload_length_bytes) {
  int samples;
  if (WebRtcOpus_PacketHasFec(payload, payload_length_bytes) != 1) {
    return 0;
  }

  samples = opus_packet_get_samples_per_frame(payload, 48000);
  if (samples < 480 || samples > 5760) {
    /* Invalid payload duration. */
    return 0;
  }
  return samples;
}

int WebRtcOpus_PacketHasFec(const uint8_t* payload,
                            int payload_length_bytes) {
  int frames, channels, payload_length_ms;
  int n;
  opus_int16 frame_sizes[48];
  const unsigned char *frame_data[48];

  if (payload == NULL || payload_length_bytes <= 0)
    return 0;

  /* In CELT_ONLY mode, packets should not have FEC. */
  if (payload[0] & 0x80)
    return 0;

  payload_length_ms = opus_packet_get_samples_per_frame(payload, 48000) / 48;
  if (10 > payload_length_ms)
    payload_length_ms = 10;

  channels = opus_packet_get_nb_channels(payload);

  switch (payload_length_ms) {
    case 10:
    case 20: {
      frames = 1;
      break;
    }
    case 40: {
      frames = 2;
      break;
    }
    case 60: {
      frames = 3;
      break;
    }
    default: {
      return 0; // It is actually even an invalid packet.
    }
  }

  /* The following is to parse the LBRR flags. */
  if (opus_packet_parse(payload, payload_length_bytes, NULL, frame_data,
                        frame_sizes, NULL) < 0) {
    return 0;
  }

  if (frame_sizes[0] <= 1) {
    return 0;
  }

  for (n = 0; n < channels; n++) {
    if (frame_data[0][0] & (0x80 >> ((n + 1) * (frames + 1) - 1)))
      return 1;
  }

  return 0;
}
