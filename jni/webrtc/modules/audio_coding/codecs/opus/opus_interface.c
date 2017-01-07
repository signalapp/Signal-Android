/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_inst.h"

#include <assert.h>
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

int16_t WebRtcOpus_EncoderCreate(OpusEncInst** inst,
                                 size_t channels,
                                 int32_t application) {
  int opus_app;
  if (!inst)
    return -1;

  switch (application) {
    case 0:
      opus_app = OPUS_APPLICATION_VOIP;
      break;
    case 1:
      opus_app = OPUS_APPLICATION_AUDIO;
      break;
    default:
      return -1;
  }

  OpusEncInst* state = calloc(1, sizeof(OpusEncInst));
  assert(state);

  int error;
  state->encoder = opus_encoder_create(48000, (int)channels, opus_app,
                                       &error);
  if (error != OPUS_OK || !state->encoder) {
    WebRtcOpus_EncoderFree(state);
    return -1;
  }

  state->in_dtx_mode = 0;
  state->channels = channels;

  *inst = state;
  return 0;
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

int WebRtcOpus_Encode(OpusEncInst* inst,
                      const int16_t* audio_in,
                      size_t samples,
                      size_t length_encoded_buffer,
                      uint8_t* encoded) {
  int res;

  if (samples > 48 * kWebRtcOpusMaxEncodeFrameSizeMs) {
    return -1;
  }

  res = opus_encode(inst->encoder,
                    (const opus_int16*)audio_in,
                    (int)samples,
                    encoded,
                    (opus_int32)length_encoded_buffer);

  if (res == 1) {
    // Indicates DTX since the packet has nothing but a header. In principle,
    // there is no need to send this packet. However, we do transmit the first
    // occurrence to let the decoder know that the encoder enters DTX mode.
    if (inst->in_dtx_mode) {
      return 0;
    } else {
      inst->in_dtx_mode = 1;
      return 1;
    }
  } else if (res > 1) {
    inst->in_dtx_mode = 0;
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

int16_t WebRtcOpus_SetMaxPlaybackRate(OpusEncInst* inst, int32_t frequency_hz) {
  opus_int32 set_bandwidth;

  if (!inst)
    return -1;

  if (frequency_hz <= 8000) {
    set_bandwidth = OPUS_BANDWIDTH_NARROWBAND;
  } else if (frequency_hz <= 12000) {
    set_bandwidth = OPUS_BANDWIDTH_MEDIUMBAND;
  } else if (frequency_hz <= 16000) {
    set_bandwidth = OPUS_BANDWIDTH_WIDEBAND;
  } else if (frequency_hz <= 24000) {
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

int16_t WebRtcOpus_EnableDtx(OpusEncInst* inst) {
  if (!inst) {
    return -1;
  }

  // To prevent Opus from entering CELT-only mode by forcing signal type to
  // voice to make sure that DTX behaves correctly. Currently, DTX does not
  // last long during a pure silence, if the signal type is not forced.
  // TODO(minyue): Remove the signal type forcing when Opus DTX works properly
  // without it.
  int ret = opus_encoder_ctl(inst->encoder,
                             OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
  if (ret != OPUS_OK)
    return ret;

  return opus_encoder_ctl(inst->encoder, OPUS_SET_DTX(1));
}

int16_t WebRtcOpus_DisableDtx(OpusEncInst* inst) {
  if (inst) {
    int ret = opus_encoder_ctl(inst->encoder,
                               OPUS_SET_SIGNAL(OPUS_AUTO));
    if (ret != OPUS_OK)
      return ret;
    return opus_encoder_ctl(inst->encoder, OPUS_SET_DTX(0));
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

int16_t WebRtcOpus_DecoderCreate(OpusDecInst** inst, size_t channels) {
  int error;
  OpusDecInst* state;

  if (inst != NULL) {
    /* Create Opus decoder state. */
    state = (OpusDecInst*) calloc(1, sizeof(OpusDecInst));
    if (state == NULL) {
      return -1;
    }

    /* Create new memory, always at 48000 Hz. */
    state->decoder = opus_decoder_create(48000, (int)channels, &error);
    if (error == OPUS_OK && state->decoder != NULL) {
      /* Creation of memory all ok. */
      state->channels = channels;
      state->prev_decoded_samples = kWebRtcOpusDefaultFrameSize;
      state->in_dtx_mode = 0;
      *inst = state;
      return 0;
    }

    /* If memory allocation was unsuccessful, free the entire state. */
    if (state->decoder) {
      opus_decoder_destroy(state->decoder);
    }
    free(state);
  }
  return -1;
}

int16_t WebRtcOpus_DecoderFree(OpusDecInst* inst) {
  if (inst) {
    opus_decoder_destroy(inst->decoder);
    free(inst);
    return 0;
  } else {
    return -1;
  }
}

size_t WebRtcOpus_DecoderChannels(OpusDecInst* inst) {
  return inst->channels;
}

void WebRtcOpus_DecoderInit(OpusDecInst* inst) {
  opus_decoder_ctl(inst->decoder, OPUS_RESET_STATE);
  inst->in_dtx_mode = 0;
}

/* For decoder to determine if it is to output speech or comfort noise. */
static int16_t DetermineAudioType(OpusDecInst* inst, size_t encoded_bytes) {
  // Audio type becomes comfort noise if |encoded_byte| is 1 and keeps
  // to be so if the following |encoded_byte| are 0 or 1.
  if (encoded_bytes == 0 && inst->in_dtx_mode) {
    return 2;  // Comfort noise.
  } else if (encoded_bytes == 1) {
    inst->in_dtx_mode = 1;
    return 2;  // Comfort noise.
  } else {
    inst->in_dtx_mode = 0;
    return 0;  // Speech.
  }
}

/* |frame_size| is set to maximum Opus frame size in the normal case, and
 * is set to the number of samples needed for PLC in case of losses.
 * It is up to the caller to make sure the value is correct. */
static int DecodeNative(OpusDecInst* inst, const uint8_t* encoded,
                        size_t encoded_bytes, int frame_size,
                        int16_t* decoded, int16_t* audio_type, int decode_fec) {
  int res = opus_decode(inst->decoder, encoded, (opus_int32)encoded_bytes,
                        (opus_int16*)decoded, frame_size, decode_fec);

  if (res <= 0)
    return -1;

  *audio_type = DetermineAudioType(inst, encoded_bytes);

  return res;
}

int WebRtcOpus_Decode(OpusDecInst* inst, const uint8_t* encoded,
                      size_t encoded_bytes, int16_t* decoded,
                      int16_t* audio_type) {
  int decoded_samples;

  if (encoded_bytes == 0) {
    *audio_type = DetermineAudioType(inst, encoded_bytes);
    decoded_samples = WebRtcOpus_DecodePlc(inst, decoded, 1);
  } else {
    decoded_samples = DecodeNative(inst,
                                   encoded,
                                   encoded_bytes,
                                   kWebRtcOpusMaxFrameSizePerChannel,
                                   decoded,
                                   audio_type,
                                   0);
  }
  if (decoded_samples < 0) {
    return -1;
  }

  /* Update decoded sample memory, to be used by the PLC in case of losses. */
  inst->prev_decoded_samples = decoded_samples;

  return decoded_samples;
}

int WebRtcOpus_DecodePlc(OpusDecInst* inst, int16_t* decoded,
                         int number_of_lost_frames) {
  int16_t audio_type = 0;
  int decoded_samples;
  int plc_samples;

  /* The number of samples we ask for is |number_of_lost_frames| times
   * |prev_decoded_samples_|. Limit the number of samples to maximum
   * |kWebRtcOpusMaxFrameSizePerChannel|. */
  plc_samples = number_of_lost_frames * inst->prev_decoded_samples;
  plc_samples = (plc_samples <= kWebRtcOpusMaxFrameSizePerChannel) ?
      plc_samples : kWebRtcOpusMaxFrameSizePerChannel;
  decoded_samples = DecodeNative(inst, NULL, 0, plc_samples,
                                 decoded, &audio_type, 0);
  if (decoded_samples < 0) {
    return -1;
  }

  return decoded_samples;
}

int WebRtcOpus_DecodeFec(OpusDecInst* inst, const uint8_t* encoded,
                         size_t encoded_bytes, int16_t* decoded,
                         int16_t* audio_type) {
  int decoded_samples;
  int fec_samples;

  if (WebRtcOpus_PacketHasFec(encoded, encoded_bytes) != 1) {
    return 0;
  }

  fec_samples = opus_packet_get_samples_per_frame(encoded, 48000);

  decoded_samples = DecodeNative(inst, encoded, encoded_bytes,
                                 fec_samples, decoded, audio_type, 1);
  if (decoded_samples < 0) {
    return -1;
  }

  return decoded_samples;
}

int WebRtcOpus_DurationEst(OpusDecInst* inst,
                           const uint8_t* payload,
                           size_t payload_length_bytes) {
  if (payload_length_bytes == 0) {
    // WebRtcOpus_Decode calls PLC when payload length is zero. So we return
    // PLC duration correspondingly.
    return WebRtcOpus_PlcDuration(inst);
  }

  int frames, samples;
  frames = opus_packet_get_nb_frames(payload, (opus_int32)payload_length_bytes);
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

int WebRtcOpus_PlcDuration(OpusDecInst* inst) {
  /* The number of samples we ask for is |number_of_lost_frames| times
   * |prev_decoded_samples_|. Limit the number of samples to maximum
   * |kWebRtcOpusMaxFrameSizePerChannel|. */
  const int plc_samples = inst->prev_decoded_samples;
  return (plc_samples <= kWebRtcOpusMaxFrameSizePerChannel) ?
      plc_samples : kWebRtcOpusMaxFrameSizePerChannel;
}

int WebRtcOpus_FecDurationEst(const uint8_t* payload,
                              size_t payload_length_bytes) {
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
                            size_t payload_length_bytes) {
  int frames, channels, payload_length_ms;
  int n;
  opus_int16 frame_sizes[48];
  const unsigned char *frame_data[48];

  if (payload == NULL || payload_length_bytes == 0)
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
  if (opus_packet_parse(payload, (opus_int32)payload_length_bytes, NULL,
                        frame_data, frame_sizes, NULL) < 0) {
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
