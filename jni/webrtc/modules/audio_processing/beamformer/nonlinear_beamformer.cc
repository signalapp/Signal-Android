/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#define _USE_MATH_DEFINES

#include "webrtc/modules/audio_processing/beamformer/nonlinear_beamformer.h"

#include <algorithm>
#include <cmath>
#include <numeric>
#include <vector>

#include "webrtc/base/arraysize.h"
#include "webrtc/common_audio/window_generator.h"
#include "webrtc/modules/audio_processing/beamformer/covariance_matrix_generator.h"

namespace webrtc {
namespace {

// Alpha for the Kaiser Bessel Derived window.
const float kKbdAlpha = 1.5f;

const float kSpeedOfSoundMeterSeconds = 343;

// The minimum separation in radians between the target direction and an
// interferer scenario.
const float kMinAwayRadians = 0.2f;

// The separation between the target direction and the closest interferer
// scenario is proportional to this constant.
const float kAwaySlope = 0.008f;

// When calculating the interference covariance matrix, this is the weight for
// the weighted average between the uniform covariance matrix and the angled
// covariance matrix.
// Rpsi = Rpsi_angled * kBalance + Rpsi_uniform * (1 - kBalance)
const float kBalance = 0.95f;

// Alpha coefficients for mask smoothing.
const float kMaskTimeSmoothAlpha = 0.2f;
const float kMaskFrequencySmoothAlpha = 0.6f;

// The average mask is computed from masks in this mid-frequency range. If these
// ranges are changed |kMaskQuantile| might need to be adjusted.
const int kLowMeanStartHz = 200;
const int kLowMeanEndHz = 400;

// Range limiter for subtractive terms in the nominator and denominator of the
// postfilter expression. It handles the scenario mismatch between the true and
// model sources (target and interference).
const float kCutOffConstant = 0.9999f;

// Quantile of mask values which is used to estimate target presence.
const float kMaskQuantile = 0.7f;
// Mask threshold over which the data is considered signal and not interference.
// It has to be updated every time the postfilter calculation is changed
// significantly.
// TODO(aluebs): Write a tool to tune the target threshold automatically based
// on files annotated with target and interference ground truth.
const float kMaskTargetThreshold = 0.01f;
// Time in seconds after which the data is considered interference if the mask
// does not pass |kMaskTargetThreshold|.
const float kHoldTargetSeconds = 0.25f;

// To compensate for the attenuation this algorithm introduces to the target
// signal. It was estimated empirically from a low-noise low-reverberation
// recording from broadside.
const float kCompensationGain = 2.f;

// Does conjugate(|norm_mat|) * |mat| * transpose(|norm_mat|). No extra space is
// used; to accomplish this, we compute both multiplications in the same loop.
// The returned norm is clamped to be non-negative.
float Norm(const ComplexMatrix<float>& mat,
           const ComplexMatrix<float>& norm_mat) {
  RTC_CHECK_EQ(1u, norm_mat.num_rows());
  RTC_CHECK_EQ(norm_mat.num_columns(), mat.num_rows());
  RTC_CHECK_EQ(norm_mat.num_columns(), mat.num_columns());

  complex<float> first_product = complex<float>(0.f, 0.f);
  complex<float> second_product = complex<float>(0.f, 0.f);

  const complex<float>* const* mat_els = mat.elements();
  const complex<float>* const* norm_mat_els = norm_mat.elements();

  for (size_t i = 0; i < norm_mat.num_columns(); ++i) {
    for (size_t j = 0; j < norm_mat.num_columns(); ++j) {
      first_product += conj(norm_mat_els[0][j]) * mat_els[j][i];
    }
    second_product += first_product * norm_mat_els[0][i];
    first_product = 0.f;
  }
  return std::max(second_product.real(), 0.f);
}

// Does conjugate(|lhs|) * |rhs| for row vectors |lhs| and |rhs|.
complex<float> ConjugateDotProduct(const ComplexMatrix<float>& lhs,
                                   const ComplexMatrix<float>& rhs) {
  RTC_CHECK_EQ(1u, lhs.num_rows());
  RTC_CHECK_EQ(1u, rhs.num_rows());
  RTC_CHECK_EQ(lhs.num_columns(), rhs.num_columns());

  const complex<float>* const* lhs_elements = lhs.elements();
  const complex<float>* const* rhs_elements = rhs.elements();

  complex<float> result = complex<float>(0.f, 0.f);
  for (size_t i = 0; i < lhs.num_columns(); ++i) {
    result += conj(lhs_elements[0][i]) * rhs_elements[0][i];
  }

  return result;
}

// Works for positive numbers only.
size_t Round(float x) {
  return static_cast<size_t>(std::floor(x + 0.5f));
}

// Calculates the sum of absolute values of a complex matrix.
float SumAbs(const ComplexMatrix<float>& mat) {
  float sum_abs = 0.f;
  const complex<float>* const* mat_els = mat.elements();
  for (size_t i = 0; i < mat.num_rows(); ++i) {
    for (size_t j = 0; j < mat.num_columns(); ++j) {
      sum_abs += std::abs(mat_els[i][j]);
    }
  }
  return sum_abs;
}

// Calculates the sum of squares of a complex matrix.
float SumSquares(const ComplexMatrix<float>& mat) {
  float sum_squares = 0.f;
  const complex<float>* const* mat_els = mat.elements();
  for (size_t i = 0; i < mat.num_rows(); ++i) {
    for (size_t j = 0; j < mat.num_columns(); ++j) {
      float abs_value = std::abs(mat_els[i][j]);
      sum_squares += abs_value * abs_value;
    }
  }
  return sum_squares;
}

// Does |out| = |in|.' * conj(|in|) for row vector |in|.
void TransposedConjugatedProduct(const ComplexMatrix<float>& in,
                                 ComplexMatrix<float>* out) {
  RTC_CHECK_EQ(1u, in.num_rows());
  RTC_CHECK_EQ(out->num_rows(), in.num_columns());
  RTC_CHECK_EQ(out->num_columns(), in.num_columns());
  const complex<float>* in_elements = in.elements()[0];
  complex<float>* const* out_elements = out->elements();
  for (size_t i = 0; i < out->num_rows(); ++i) {
    for (size_t j = 0; j < out->num_columns(); ++j) {
      out_elements[i][j] = in_elements[i] * conj(in_elements[j]);
    }
  }
}

std::vector<Point> GetCenteredArray(std::vector<Point> array_geometry) {
  for (size_t dim = 0; dim < 3; ++dim) {
    float center = 0.f;
    for (size_t i = 0; i < array_geometry.size(); ++i) {
      center += array_geometry[i].c[dim];
    }
    center /= array_geometry.size();
    for (size_t i = 0; i < array_geometry.size(); ++i) {
      array_geometry[i].c[dim] -= center;
    }
  }
  return array_geometry;
}

}  // namespace

const float NonlinearBeamformer::kHalfBeamWidthRadians = DegreesToRadians(20.f);

// static
const size_t NonlinearBeamformer::kNumFreqBins;

NonlinearBeamformer::NonlinearBeamformer(
    const std::vector<Point>& array_geometry,
    SphericalPointf target_direction)
    : num_input_channels_(array_geometry.size()),
      array_geometry_(GetCenteredArray(array_geometry)),
      array_normal_(GetArrayNormalIfExists(array_geometry)),
      min_mic_spacing_(GetMinimumSpacing(array_geometry)),
      target_angle_radians_(target_direction.azimuth()),
      away_radians_(std::min(
          static_cast<float>(M_PI),
          std::max(kMinAwayRadians,
                   kAwaySlope * static_cast<float>(M_PI) / min_mic_spacing_))) {
  WindowGenerator::KaiserBesselDerived(kKbdAlpha, kFftSize, window_);
}

void NonlinearBeamformer::Initialize(int chunk_size_ms, int sample_rate_hz) {
  chunk_length_ =
      static_cast<size_t>(sample_rate_hz / (1000.f / chunk_size_ms));
  sample_rate_hz_ = sample_rate_hz;

  high_pass_postfilter_mask_ = 1.f;
  is_target_present_ = false;
  hold_target_blocks_ = kHoldTargetSeconds * 2 * sample_rate_hz / kFftSize;
  interference_blocks_count_ = hold_target_blocks_;

  lapped_transform_.reset(new LappedTransform(num_input_channels_,
                                              1,
                                              chunk_length_,
                                              window_,
                                              kFftSize,
                                              kFftSize / 2,
                                              this));
  for (size_t i = 0; i < kNumFreqBins; ++i) {
    time_smooth_mask_[i] = 1.f;
    final_mask_[i] = 1.f;
    float freq_hz = (static_cast<float>(i) / kFftSize) * sample_rate_hz_;
    wave_numbers_[i] = 2 * M_PI * freq_hz / kSpeedOfSoundMeterSeconds;
  }

  InitLowFrequencyCorrectionRanges();
  InitDiffuseCovMats();
  AimAt(SphericalPointf(target_angle_radians_, 0.f, 1.f));
}

// These bin indexes determine the regions over which a mean is taken. This is
// applied as a constant value over the adjacent end "frequency correction"
// regions.
//
//             low_mean_start_bin_     high_mean_start_bin_
//                   v                         v              constant
// |----------------|--------|----------------|-------|----------------|
//   constant               ^                        ^
//             low_mean_end_bin_       high_mean_end_bin_
//
void NonlinearBeamformer::InitLowFrequencyCorrectionRanges() {
  low_mean_start_bin_ = Round(kLowMeanStartHz * kFftSize / sample_rate_hz_);
  low_mean_end_bin_ = Round(kLowMeanEndHz * kFftSize / sample_rate_hz_);

  RTC_DCHECK_GT(low_mean_start_bin_, 0U);
  RTC_DCHECK_LT(low_mean_start_bin_, low_mean_end_bin_);
}

void NonlinearBeamformer::InitHighFrequencyCorrectionRanges() {
  const float kAliasingFreqHz =
      kSpeedOfSoundMeterSeconds /
      (min_mic_spacing_ * (1.f + std::abs(std::cos(target_angle_radians_))));
  const float kHighMeanStartHz = std::min(0.5f *  kAliasingFreqHz,
                                          sample_rate_hz_ / 2.f);
  const float kHighMeanEndHz = std::min(0.75f *  kAliasingFreqHz,
                                        sample_rate_hz_ / 2.f);
  high_mean_start_bin_ = Round(kHighMeanStartHz * kFftSize / sample_rate_hz_);
  high_mean_end_bin_ = Round(kHighMeanEndHz * kFftSize / sample_rate_hz_);

  RTC_DCHECK_LT(low_mean_end_bin_, high_mean_end_bin_);
  RTC_DCHECK_LT(high_mean_start_bin_, high_mean_end_bin_);
  RTC_DCHECK_LT(high_mean_end_bin_, kNumFreqBins - 1);
}

void NonlinearBeamformer::InitInterfAngles() {
  interf_angles_radians_.clear();
  const Point target_direction = AzimuthToPoint(target_angle_radians_);
  const Point clockwise_interf_direction =
      AzimuthToPoint(target_angle_radians_ - away_radians_);
  if (!array_normal_ ||
      DotProduct(*array_normal_, target_direction) *
              DotProduct(*array_normal_, clockwise_interf_direction) >=
          0.f) {
    // The target and clockwise interferer are in the same half-plane defined
    // by the array.
    interf_angles_radians_.push_back(target_angle_radians_ - away_radians_);
  } else {
    // Otherwise, the interferer will begin reflecting back at the target.
    // Instead rotate it away 180 degrees.
    interf_angles_radians_.push_back(target_angle_radians_ - away_radians_ +
                                     M_PI);
  }
  const Point counterclock_interf_direction =
      AzimuthToPoint(target_angle_radians_ + away_radians_);
  if (!array_normal_ ||
      DotProduct(*array_normal_, target_direction) *
              DotProduct(*array_normal_, counterclock_interf_direction) >=
          0.f) {
    // The target and counter-clockwise interferer are in the same half-plane
    // defined by the array.
    interf_angles_radians_.push_back(target_angle_radians_ + away_radians_);
  } else {
    // Otherwise, the interferer will begin reflecting back at the target.
    // Instead rotate it away 180 degrees.
    interf_angles_radians_.push_back(target_angle_radians_ + away_radians_ -
                                     M_PI);
  }
}

void NonlinearBeamformer::InitDelaySumMasks() {
  for (size_t f_ix = 0; f_ix < kNumFreqBins; ++f_ix) {
    delay_sum_masks_[f_ix].Resize(1, num_input_channels_);
    CovarianceMatrixGenerator::PhaseAlignmentMasks(
        f_ix, kFftSize, sample_rate_hz_, kSpeedOfSoundMeterSeconds,
        array_geometry_, target_angle_radians_, &delay_sum_masks_[f_ix]);

    complex_f norm_factor = sqrt(
        ConjugateDotProduct(delay_sum_masks_[f_ix], delay_sum_masks_[f_ix]));
    delay_sum_masks_[f_ix].Scale(1.f / norm_factor);
    normalized_delay_sum_masks_[f_ix].CopyFrom(delay_sum_masks_[f_ix]);
    normalized_delay_sum_masks_[f_ix].Scale(1.f / SumAbs(
        normalized_delay_sum_masks_[f_ix]));
  }
}

void NonlinearBeamformer::InitTargetCovMats() {
  for (size_t i = 0; i < kNumFreqBins; ++i) {
    target_cov_mats_[i].Resize(num_input_channels_, num_input_channels_);
    TransposedConjugatedProduct(delay_sum_masks_[i], &target_cov_mats_[i]);
  }
}

void NonlinearBeamformer::InitDiffuseCovMats() {
  for (size_t i = 0; i < kNumFreqBins; ++i) {
    uniform_cov_mat_[i].Resize(num_input_channels_, num_input_channels_);
    CovarianceMatrixGenerator::UniformCovarianceMatrix(
        wave_numbers_[i], array_geometry_, &uniform_cov_mat_[i]);
    complex_f normalization_factor = uniform_cov_mat_[i].elements()[0][0];
    uniform_cov_mat_[i].Scale(1.f / normalization_factor);
    uniform_cov_mat_[i].Scale(1 - kBalance);
  }
}

void NonlinearBeamformer::InitInterfCovMats() {
  for (size_t i = 0; i < kNumFreqBins; ++i) {
    interf_cov_mats_[i].clear();
    for (size_t j = 0; j < interf_angles_radians_.size(); ++j) {
      interf_cov_mats_[i].push_back(std::unique_ptr<ComplexMatrixF>(
          new ComplexMatrixF(num_input_channels_, num_input_channels_)));
      ComplexMatrixF angled_cov_mat(num_input_channels_, num_input_channels_);
      CovarianceMatrixGenerator::AngledCovarianceMatrix(
          kSpeedOfSoundMeterSeconds,
          interf_angles_radians_[j],
          i,
          kFftSize,
          kNumFreqBins,
          sample_rate_hz_,
          array_geometry_,
          &angled_cov_mat);
      // Normalize matrices before averaging them.
      complex_f normalization_factor = angled_cov_mat.elements()[0][0];
      angled_cov_mat.Scale(1.f / normalization_factor);
      // Weighted average of matrices.
      angled_cov_mat.Scale(kBalance);
      interf_cov_mats_[i][j]->Add(uniform_cov_mat_[i], angled_cov_mat);
    }
  }
}

void NonlinearBeamformer::NormalizeCovMats() {
  for (size_t i = 0; i < kNumFreqBins; ++i) {
    rxiws_[i] = Norm(target_cov_mats_[i], delay_sum_masks_[i]);
    rpsiws_[i].clear();
    for (size_t j = 0; j < interf_angles_radians_.size(); ++j) {
      rpsiws_[i].push_back(Norm(*interf_cov_mats_[i][j], delay_sum_masks_[i]));
    }
  }
}

void NonlinearBeamformer::ProcessChunk(const ChannelBuffer<float>& input,
                                       ChannelBuffer<float>* output) {
  RTC_DCHECK_EQ(input.num_channels(), num_input_channels_);
  RTC_DCHECK_EQ(input.num_frames_per_band(), chunk_length_);

  float old_high_pass_mask = high_pass_postfilter_mask_;
  lapped_transform_->ProcessChunk(input.channels(0), output->channels(0));
  // Ramp up/down for smoothing. 1 mask per 10ms results in audible
  // discontinuities.
  const float ramp_increment =
      (high_pass_postfilter_mask_ - old_high_pass_mask) /
      input.num_frames_per_band();
  // Apply the smoothed high-pass mask to the first channel of each band.
  // This can be done because the effect of the linear beamformer is negligible
  // compared to the post-filter.
  for (size_t i = 1; i < input.num_bands(); ++i) {
    float smoothed_mask = old_high_pass_mask;
    for (size_t j = 0; j < input.num_frames_per_band(); ++j) {
      smoothed_mask += ramp_increment;
      output->channels(i)[0][j] = input.channels(i)[0][j] * smoothed_mask;
    }
  }
}

void NonlinearBeamformer::AimAt(const SphericalPointf& target_direction) {
  target_angle_radians_ = target_direction.azimuth();
  InitHighFrequencyCorrectionRanges();
  InitInterfAngles();
  InitDelaySumMasks();
  InitTargetCovMats();
  InitInterfCovMats();
  NormalizeCovMats();
}

bool NonlinearBeamformer::IsInBeam(const SphericalPointf& spherical_point) {
  // If more than half-beamwidth degrees away from the beam's center,
  // you are out of the beam.
  return fabs(spherical_point.azimuth() - target_angle_radians_) <
         kHalfBeamWidthRadians;
}

void NonlinearBeamformer::ProcessAudioBlock(const complex_f* const* input,
                                            size_t num_input_channels,
                                            size_t num_freq_bins,
                                            size_t num_output_channels,
                                            complex_f* const* output) {
  RTC_CHECK_EQ(kNumFreqBins, num_freq_bins);
  RTC_CHECK_EQ(num_input_channels_, num_input_channels);
  RTC_CHECK_EQ(1u, num_output_channels);

  // Calculating the post-filter masks. Note that we need two for each
  // frequency bin to account for the positive and negative interferer
  // angle.
  for (size_t i = low_mean_start_bin_; i <= high_mean_end_bin_; ++i) {
    eig_m_.CopyFromColumn(input, i, num_input_channels_);
    float eig_m_norm_factor = std::sqrt(SumSquares(eig_m_));
    if (eig_m_norm_factor != 0.f) {
      eig_m_.Scale(1.f / eig_m_norm_factor);
    }

    float rxim = Norm(target_cov_mats_[i], eig_m_);
    float ratio_rxiw_rxim = 0.f;
    if (rxim > 0.f) {
      ratio_rxiw_rxim = rxiws_[i] / rxim;
    }

    complex_f rmw = abs(ConjugateDotProduct(delay_sum_masks_[i], eig_m_));
    rmw *= rmw;
    float rmw_r = rmw.real();

    new_mask_[i] = CalculatePostfilterMask(*interf_cov_mats_[i][0],
                                           rpsiws_[i][0],
                                           ratio_rxiw_rxim,
                                           rmw_r);
    for (size_t j = 1; j < interf_angles_radians_.size(); ++j) {
      float tmp_mask = CalculatePostfilterMask(*interf_cov_mats_[i][j],
                                               rpsiws_[i][j],
                                               ratio_rxiw_rxim,
                                               rmw_r);
      if (tmp_mask < new_mask_[i]) {
        new_mask_[i] = tmp_mask;
      }
    }
  }

  ApplyMaskTimeSmoothing();
  EstimateTargetPresence();
  ApplyLowFrequencyCorrection();
  ApplyHighFrequencyCorrection();
  ApplyMaskFrequencySmoothing();
  ApplyMasks(input, output);
}

float NonlinearBeamformer::CalculatePostfilterMask(
    const ComplexMatrixF& interf_cov_mat,
    float rpsiw,
    float ratio_rxiw_rxim,
    float rmw_r) {
  float rpsim = Norm(interf_cov_mat, eig_m_);

  float ratio = 0.f;
  if (rpsim > 0.f) {
    ratio = rpsiw / rpsim;
  }

  float numerator = 1.f - kCutOffConstant;
  if (rmw_r > 0.f) {
    numerator = 1.f - std::min(kCutOffConstant, ratio / rmw_r);
  }

  float denominator = 1.f - kCutOffConstant;
  if (ratio_rxiw_rxim > 0.f) {
    denominator = 1.f - std::min(kCutOffConstant, ratio / ratio_rxiw_rxim);
  }

  return numerator / denominator;
}

void NonlinearBeamformer::ApplyMasks(const complex_f* const* input,
                                     complex_f* const* output) {
  complex_f* output_channel = output[0];
  for (size_t f_ix = 0; f_ix < kNumFreqBins; ++f_ix) {
    output_channel[f_ix] = complex_f(0.f, 0.f);

    const complex_f* delay_sum_mask_els =
        normalized_delay_sum_masks_[f_ix].elements()[0];
    for (size_t c_ix = 0; c_ix < num_input_channels_; ++c_ix) {
      output_channel[f_ix] += input[c_ix][f_ix] * delay_sum_mask_els[c_ix];
    }

    output_channel[f_ix] *= kCompensationGain * final_mask_[f_ix];
  }
}

// Smooth new_mask_ into time_smooth_mask_.
void NonlinearBeamformer::ApplyMaskTimeSmoothing() {
  for (size_t i = low_mean_start_bin_; i <= high_mean_end_bin_; ++i) {
    time_smooth_mask_[i] = kMaskTimeSmoothAlpha * new_mask_[i] +
                           (1 - kMaskTimeSmoothAlpha) * time_smooth_mask_[i];
  }
}

// Copy time_smooth_mask_ to final_mask_ and smooth over frequency.
void NonlinearBeamformer::ApplyMaskFrequencySmoothing() {
  // Smooth over frequency in both directions. The "frequency correction"
  // regions have constant value, but we enter them to smooth over the jump
  // that exists at the boundary. However, this does mean when smoothing "away"
  // from the region that we only need to use the last element.
  //
  // Upward smoothing:
  //   low_mean_start_bin_
  //         v
  // |------|------------|------|
  //       ^------------------>^
  //
  // Downward smoothing:
  //         high_mean_end_bin_
  //                    v
  // |------|------------|------|
  //  ^<------------------^
  std::copy(time_smooth_mask_, time_smooth_mask_ + kNumFreqBins, final_mask_);
  for (size_t i = low_mean_start_bin_; i < kNumFreqBins; ++i) {
    final_mask_[i] = kMaskFrequencySmoothAlpha * final_mask_[i] +
                     (1 - kMaskFrequencySmoothAlpha) * final_mask_[i - 1];
  }
  for (size_t i = high_mean_end_bin_ + 1; i > 0; --i) {
    final_mask_[i - 1] = kMaskFrequencySmoothAlpha * final_mask_[i - 1] +
                         (1 - kMaskFrequencySmoothAlpha) * final_mask_[i];
  }
}

// Apply low frequency correction to time_smooth_mask_.
void NonlinearBeamformer::ApplyLowFrequencyCorrection() {
  const float low_frequency_mask =
      MaskRangeMean(low_mean_start_bin_, low_mean_end_bin_ + 1);
  std::fill(time_smooth_mask_, time_smooth_mask_ + low_mean_start_bin_,
            low_frequency_mask);
}

// Apply high frequency correction to time_smooth_mask_. Update
// high_pass_postfilter_mask_ to use for the high frequency time-domain bands.
void NonlinearBeamformer::ApplyHighFrequencyCorrection() {
  high_pass_postfilter_mask_ =
      MaskRangeMean(high_mean_start_bin_, high_mean_end_bin_ + 1);
  std::fill(time_smooth_mask_ + high_mean_end_bin_ + 1,
            time_smooth_mask_ + kNumFreqBins, high_pass_postfilter_mask_);
}

// Compute mean over the given range of time_smooth_mask_, [first, last).
float NonlinearBeamformer::MaskRangeMean(size_t first, size_t last) {
  RTC_DCHECK_GT(last, first);
  const float sum = std::accumulate(time_smooth_mask_ + first,
                                    time_smooth_mask_ + last, 0.f);
  return sum / (last - first);
}

void NonlinearBeamformer::EstimateTargetPresence() {
  const size_t quantile = static_cast<size_t>(
      (high_mean_end_bin_ - low_mean_start_bin_) * kMaskQuantile +
      low_mean_start_bin_);
  std::nth_element(new_mask_ + low_mean_start_bin_, new_mask_ + quantile,
                   new_mask_ + high_mean_end_bin_ + 1);
  if (new_mask_[quantile] > kMaskTargetThreshold) {
    is_target_present_ = true;
    interference_blocks_count_ = 0;
  } else {
    is_target_present_ = interference_blocks_count_++ < hold_target_blocks_;
  }
}

}  // namespace webrtc
