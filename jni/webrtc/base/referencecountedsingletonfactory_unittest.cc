/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/referencecountedsingletonfactory.h"

namespace rtc {

class MyExistenceWatcher {
 public:
  MyExistenceWatcher() { create_called_ = true; }
  ~MyExistenceWatcher() { delete_called_ = true; }

  static bool create_called_;
  static bool delete_called_;
};

bool MyExistenceWatcher::create_called_ = false;
bool MyExistenceWatcher::delete_called_ = false;

class TestReferenceCountedSingletonFactory :
    public ReferenceCountedSingletonFactory<MyExistenceWatcher> {
 protected:
  virtual bool SetupInstance() {
    instance_.reset(new MyExistenceWatcher());
    return true;
  }

  virtual void CleanupInstance() {
    instance_.reset();
  }
};

static void DoCreateAndGoOutOfScope(
    ReferenceCountedSingletonFactory<MyExistenceWatcher> *factory) {
  rcsf_ptr<MyExistenceWatcher> ptr(factory);
  ptr.get();
  // and now ptr should go out of scope.
}

TEST(ReferenceCountedSingletonFactory, ZeroReferenceCountCausesDeletion) {
  TestReferenceCountedSingletonFactory factory;
  MyExistenceWatcher::delete_called_ = false;
  DoCreateAndGoOutOfScope(&factory);
  EXPECT_TRUE(MyExistenceWatcher::delete_called_);
}

TEST(ReferenceCountedSingletonFactory, NonZeroReferenceCountDoesNotDelete) {
  TestReferenceCountedSingletonFactory factory;
  rcsf_ptr<MyExistenceWatcher> ptr(&factory);
  ptr.get();
  MyExistenceWatcher::delete_called_ = false;
  DoCreateAndGoOutOfScope(&factory);
  EXPECT_FALSE(MyExistenceWatcher::delete_called_);
}

TEST(ReferenceCountedSingletonFactory, ReturnedPointersReferToSameThing) {
  TestReferenceCountedSingletonFactory factory;
  rcsf_ptr<MyExistenceWatcher> one(&factory), two(&factory);

  EXPECT_EQ(one.get(), two.get());
}

TEST(ReferenceCountedSingletonFactory, Release) {
  TestReferenceCountedSingletonFactory factory;

  rcsf_ptr<MyExistenceWatcher> one(&factory);
  one.get();

  MyExistenceWatcher::delete_called_ = false;
  one.release();
  EXPECT_TRUE(MyExistenceWatcher::delete_called_);
}

TEST(ReferenceCountedSingletonFactory, GetWithoutRelease) {
  TestReferenceCountedSingletonFactory factory;
  rcsf_ptr<MyExistenceWatcher> one(&factory);
  one.get();

  MyExistenceWatcher::create_called_ = false;
  one.get();
  EXPECT_FALSE(MyExistenceWatcher::create_called_);
}

TEST(ReferenceCountedSingletonFactory, GetAfterRelease) {
  TestReferenceCountedSingletonFactory factory;
  rcsf_ptr<MyExistenceWatcher> one(&factory);

  MyExistenceWatcher::create_called_ = false;
  one.release();
  one.get();
  EXPECT_TRUE(MyExistenceWatcher::create_called_);
}

TEST(ReferenceCountedSingletonFactory, MultipleReleases) {
  TestReferenceCountedSingletonFactory factory;
  rcsf_ptr<MyExistenceWatcher> one(&factory), two(&factory);

  MyExistenceWatcher::create_called_ = false;
  MyExistenceWatcher::delete_called_ = false;
  one.release();
  EXPECT_FALSE(MyExistenceWatcher::delete_called_);
  one.release();
  EXPECT_FALSE(MyExistenceWatcher::delete_called_);
  one.release();
  EXPECT_FALSE(MyExistenceWatcher::delete_called_);
  one.get();
  EXPECT_TRUE(MyExistenceWatcher::create_called_);
}

TEST(ReferenceCountedSingletonFactory, Existentialism) {
  TestReferenceCountedSingletonFactory factory;

  rcsf_ptr<MyExistenceWatcher> one(&factory);

  MyExistenceWatcher::create_called_ = false;
  MyExistenceWatcher::delete_called_ = false;

  one.get();
  EXPECT_TRUE(MyExistenceWatcher::create_called_);
  one.release();
  EXPECT_TRUE(MyExistenceWatcher::delete_called_);
}

}  // namespace rtc
