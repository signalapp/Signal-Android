# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'isac_fix',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
        '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
        'isac_common',
      ],
      'include_dirs': [
        'fix/include',
        '<(webrtc_root)'
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'fix/include',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'fix/include/audio_decoder_isacfix.h',
        'fix/include/audio_encoder_isacfix.h',
        'fix/include/isacfix.h',
        'fix/source/arith_routines.c',
        'fix/source/arith_routines_hist.c',
        'fix/source/arith_routines_logist.c',
        'fix/source/audio_decoder_isacfix.cc',
        'fix/source/audio_encoder_isacfix.cc',
        'fix/source/bandwidth_estimator.c',
        'fix/source/decode.c',
        'fix/source/decode_bwe.c',
        'fix/source/decode_plc.c',
        'fix/source/encode.c',
        'fix/source/entropy_coding.c',
        'fix/source/fft.c',
        'fix/source/filterbank_tables.c',
        'fix/source/filterbanks.c',
        'fix/source/filters.c',
        'fix/source/initialize.c',
        'fix/source/isac_fix_type.h',
        'fix/source/isacfix.c',
        'fix/source/lattice.c',
        'fix/source/lattice_c.c',
        'fix/source/lpc_masking_model.c',
        'fix/source/lpc_tables.c',
        'fix/source/pitch_estimator.c',
        'fix/source/pitch_estimator_c.c',
        'fix/source/pitch_filter.c',
        'fix/source/pitch_filter_c.c',
        'fix/source/pitch_gain_tables.c',
        'fix/source/pitch_lag_tables.c',
        'fix/source/spectrum_ar_model_tables.c',
        'fix/source/transform.c',
        'fix/source/transform_tables.c',
        'fix/source/arith_routins.h',
        'fix/source/bandwidth_estimator.h',
        'fix/source/codec.h',
        'fix/source/entropy_coding.h',
        'fix/source/fft.h',
        'fix/source/filterbank_tables.h',
        'fix/source/lpc_masking_model.h',
        'fix/source/lpc_tables.h',
        'fix/source/pitch_estimator.h',
        'fix/source/pitch_gain_tables.h',
        'fix/source/pitch_lag_tables.h',
        'fix/source/settings.h',
        'fix/source/spectrum_ar_model_tables.h',
        'fix/source/structs.h',
      ],
      'conditions': [
        ['target_arch=="arm" and arm_version>=7', {
          'sources': [
            'fix/source/lattice_armv7.S',
            'fix/source/pitch_filter_armv6.S',
          ],
          'sources!': [
            'fix/source/lattice_c.c',
            'fix/source/pitch_filter_c.c',
          ],
        }],
        ['build_with_neon==1', {
          'dependencies': ['isac_neon', ],
        }],
        ['target_arch=="mipsel" and mips_arch_variant!="r6"', {
          'sources': [
            'fix/source/entropy_coding_mips.c',
            'fix/source/filters_mips.c',
            'fix/source/lattice_mips.c',
            'fix/source/pitch_estimator_mips.c',
            'fix/source/transform_mips.c',
          ],
          'sources!': [
            'fix/source/lattice_c.c',
            'fix/source/pitch_estimator_c.c',
          ],
          'conditions': [
            ['mips_dsp_rev>0', {
              'sources': [
                'fix/source/filterbanks_mips.c',
              ],
            }],
            ['mips_dsp_rev>1', {
              'sources': [
                'fix/source/lpc_masking_model_mips.c',
                'fix/source/pitch_filter_mips.c',
              ],
              'sources!': [
                'fix/source/pitch_filter_c.c',
              ],
            }],
          ],
        }],
      ],
    },
  ],
  'conditions': [
    ['build_with_neon==1', {
      'targets': [
        {
          'target_name': 'isac_neon',
          'type': 'static_library',
          'includes': ['../../../../build/arm_neon.gypi',],
          'dependencies': [
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
          ],
          'sources': [
            'fix/source/entropy_coding_neon.c',
            'fix/source/filterbanks_neon.c',
            'fix/source/filters_neon.c',
            'fix/source/lattice_neon.c',
            'fix/source/transform_neon.c',
          ],
        },
      ],
    }],
  ],
}
