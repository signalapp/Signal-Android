/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/scopedptrcollection.h"
#include "webrtc/base/gunit.h"

namespace rtc {

namespace {

class InstanceCounter {
 public:
  explicit InstanceCounter(int* num_instances)
      : num_instances_(num_instances) {
    ++(*num_instances_);
  }
  ~InstanceCounter() {
    --(*num_instances_);
  }

 private:
  int* num_instances_;

  RTC_DISALLOW_COPY_AND_ASSIGN(InstanceCounter);
};

}  // namespace

class ScopedPtrCollectionTest : public testing::Test {
 protected:
  ScopedPtrCollectionTest()
      : num_instances_(0),
      collection_(new ScopedPtrCollection<InstanceCounter>()) {
  }

  int num_instances_;
  std::unique_ptr<ScopedPtrCollection<InstanceCounter> > collection_;
};

TEST_F(ScopedPtrCollectionTest, PushBack) {
  EXPECT_EQ(0u, collection_->collection().size());
  EXPECT_EQ(0, num_instances_);
  const int kNum = 100;
  for (int i = 0; i < kNum; ++i) {
    collection_->PushBack(new InstanceCounter(&num_instances_));
  }
  EXPECT_EQ(static_cast<size_t>(kNum), collection_->collection().size());
  EXPECT_EQ(kNum, num_instances_);
  collection_.reset();
  EXPECT_EQ(0, num_instances_);
}

TEST_F(ScopedPtrCollectionTest, Remove) {
  InstanceCounter* ic = new InstanceCounter(&num_instances_);
  collection_->PushBack(ic);
  EXPECT_EQ(1u, collection_->collection().size());
  collection_->Remove(ic);
  EXPECT_EQ(1, num_instances_);
  collection_.reset();
  EXPECT_EQ(1, num_instances_);
  delete ic;
  EXPECT_EQ(0, num_instances_);
}


}  // namespace rtc
