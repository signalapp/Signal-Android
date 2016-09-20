/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <limits>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/wav_header.h"

namespace webrtc {

// Doesn't take ownership of the buffer.
class ReadableWavBuffer : public ReadableWav {
 public:
  ReadableWavBuffer(const uint8_t* buf, size_t size)
      : buf_(buf),
        size_(size),
        pos_(0),
        buf_exhausted_(false),
        check_read_size_(true) {}
  ReadableWavBuffer(const uint8_t* buf, size_t size, bool check_read_size)
      : buf_(buf),
        size_(size),
        pos_(0),
        buf_exhausted_(false),
        check_read_size_(check_read_size) {}

  virtual ~ReadableWavBuffer() {
    // Verify the entire buffer has been read.
    if (check_read_size_)
      EXPECT_EQ(size_, pos_);
  }

  virtual size_t Read(void* buf, size_t num_bytes) {
    // Verify we don't try to read outside of a properly sized header.
    if (size_ >= kWavHeaderSize)
      EXPECT_GE(size_, pos_ + num_bytes);
    EXPECT_FALSE(buf_exhausted_);

    const size_t bytes_remaining = size_ - pos_;
    if (num_bytes > bytes_remaining) {
      // The caller is signalled about an exhausted buffer when we return fewer
      // bytes than requested. There should not be another read attempt after
      // this point.
      buf_exhausted_ = true;
      num_bytes = bytes_remaining;
    }
    memcpy(buf, &buf_[pos_], num_bytes);
    pos_ += num_bytes;
    return num_bytes;
  }

 private:
  const uint8_t* buf_;
  const size_t size_;
  size_t pos_;
  bool buf_exhausted_;
  const bool check_read_size_;
};

// Try various choices of WAV header parameters, and make sure that the good
// ones are accepted and the bad ones rejected.
TEST(WavHeaderTest, CheckWavParameters) {
  // Try some really stupid values for one parameter at a time.
  EXPECT_TRUE(CheckWavParameters(1, 8000, kWavFormatPcm, 1, 0));
  EXPECT_FALSE(CheckWavParameters(0, 8000, kWavFormatPcm, 1, 0));
  EXPECT_FALSE(CheckWavParameters(0x10000, 8000, kWavFormatPcm, 1, 0));
  EXPECT_FALSE(CheckWavParameters(1, 0, kWavFormatPcm, 1, 0));
  EXPECT_FALSE(CheckWavParameters(1, 8000, WavFormat(0), 1, 0));
  EXPECT_FALSE(CheckWavParameters(1, 8000, kWavFormatPcm, 0, 0));

  // Try invalid format/bytes-per-sample combinations.
  EXPECT_TRUE(CheckWavParameters(1, 8000, kWavFormatPcm, 2, 0));
  EXPECT_FALSE(CheckWavParameters(1, 8000, kWavFormatPcm, 4, 0));
  EXPECT_FALSE(CheckWavParameters(1, 8000, kWavFormatALaw, 2, 0));
  EXPECT_FALSE(CheckWavParameters(1, 8000, kWavFormatMuLaw, 2, 0));

  // Too large values.
  EXPECT_FALSE(CheckWavParameters(1 << 20, 1 << 20, kWavFormatPcm, 1, 0));
  EXPECT_FALSE(CheckWavParameters(
      1, 8000, kWavFormatPcm, 1, std::numeric_limits<uint32_t>::max()));

  // Not the same number of samples for each channel.
  EXPECT_FALSE(CheckWavParameters(3, 8000, kWavFormatPcm, 1, 5));
}

TEST(WavHeaderTest, ReadWavHeaderWithErrors) {
  size_t num_channels = 0;
  int sample_rate = 0;
  WavFormat format = kWavFormatPcm;
  size_t bytes_per_sample = 0;
  size_t num_samples = 0;

  // Test a few ways the header can be invalid. We start with the valid header
  // used in WriteAndReadWavHeader, and invalidate one field per test. The
  // invalid field is indicated in the array name, and in the comments with
  // *BAD*.
  {
    static const uint8_t kBadRiffID[] = {
      'R', 'i', 'f', 'f',  // *BAD*
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      16, 0, 0, 0,  // size of fmt block - 8: 24 - 8
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
      17, 0,  // block align: NumChannels * BytesPerSample
      8, 0,  // bits per sample: 1 * 8
      'd', 'a', 't', 'a',
      0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    };
    ReadableWavBuffer r(kBadRiffID, sizeof(kBadRiffID));
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kBadBitsPerSample[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      16, 0, 0, 0,  // size of fmt block - 8: 24 - 8
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
      17, 0,  // block align: NumChannels * BytesPerSample
      1, 0,  // bits per sample: *BAD*
      'd', 'a', 't', 'a',
      0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    };
    ReadableWavBuffer r(kBadBitsPerSample, sizeof(kBadBitsPerSample));
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kBadByteRate[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      16, 0, 0, 0,  // size of fmt block - 8: 24 - 8
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0x00, 0x33, 0x03, 0,  // byte rate: *BAD*
      17, 0,  // block align: NumChannels * BytesPerSample
      8, 0,  // bits per sample: 1 * 8
      'd', 'a', 't', 'a',
      0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    };
    ReadableWavBuffer r(kBadByteRate, sizeof(kBadByteRate));
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kBadFmtHeaderSize[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      17, 0, 0, 0,  // size of fmt block *BAD*. Only 16 and 18 permitted.
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
      17, 0,  // block align: NumChannels * BytesPerSample
      8, 0,  // bits per sample: 1 * 8
      0,  // extra (though invalid) header byte
      'd', 'a', 't', 'a',
      0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    };
    ReadableWavBuffer r(kBadFmtHeaderSize, sizeof(kBadFmtHeaderSize), false);
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kNonZeroExtensionField[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      18, 0, 0, 0,  // size of fmt block - 8: 24 - 8
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
      17, 0,  // block align: NumChannels * BytesPerSample
      8, 0,  // bits per sample: 1 * 8
      1, 0,  // non-zero extension field *BAD*
      'd', 'a', 't', 'a',
      0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    };
    ReadableWavBuffer r(kNonZeroExtensionField, sizeof(kNonZeroExtensionField),
                        false);
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kMissingDataChunk[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
      'f', 'm', 't', ' ',
      16, 0, 0, 0,  // size of fmt block - 8: 24 - 8
      6, 0,  // format: A-law (6)
      17, 0,  // channels: 17
      0x39, 0x30, 0, 0,  // sample rate: 12345
      0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
      17, 0,  // block align: NumChannels * BytesPerSample
      8, 0,  // bits per sample: 1 * 8
    };
    ReadableWavBuffer r(kMissingDataChunk, sizeof(kMissingDataChunk));
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
  {
    static const uint8_t kMissingFmtAndDataChunks[] = {
      'R', 'I', 'F', 'F',
      0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
      'W', 'A', 'V', 'E',
    };
    ReadableWavBuffer r(kMissingFmtAndDataChunks,
                        sizeof(kMissingFmtAndDataChunks));
    EXPECT_FALSE(
        ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                      &bytes_per_sample, &num_samples));
  }
}

// Try writing and reading a valid WAV header and make sure it looks OK.
TEST(WavHeaderTest, WriteAndReadWavHeader) {
  static const int kSize = 4 + kWavHeaderSize + 4;
  uint8_t buf[kSize];
  memset(buf, 0xa4, sizeof(buf));
  WriteWavHeader(buf + 4, 17, 12345, kWavFormatALaw, 1, 123457689);
  static const uint8_t kExpectedBuf[] = {
    0xa4, 0xa4, 0xa4, 0xa4,  // untouched bytes before header
    'R', 'I', 'F', 'F',
    0xbd, 0xd0, 0x5b, 0x07,  // size of whole file - 8: 123457689 + 44 - 8
    'W', 'A', 'V', 'E',
    'f', 'm', 't', ' ',
    16, 0, 0, 0,  // size of fmt block - 8: 24 - 8
    6, 0,  // format: A-law (6)
    17, 0,  // channels: 17
    0x39, 0x30, 0, 0,  // sample rate: 12345
    0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
    17, 0,  // block align: NumChannels * BytesPerSample
    8, 0,  // bits per sample: 1 * 8
    'd', 'a', 't', 'a',
    0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
    0xa4, 0xa4, 0xa4, 0xa4,  // untouched bytes after header
  };
  static_assert(sizeof(kExpectedBuf) == kSize, "buffer size");
  EXPECT_EQ(0, memcmp(kExpectedBuf, buf, kSize));

  size_t num_channels = 0;
  int sample_rate = 0;
  WavFormat format = kWavFormatPcm;
  size_t bytes_per_sample = 0;
  size_t num_samples = 0;
  ReadableWavBuffer r(buf + 4, sizeof(buf) - 8);
  EXPECT_TRUE(
      ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                    &bytes_per_sample, &num_samples));
  EXPECT_EQ(17u, num_channels);
  EXPECT_EQ(12345, sample_rate);
  EXPECT_EQ(kWavFormatALaw, format);
  EXPECT_EQ(1u, bytes_per_sample);
  EXPECT_EQ(123457689u, num_samples);
}

// Try reading an atypical but valid WAV header and make sure it's parsed OK.
TEST(WavHeaderTest, ReadAtypicalWavHeader) {
  static const uint8_t kBuf[] = {
    'R', 'I', 'F', 'F',
    0x3d, 0xd1, 0x5b, 0x07,  // size of whole file - 8 + an extra 128 bytes of
                             // "metadata": 123457689 + 44 - 8 + 128. (atypical)
    'W', 'A', 'V', 'E',
    'f', 'm', 't', ' ',
    18, 0, 0, 0,  // size of fmt block (with an atypical extension size field)
    6, 0,  // format: A-law (6)
    17, 0,  // channels: 17
    0x39, 0x30, 0, 0,  // sample rate: 12345
    0xc9, 0x33, 0x03, 0,  // byte rate: 1 * 17 * 12345
    17, 0,  // block align: NumChannels * BytesPerSample
    8, 0,  // bits per sample: 1 * 8
    0, 0,  // zero extension size field (atypical)
    'd', 'a', 't', 'a',
    0x99, 0xd0, 0x5b, 0x07,  // size of payload: 123457689
  };

  size_t num_channels = 0;
  int sample_rate = 0;
  WavFormat format = kWavFormatPcm;
  size_t bytes_per_sample = 0;
  size_t num_samples = 0;
  ReadableWavBuffer r(kBuf, sizeof(kBuf));
  EXPECT_TRUE(
      ReadWavHeader(&r, &num_channels, &sample_rate, &format,
                    &bytes_per_sample, &num_samples));
  EXPECT_EQ(17u, num_channels);
  EXPECT_EQ(12345, sample_rate);
  EXPECT_EQ(kWavFormatALaw, format);
  EXPECT_EQ(1u, bytes_per_sample);
  EXPECT_EQ(123457689u, num_samples);
}

}  // namespace webrtc
