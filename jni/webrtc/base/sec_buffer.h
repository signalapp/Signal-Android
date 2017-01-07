/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// @file Contains utility classes that make it easier to use SecBuffers

#ifndef WEBRTC_BASE_SEC_BUFFER_H__
#define WEBRTC_BASE_SEC_BUFFER_H__

namespace rtc {

// A base class for CSecBuffer<T>. Contains
// all implementation that does not require
// template arguments.
class CSecBufferBase : public SecBuffer {
 public:
  CSecBufferBase() {
    Clear();
  }

  // Uses the SSPI to free a pointer, must be
  // used for buffers returned from SSPI APIs.
  static void FreeSSPI(void *ptr) {
    if ( ptr ) {
      SECURITY_STATUS status;
      status = ::FreeContextBuffer(ptr);
      ASSERT(SEC_E_OK == status); // "Freeing context buffer"
    }
  }

  // Deletes a buffer with operator delete
  static void FreeDelete(void *ptr) {
    delete [] reinterpret_cast<char*>(ptr);
  }

  // A noop delete, for buffers over other
  // people's memory
  static void FreeNone(void *ptr) {
  }

 protected:
  // Clears the buffer to EMPTY & NULL
  void Clear() {
    this->BufferType = SECBUFFER_EMPTY;
    this->cbBuffer = 0;
    this->pvBuffer = NULL;
  }
};

// Wrapper class for SecBuffer to take care
// of initialization and destruction.
template <void (*pfnFreeBuffer)(void *ptr)>
class CSecBuffer: public CSecBufferBase {
 public:
  // Initializes buffer to empty & NULL
  CSecBuffer() {
  }

  // Frees any allocated memory
  ~CSecBuffer() {
    Release();
  }

  // Frees the buffer appropriately, and re-nulls
  void Release() {
    pfnFreeBuffer(this->pvBuffer);
    Clear();
  }

 private:
  // A placeholder function for compile-time asserts on the class
  void CompileAsserts() {
    // never invoked...
    assert(false); // _T("Notreached")

    // This class must not extend the size of SecBuffer, since
    // we use arrays of CSecBuffer in CSecBufferBundle below
    cassert(sizeof(CSecBuffer<SSPIFree> == sizeof(SecBuffer)));
  }
};

// Contains all generic implementation for the
// SecBufferBundle class
class SecBufferBundleBase {
 public:
};

// A template class that bundles a SecBufferDesc with
// one or more SecBuffers for convenience. Can take
// care of deallocating buffers appropriately, as indicated
// by pfnFreeBuffer function.
// By default does no deallocation.
template <int num_buffers,
          void (*pfnFreeBuffer)(void *ptr) = CSecBufferBase::FreeNone>
class CSecBufferBundle : public SecBufferBundleBase {
 public:
  // Constructs a security buffer bundle with num_buffers
  // buffers, all of which are empty and nulled.
  CSecBufferBundle() {
    desc_.ulVersion = SECBUFFER_VERSION;
    desc_.cBuffers = num_buffers;
    desc_.pBuffers = buffers_;
  }

  // Frees all currently used buffers.
  ~CSecBufferBundle() {
    Release();
  }

  // Accessor for the descriptor
  PSecBufferDesc desc() {
    return &desc_;
  }

  // Accessor for the descriptor
  PSecBufferDesc desc() const {
    return &desc_;
  }

  // returns the i-th security buffer
  SecBuffer &operator[] (size_t num) {
    ASSERT(num < num_buffers); // "Buffer index out of bounds"
    return buffers_[num];
  }

  // returns the i-th security buffer
  const SecBuffer &operator[] (size_t num) const {
    ASSERT(num < num_buffers); // "Buffer index out of bounds"
    return buffers_[num];
  }

  // Frees all non-NULL security buffers,
  // using the deallocation function
  void Release() {
    for ( size_t i = 0; i < num_buffers; ++i ) {
      buffers_[i].Release();
    }
  }

 private:
  // Our descriptor
  SecBufferDesc               desc_;
  // Our bundled buffers, each takes care of its own
  // initialization and destruction
  CSecBuffer<pfnFreeBuffer>   buffers_[num_buffers];
};

} // namespace rtc

#endif  // WEBRTC_BASE_SEC_BUFFER_H__
