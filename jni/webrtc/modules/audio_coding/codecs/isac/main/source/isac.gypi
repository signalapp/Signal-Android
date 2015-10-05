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
      'target_name': 'iSAC',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
      ],
      'include_dirs': [
        '../interface',
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '../interface',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        '../interface/isac.h',
        'arith_routines.c',
        'arith_routines_hist.c',
        'arith_routines_logist.c',
        'bandwidth_estimator.c',
        'crc.c',
        'decode.c',
        'decode_bwe.c',
        'encode.c',
        'encode_lpc_swb.c',
        'entropy_coding.c',
        'fft.c',
        'filter_functions.c',
        'filterbank_tables.c',
        'intialize.c',
        'isac.c',
        'filterbanks.c',
        'pitch_lag_tables.c',
        'lattice.c',
        'lpc_gain_swb_tables.c',
        'lpc_analysis.c',
        'lpc_shape_swb12_tables.c',
        'lpc_shape_swb16_tables.c',
        'lpc_tables.c',
        'pitch_estimator.c',
        'pitch_filter.c',
        'pitch_gain_tables.c',
        'spectrum_ar_model_tables.c',
        'transform.c',
        'arith_routines.h',
        'bandwidth_estimator.h',
        'codec.h',
        'crc.h',
        'encode_lpc_swb.h',
        'entropy_coding.h',
        'fft.h',
        'filterbank_tables.h',
        'lpc_gain_swb_tables.h',
        'lpc_analysis.h',
        'lpc_shape_swb12_tables.h',
        'lpc_shape_swb16_tables.h',
        'lpc_tables.h',
        'pitch_estimator.h',
        'pitch_gain_tables.h',
        'pitch_lag_tables.h',
        'settings.h',
        'spectrum_ar_model_tables.h',
        'structs.h',
        'os_specific_inline.h',
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
