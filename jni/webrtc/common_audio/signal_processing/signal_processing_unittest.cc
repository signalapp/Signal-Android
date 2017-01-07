/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>
#include <sstream>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

static const size_t kVector16Size = 9;
static const int16_t vector16[kVector16Size] = {1, -15511, 4323, 1963,
  WEBRTC_SPL_WORD16_MAX, 0, WEBRTC_SPL_WORD16_MIN + 5, -3333, 345};

class SplTest : public testing::Test {
 protected:
   SplTest() {
     WebRtcSpl_Init();
   }
   virtual ~SplTest() {
   }
};

TEST_F(SplTest, MacroTest) {
    // Macros with inputs.
    int A = 10;
    int B = 21;
    int a = -3;
    int b = WEBRTC_SPL_WORD32_MAX;

    EXPECT_EQ(10, WEBRTC_SPL_MIN(A, B));
    EXPECT_EQ(21, WEBRTC_SPL_MAX(A, B));

    EXPECT_EQ(3, WEBRTC_SPL_ABS_W16(a));
    EXPECT_EQ(3, WEBRTC_SPL_ABS_W32(a));

    EXPECT_EQ(-63, WEBRTC_SPL_MUL(a, B));
    EXPECT_EQ(-2147483645, WEBRTC_SPL_MUL(a, b));
    EXPECT_EQ(2147483651u, WEBRTC_SPL_UMUL(a, b));
    b = WEBRTC_SPL_WORD16_MAX >> 1;
    EXPECT_EQ(4294918147u, WEBRTC_SPL_UMUL_32_16(a, b));
    EXPECT_EQ(-49149, WEBRTC_SPL_MUL_16_U16(a, b));

    a = b;
    b = -3;

    EXPECT_EQ(-1, WEBRTC_SPL_MUL_16_32_RSFT16(a, b));
    EXPECT_EQ(-1, WEBRTC_SPL_MUL_16_32_RSFT15(a, b));
    EXPECT_EQ(-3, WEBRTC_SPL_MUL_16_32_RSFT14(a, b));
    EXPECT_EQ(-24, WEBRTC_SPL_MUL_16_32_RSFT11(a, b));

    EXPECT_EQ(-12288, WEBRTC_SPL_MUL_16_16_RSFT(a, b, 2));
    EXPECT_EQ(-12287, WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(a, b, 2));

    EXPECT_EQ(21, WEBRTC_SPL_SAT(a, A, B));
    EXPECT_EQ(21, WEBRTC_SPL_SAT(a, B, A));

    // Shifting with negative numbers allowed
    int shift_amount = 1;  // Workaround compiler warning using variable here.
    // Positive means left shift
    EXPECT_EQ(32766, WEBRTC_SPL_SHIFT_W32(a, shift_amount));

    // Shifting with negative numbers not allowed
    // We cannot do casting here due to signed/unsigned problem
    EXPECT_EQ(32766, WEBRTC_SPL_LSHIFT_W32(a, 1));

    EXPECT_EQ(8191u, WEBRTC_SPL_RSHIFT_U32(a, 1));

    EXPECT_EQ(1470, WEBRTC_SPL_RAND(A));

    EXPECT_EQ(-49149, WEBRTC_SPL_MUL_16_16(a, b));
    EXPECT_EQ(1073676289, WEBRTC_SPL_MUL_16_16(WEBRTC_SPL_WORD16_MAX,
                                               WEBRTC_SPL_WORD16_MAX));
    EXPECT_EQ(1073709055, WEBRTC_SPL_MUL_16_32_RSFT16(WEBRTC_SPL_WORD16_MAX,
                                                      WEBRTC_SPL_WORD32_MAX));
    EXPECT_EQ(1073741824, WEBRTC_SPL_MUL_16_32_RSFT16(WEBRTC_SPL_WORD16_MIN,
                                                      WEBRTC_SPL_WORD32_MIN));
#ifdef WEBRTC_ARCH_ARM_V7
    EXPECT_EQ(-1073741824,
              WEBRTC_SPL_MUL_16_32_RSFT16(WEBRTC_SPL_WORD16_MIN,
                                          WEBRTC_SPL_WORD32_MAX));
#else
    EXPECT_EQ(-1073741823,
              WEBRTC_SPL_MUL_16_32_RSFT16(WEBRTC_SPL_WORD16_MIN,
                                          WEBRTC_SPL_WORD32_MAX));
#endif
}

TEST_F(SplTest, InlineTest) {
    int16_t a16 = 121;
    int16_t b16 = -17;
    int32_t a32 = 111121;
    int32_t b32 = -1711;

    EXPECT_EQ(17, WebRtcSpl_GetSizeInBits(a32));

    EXPECT_EQ(0, WebRtcSpl_NormW32(0));
    EXPECT_EQ(31, WebRtcSpl_NormW32(-1));
    EXPECT_EQ(0, WebRtcSpl_NormW32(WEBRTC_SPL_WORD32_MIN));
    EXPECT_EQ(14, WebRtcSpl_NormW32(a32));

    EXPECT_EQ(0, WebRtcSpl_NormW16(0));
    EXPECT_EQ(15, WebRtcSpl_NormW16(-1));
    EXPECT_EQ(0, WebRtcSpl_NormW16(WEBRTC_SPL_WORD16_MIN));
    EXPECT_EQ(4, WebRtcSpl_NormW16(b32));
    for (int ii = 0; ii < 15; ++ii) {
      int16_t value = 1 << ii;
      EXPECT_EQ(14 - ii, WebRtcSpl_NormW16(value));
      EXPECT_EQ(15 - ii, WebRtcSpl_NormW16(-value));
    }

    EXPECT_EQ(0, WebRtcSpl_NormU32(0u));
    EXPECT_EQ(0, WebRtcSpl_NormU32(0xffffffff));
    EXPECT_EQ(15, WebRtcSpl_NormU32(static_cast<uint32_t>(a32)));

    EXPECT_EQ(104, WebRtcSpl_AddSatW16(a16, b16));
    EXPECT_EQ(138, WebRtcSpl_SubSatW16(a16, b16));
}

TEST_F(SplTest, AddSubSatW32) {
  static constexpr int32_t kAddSubArgs[] = {
      INT32_MIN, INT32_MIN + 1, -3,       -2, -1, 0, 1, -1, 2,
      3,         INT32_MAX - 1, INT32_MAX};
  for (int32_t a : kAddSubArgs) {
    for (int32_t b : kAddSubArgs) {
      const int64_t sum = std::max<int64_t>(
          INT32_MIN, std::min<int64_t>(INT32_MAX, static_cast<int64_t>(a) + b));
      const int64_t diff = std::max<int64_t>(
          INT32_MIN, std::min<int64_t>(INT32_MAX, static_cast<int64_t>(a) - b));
      std::ostringstream ss;
      ss << a << " +/- " << b << ": sum " << sum << ", diff " << diff;
      SCOPED_TRACE(ss.str());
      EXPECT_EQ(sum, WebRtcSpl_AddSatW32(a, b));
      EXPECT_EQ(diff, WebRtcSpl_SubSatW32(a, b));
    }
  }
}

TEST_F(SplTest, CountLeadingZeros32) {
  EXPECT_EQ(32, WebRtcSpl_CountLeadingZeros32(0));
  EXPECT_EQ(32, WebRtcSpl_CountLeadingZeros32_NotBuiltin(0));
  for (int i = 0; i < 32; ++i) {
    const uint32_t single_one = uint32_t{1} << i;
    const uint32_t all_ones = 2 * single_one - 1;
    EXPECT_EQ(31 - i, WebRtcSpl_CountLeadingZeros32(single_one));
    EXPECT_EQ(31 - i, WebRtcSpl_CountLeadingZeros32_NotBuiltin(single_one));
    EXPECT_EQ(31 - i, WebRtcSpl_CountLeadingZeros32(all_ones));
    EXPECT_EQ(31 - i, WebRtcSpl_CountLeadingZeros32_NotBuiltin(all_ones));
  }
}

TEST_F(SplTest, CountLeadingZeros64) {
  EXPECT_EQ(64, WebRtcSpl_CountLeadingZeros64(0));
  EXPECT_EQ(64, WebRtcSpl_CountLeadingZeros64_NotBuiltin(0));
  for (int i = 0; i < 64; ++i) {
    const uint64_t single_one = uint64_t{1} << i;
    const uint64_t all_ones = 2 * single_one - 1;
    EXPECT_EQ(63 - i, WebRtcSpl_CountLeadingZeros64(single_one));
    EXPECT_EQ(63 - i, WebRtcSpl_CountLeadingZeros64_NotBuiltin(single_one));
    EXPECT_EQ(63 - i, WebRtcSpl_CountLeadingZeros64(all_ones));
    EXPECT_EQ(63 - i, WebRtcSpl_CountLeadingZeros64_NotBuiltin(all_ones));
  }
}

TEST_F(SplTest, MathOperationsTest) {
    int A = 1134567892;
    int32_t num = 117;
    int32_t den = -5;
    uint16_t denU = 5;
    EXPECT_EQ(33700, WebRtcSpl_Sqrt(A));
    EXPECT_EQ(33683, WebRtcSpl_SqrtFloor(A));


    EXPECT_EQ(-91772805, WebRtcSpl_DivResultInQ31(den, num));
    EXPECT_EQ(-23, WebRtcSpl_DivW32W16ResW16(num, (int16_t)den));
    EXPECT_EQ(-23, WebRtcSpl_DivW32W16(num, (int16_t)den));
    EXPECT_EQ(23u, WebRtcSpl_DivU32U16(num, denU));
    EXPECT_EQ(0, WebRtcSpl_DivW32HiLow(128, 0, 256));
}

TEST_F(SplTest, BasicArrayOperationsTest) {
    const size_t kVectorSize = 4;
    int B[] = {4, 12, 133, 1100};
    int16_t b16[kVectorSize];
    int32_t b32[kVectorSize];

    int16_t bTmp16[kVectorSize];
    int32_t bTmp32[kVectorSize];

    WebRtcSpl_MemSetW16(b16, 3, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(3, b16[kk]);
    }
    WebRtcSpl_ZerosArrayW16(b16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(0, b16[kk]);
    }
    WebRtcSpl_MemSetW32(b32, 3, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(3, b32[kk]);
    }
    WebRtcSpl_ZerosArrayW32(b32, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(0, b32[kk]);
    }
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        bTmp16[kk] = (int16_t)kk;
        bTmp32[kk] = (int32_t)kk;
    }
    WEBRTC_SPL_MEMCPY_W16(b16, bTmp16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(b16[kk], bTmp16[kk]);
    }
//    WEBRTC_SPL_MEMCPY_W32(b32, bTmp32, kVectorSize);
//    for (int kk = 0; kk < kVectorSize; ++kk) {
//        EXPECT_EQ(b32[kk], bTmp32[kk]);
//    }
    WebRtcSpl_CopyFromEndW16(b16, kVectorSize, 2, bTmp16);
    for (size_t kk = 0; kk < 2; ++kk) {
        EXPECT_EQ(static_cast<int16_t>(kk+2), bTmp16[kk]);
    }

    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        b32[kk] = B[kk];
        b16[kk] = (int16_t)B[kk];
    }
    WebRtcSpl_VectorBitShiftW32ToW16(bTmp16, kVectorSize, b32, 1);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((B[kk]>>1), bTmp16[kk]);
    }
    WebRtcSpl_VectorBitShiftW16(bTmp16, kVectorSize, b16, 1);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((B[kk]>>1), bTmp16[kk]);
    }
    WebRtcSpl_VectorBitShiftW32(bTmp32, kVectorSize, b32, 1);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((B[kk]>>1), bTmp32[kk]);
    }

    WebRtcSpl_MemCpyReversedOrder(&bTmp16[3], b16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(b16[3-kk], bTmp16[kk]);
    }
}

TEST_F(SplTest, MinMaxOperationsTest) {
  const size_t kVectorSize = 17;

  // Vectors to test the cases where minimum values have to be caught
  // outside of the unrolled loops in ARM-Neon.
  int16_t vector16[kVectorSize] = {-1, 7485, 0, 3333,
      -18283, 0, 12334, -29871, 988, -3333,
      345, -456, 222, 999,  888, 8774, WEBRTC_SPL_WORD16_MIN};
  int32_t vector32[kVectorSize] = {-1, 0, 283211, 3333,
      8712345, 0, -3333, 89345, -374585456, 222, 999, 122345334,
      -12389756, -987329871, 888, -2, WEBRTC_SPL_WORD32_MIN};

  EXPECT_EQ(WEBRTC_SPL_WORD16_MIN,
            WebRtcSpl_MinValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MIN,
            WebRtcSpl_MinValueW32(vector32, kVectorSize));
  EXPECT_EQ(kVectorSize - 1, WebRtcSpl_MinIndexW16(vector16, kVectorSize));
  EXPECT_EQ(kVectorSize - 1, WebRtcSpl_MinIndexW32(vector32, kVectorSize));

  // Test the cases where maximum values have to be caught
  // outside of the unrolled loops in ARM-Neon.
  vector16[kVectorSize - 1] = WEBRTC_SPL_WORD16_MAX;
  vector32[kVectorSize - 1] = WEBRTC_SPL_WORD32_MAX;

  EXPECT_EQ(WEBRTC_SPL_WORD16_MAX,
            WebRtcSpl_MaxAbsValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD16_MAX,
            WebRtcSpl_MaxValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MAX,
            WebRtcSpl_MaxAbsValueW32(vector32, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MAX,
            WebRtcSpl_MaxValueW32(vector32, kVectorSize));
  EXPECT_EQ(kVectorSize - 1, WebRtcSpl_MaxAbsIndexW16(vector16, kVectorSize));
  EXPECT_EQ(kVectorSize - 1, WebRtcSpl_MaxIndexW16(vector16, kVectorSize));
  EXPECT_EQ(kVectorSize - 1, WebRtcSpl_MaxIndexW32(vector32, kVectorSize));

  // Test the cases where multiple maximum and minimum values are present.
  vector16[1] = WEBRTC_SPL_WORD16_MAX;
  vector16[6] = WEBRTC_SPL_WORD16_MIN;
  vector16[11] = WEBRTC_SPL_WORD16_MIN;
  vector32[1] = WEBRTC_SPL_WORD32_MAX;
  vector32[6] = WEBRTC_SPL_WORD32_MIN;
  vector32[11] = WEBRTC_SPL_WORD32_MIN;

  EXPECT_EQ(WEBRTC_SPL_WORD16_MAX,
            WebRtcSpl_MaxAbsValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD16_MAX,
            WebRtcSpl_MaxValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD16_MIN,
            WebRtcSpl_MinValueW16(vector16, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MAX,
            WebRtcSpl_MaxAbsValueW32(vector32, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MAX,
            WebRtcSpl_MaxValueW32(vector32, kVectorSize));
  EXPECT_EQ(WEBRTC_SPL_WORD32_MIN,
            WebRtcSpl_MinValueW32(vector32, kVectorSize));
  EXPECT_EQ(6u, WebRtcSpl_MaxAbsIndexW16(vector16, kVectorSize));
  EXPECT_EQ(1u, WebRtcSpl_MaxIndexW16(vector16, kVectorSize));
  EXPECT_EQ(1u, WebRtcSpl_MaxIndexW32(vector32, kVectorSize));
  EXPECT_EQ(6u, WebRtcSpl_MinIndexW16(vector16, kVectorSize));
  EXPECT_EQ(6u, WebRtcSpl_MinIndexW32(vector32, kVectorSize));
}

TEST_F(SplTest, VectorOperationsTest) {
    const size_t kVectorSize = 4;
    int B[] = {4, 12, 133, 1100};
    int16_t a16[kVectorSize];
    int16_t b16[kVectorSize];
    int16_t bTmp16[kVectorSize];

    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        a16[kk] = B[kk];
        b16[kk] = B[kk];
    }

    WebRtcSpl_AffineTransformVector(bTmp16, b16, 3, 7, 2, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((B[kk]*3+7)>>2, bTmp16[kk]);
    }
    WebRtcSpl_ScaleAndAddVectorsWithRound(b16, 3, b16, 2, 2, bTmp16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((B[kk]*3+B[kk]*2+2)>>2, bTmp16[kk]);
    }

    WebRtcSpl_AddAffineVectorToVector(bTmp16, b16, 3, 7, 2, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(((B[kk]*3+B[kk]*2+2)>>2)+((b16[kk]*3+7)>>2), bTmp16[kk]);
    }

    WebRtcSpl_ScaleVector(b16, bTmp16, 13, kVectorSize, 2);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((b16[kk]*13)>>2, bTmp16[kk]);
    }
    WebRtcSpl_ScaleVectorWithSat(b16, bTmp16, 13, kVectorSize, 2);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((b16[kk]*13)>>2, bTmp16[kk]);
    }
    WebRtcSpl_ScaleAndAddVectors(a16, 13, 2, b16, 7, 2, bTmp16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(((a16[kk]*13)>>2)+((b16[kk]*7)>>2), bTmp16[kk]);
    }

    WebRtcSpl_AddVectorsAndShift(bTmp16, a16, b16, kVectorSize, 2);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(B[kk] >> 1, bTmp16[kk]);
    }
    WebRtcSpl_ReverseOrderMultArrayElements(bTmp16, a16, &b16[3], kVectorSize, 2);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((a16[kk]*b16[3-kk])>>2, bTmp16[kk]);
    }
    WebRtcSpl_ElementwiseVectorMult(bTmp16, a16, b16, kVectorSize, 6);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ((a16[kk]*b16[kk])>>6, bTmp16[kk]);
    }

    WebRtcSpl_SqrtOfOneMinusXSquared(b16, kVectorSize, bTmp16);
    for (size_t kk = 0; kk < kVectorSize - 1; ++kk) {
        EXPECT_EQ(32767, bTmp16[kk]);
    }
    EXPECT_EQ(32749, bTmp16[kVectorSize - 1]);

    EXPECT_EQ(0, WebRtcSpl_GetScalingSquare(b16, kVectorSize, 1));
}

TEST_F(SplTest, EstimatorsTest) {
  const size_t kOrder = 2;
  const int32_t unstable_filter[] = { 4, 12, 133, 1100 };
  const int32_t stable_filter[] = { 1100, 133, 12, 4 };
  int16_t lpc[kOrder + 2] = { 0 };
  int16_t refl[kOrder + 2] = { 0 };
  int16_t lpc_result[] = { 4096, -497, 15, 0 };
  int16_t refl_result[] = { -3962, 123, 0, 0 };

  EXPECT_EQ(0, WebRtcSpl_LevinsonDurbin(unstable_filter, lpc, refl, kOrder));
  EXPECT_EQ(1, WebRtcSpl_LevinsonDurbin(stable_filter, lpc, refl, kOrder));
  for (size_t i = 0; i < kOrder + 2; ++i) {
    EXPECT_EQ(lpc_result[i], lpc[i]);
    EXPECT_EQ(refl_result[i], refl[i]);
  }
}

TEST_F(SplTest, FilterTest) {
    const size_t kVectorSize = 4;
    const size_t kFilterOrder = 3;
    int16_t A[] = {1, 2, 33, 100};
    int16_t A5[] = {1, 2, 33, 100, -5};
    int16_t B[] = {4, 12, 133, 110};
    int16_t data_in[kVectorSize];
    int16_t data_out[kVectorSize];
    int16_t bTmp16Low[kVectorSize];
    int16_t bState[kVectorSize];
    int16_t bStateLow[kVectorSize];

    WebRtcSpl_ZerosArrayW16(bState, kVectorSize);
    WebRtcSpl_ZerosArrayW16(bStateLow, kVectorSize);

    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        data_in[kk] = A[kk];
        data_out[kk] = 0;
    }

    // MA filters.
    // Note that the input data has |kFilterOrder| states before the actual
    // data (one sample).
    WebRtcSpl_FilterMAFastQ12(&data_in[kFilterOrder], data_out, B,
                              kFilterOrder + 1, 1);
    EXPECT_EQ(0, data_out[0]);
    // AR filters.
    // Note that the output data has |kFilterOrder| states before the actual
    // data (one sample).
    WebRtcSpl_FilterARFastQ12(data_in, &data_out[kFilterOrder], A,
                              kFilterOrder + 1, 1);
    EXPECT_EQ(0, data_out[kFilterOrder]);

    EXPECT_EQ(kVectorSize, WebRtcSpl_FilterAR(A5,
                                              5,
                                              data_in,
                                              kVectorSize,
                                              bState,
                                              kVectorSize,
                                              bStateLow,
                                              kVectorSize,
                                              data_out,
                                              bTmp16Low,
                                              kVectorSize));
}

TEST_F(SplTest, RandTest) {
    const int kVectorSize = 4;
    int16_t BU[] = {3653, 12446, 8525, 30691};
    int16_t b16[kVectorSize];
    uint32_t bSeed = 100000;

    EXPECT_EQ(7086, WebRtcSpl_RandU(&bSeed));
    EXPECT_EQ(31565, WebRtcSpl_RandU(&bSeed));
    EXPECT_EQ(-9786, WebRtcSpl_RandN(&bSeed));
    EXPECT_EQ(kVectorSize, WebRtcSpl_RandUArray(b16, kVectorSize, &bSeed));
    for (int kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(BU[kk], b16[kk]);
    }
}

TEST_F(SplTest, DotProductWithScaleTest) {
  EXPECT_EQ(605362796, WebRtcSpl_DotProductWithScale(vector16,
      vector16, kVector16Size, 2));
}

TEST_F(SplTest, CrossCorrelationTest) {
  // Note the function arguments relation specificed by API.
  const size_t kCrossCorrelationDimension = 3;
  const int kShift = 2;
  const int kStep = 1;
  const size_t kSeqDimension = 6;

  const int16_t kVector16[kVector16Size] = {1, 4323, 1963,
    WEBRTC_SPL_WORD16_MAX, WEBRTC_SPL_WORD16_MIN + 5, -3333, -876, 8483, 142};
  int32_t vector32[kCrossCorrelationDimension] = {0};

  WebRtcSpl_CrossCorrelation(vector32, vector16, kVector16, kSeqDimension,
                             kCrossCorrelationDimension, kShift, kStep);

  // WebRtcSpl_CrossCorrelationC() and WebRtcSpl_CrossCorrelationNeon()
  // are not bit-exact.
  const int32_t kExpected[kCrossCorrelationDimension] =
      {-266947903, -15579555, -171282001};
  const int32_t* expected = kExpected;
#if !defined(MIPS32_LE)
  const int32_t kExpectedNeon[kCrossCorrelationDimension] =
      {-266947901, -15579553, -171281999};
  if (WebRtcSpl_CrossCorrelation != WebRtcSpl_CrossCorrelationC) {
    expected = kExpectedNeon;
  }
#endif
  for (size_t i = 0; i < kCrossCorrelationDimension; ++i) {
    EXPECT_EQ(expected[i], vector32[i]);
  }
}

TEST_F(SplTest, AutoCorrelationTest) {
  int scale = 0;
  int32_t vector32[kVector16Size];
  const int32_t expected[kVector16Size] = {302681398, 14223410, -121705063,
    -85221647, -17104971, 61806945, 6644603, -669329, 43};

  EXPECT_EQ(kVector16Size,
            WebRtcSpl_AutoCorrelation(vector16, kVector16Size,
                                      kVector16Size - 1, vector32, &scale));
  EXPECT_EQ(3, scale);
  for (size_t i = 0; i < kVector16Size; ++i) {
    EXPECT_EQ(expected[i], vector32[i]);
  }
}

TEST_F(SplTest, SignalProcessingTest) {
    const size_t kVectorSize = 4;
    int A[] = {1, 2, 33, 100};
    const int16_t kHanning[4] = { 2399, 8192, 13985, 16384 };
    int16_t b16[kVectorSize];

    int16_t bTmp16[kVectorSize];

    int bScale = 0;

    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        b16[kk] = A[kk];
    }

    // TODO(bjornv): Activate the Reflection Coefficient tests when refactoring.
//    WebRtcSpl_ReflCoefToLpc(b16, kVectorSize, bTmp16);
////    for (int kk = 0; kk < kVectorSize; ++kk) {
////        EXPECT_EQ(aTmp16[kk], bTmp16[kk]);
////    }
//    WebRtcSpl_LpcToReflCoef(bTmp16, kVectorSize, b16);
////    for (int kk = 0; kk < kVectorSize; ++kk) {
////        EXPECT_EQ(a16[kk], b16[kk]);
////    }
//    WebRtcSpl_AutoCorrToReflCoef(b32, kVectorSize, bTmp16);
////    for (int kk = 0; kk < kVectorSize; ++kk) {
////        EXPECT_EQ(aTmp16[kk], bTmp16[kk]);
////    }

    WebRtcSpl_GetHanningWindow(bTmp16, kVectorSize);
    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        EXPECT_EQ(kHanning[kk], bTmp16[kk]);
    }

    for (size_t kk = 0; kk < kVectorSize; ++kk) {
        b16[kk] = A[kk];
    }
    EXPECT_EQ(11094 , WebRtcSpl_Energy(b16, kVectorSize, &bScale));
    EXPECT_EQ(0, bScale);
}

TEST_F(SplTest, FFTTest) {
    int16_t B[] = {1, 2, 33, 100,
            2, 3, 34, 101,
            3, 4, 35, 102,
            4, 5, 36, 103};

    EXPECT_EQ(0, WebRtcSpl_ComplexFFT(B, 3, 1));
//    for (int kk = 0; kk < 16; ++kk) {
//        EXPECT_EQ(A[kk], B[kk]);
//    }
    EXPECT_EQ(0, WebRtcSpl_ComplexIFFT(B, 3, 1));
//    for (int kk = 0; kk < 16; ++kk) {
//        EXPECT_EQ(A[kk], B[kk]);
//    }
    WebRtcSpl_ComplexBitReverse(B, 3);
    for (int kk = 0; kk < 16; ++kk) {
        //EXPECT_EQ(A[kk], B[kk]);
    }
}

TEST_F(SplTest, Resample48WithSaturationTest) {
  // The test resamples 3*kBlockSize number of samples to 2*kBlockSize number
  // of samples.
  const size_t kBlockSize = 16;

  // Saturated input vector of 48 samples.
  const int32_t kVectorSaturated[3 * kBlockSize + 7] = {
     -32768, -32768, -32768, -32768, -32768, -32768, -32768, -32768,
     -32768, -32768, -32768, -32768, -32768, -32768, -32768, -32768,
     -32768, -32768, -32768, -32768, -32768, -32768, -32768, -32768,
     32767, 32767, 32767, 32767, 32767, 32767, 32767, 32767,
     32767, 32767, 32767, 32767, 32767, 32767, 32767, 32767,
     32767, 32767, 32767, 32767, 32767, 32767, 32767, 32767,
     32767, 32767, 32767, 32767, 32767, 32767, 32767
  };

  // All values in |out_vector| should be |kRefValue32kHz|.
  const int32_t kRefValue32kHz1 = -1077493760;
  const int32_t kRefValue32kHz2 = 1077493645;

  // After bit shift with saturation, |out_vector_w16| is saturated.

  const int16_t kRefValue16kHz1 = -32768;
  const int16_t kRefValue16kHz2 = 32767;
  // Vector for storing output.
  int32_t out_vector[2 * kBlockSize];
  int16_t out_vector_w16[2 * kBlockSize];

  WebRtcSpl_Resample48khzTo32khz(kVectorSaturated, out_vector, kBlockSize);
  WebRtcSpl_VectorBitShiftW32ToW16(out_vector_w16, 2 * kBlockSize, out_vector,
                                   15);

  // Comparing output values against references. The values at position
  // 12-15 are skipped to account for the filter lag.
  for (size_t i = 0; i < 12; ++i) {
    EXPECT_EQ(kRefValue32kHz1, out_vector[i]);
    EXPECT_EQ(kRefValue16kHz1, out_vector_w16[i]);
  }
  for (size_t i = 16; i < 2 * kBlockSize; ++i) {
    EXPECT_EQ(kRefValue32kHz2, out_vector[i]);
    EXPECT_EQ(kRefValue16kHz2, out_vector_w16[i]);
  }
}
