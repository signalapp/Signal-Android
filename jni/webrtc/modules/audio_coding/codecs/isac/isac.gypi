# Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'isac',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
        'audio_decoder_interface',
        'audio_encoder_interface',
        'isac_common',
      ],
      'include_dirs': [
        'main/include',
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'main/include',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'main/include/audio_decoder_isac.h',
        'main/include/audio_encoder_isac.h',
        'main/include/isac.h',
        'main/source/arith_routines.c',
        'main/source/arith_routines_hist.c',
        'main/source/arith_routines_logist.c',
        'main/source/audio_decoder_isac.cc',
        'main/source/audio_encoder_isac.cc',
        'main/source/bandwidth_estimator.c',
        'main/source/crc.c',
        'main/source/decode.c',
        'main/source/decode_bwe.c',
        'main/source/encode.c',
        'main/source/encode_lpc_swb.c',
        'main/source/entropy_coding.c',
        'main/source/fft.c',
        'main/source/filter_functions.c',
        'main/source/filterbank_tables.c',
        'main/source/intialize.c',
        'main/source/isac.c',
        'main/source/isac_float_type.h',
        'main/source/filterbanks.c',
        'main/source/pitch_lag_tables.c',
        'main/source/lattice.c',
        'main/source/lpc_gain_swb_tables.c',
        'main/source/lpc_analysis.c',
        'main/source/lpc_shape_swb12_tables.c',
        'main/source/lpc_shape_swb16_tables.c',
        'main/source/lpc_tables.c',
        'main/source/pitch_estimator.c',
        'main/source/pitch_filter.c',
        'main/source/pitch_gain_tables.c',
        'main/source/spectrum_ar_model_tables.c',
        'main/source/transform.c',
        'main/source/arith_routines.h',
        'main/source/bandwidth_estimator.h',
        'main/source/codec.h',
        'main/source/crc.h',
        'main/source/encode_lpc_swb.h',
        'main/source/entropy_coding.h',
        'main/source/fft.h',
        'main/source/filterbank_tables.h',
        'main/source/lpc_gain_swb_tables.h',
        'main/source/lpc_analysis.h',
        'main/source/lpc_shape_swb12_tables.h',
        'main/source/lpc_shape_swb16_tables.h',
        'main/source/lpc_tables.h',
        'main/source/pitch_estimator.h',
        'main/source/pitch_gain_tables.h',
        'main/source/pitch_lag_tables.h',
        'main/source/settings.h',
        'main/source/spectrum_ar_model_tables.h',
        'main/source/structs.h',
        'main/source/os_specific_inline.h',
     ],
     'conditions': [
       ['OS=="linux"', {
         'link_settings': {
           'libraries': ['-lm',],
         },
       }],
     ],
    },
  ],
}
