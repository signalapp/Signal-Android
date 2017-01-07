/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Generic unstable sorting routines.

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_SORT_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_SORT_H_

#include "webrtc/common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

enum Type {
  TYPE_Word8,
  TYPE_UWord8,
  TYPE_Word16,
  TYPE_UWord16,
  TYPE_Word32,
  TYPE_UWord32,
  TYPE_Word64,
  TYPE_UWord64,
  TYPE_Float32,
  TYPE_Float64
};

// Sorts intrinsic data types.
//
// data            [in/out] A pointer to an array of intrinsic type.
//                 Upon return it will be sorted in ascending order.
// num_of_elements The number of elements in the array.
// data_type       Enum corresponding to the type of the array.
//
// returns 0 on success, -1 on failure.
int32_t Sort(void* data, uint32_t num_of_elements, Type data_type);

// Sorts arbitrary data types. This requires an array of intrinsically typed
// key values which will be used to sort the data array. There must be a
// one-to-one correspondence between data elements and key elements, with
// corresponding elements sharing the same position in their respective
// arrays.
//
// data            [in/out] A pointer to an array of arbitrary type.
//                 Upon return it will be sorted in ascending order.
// key             [in] A pointer to an array of keys used to sort the
//                 data array.
// num_of_elements The number of elements in the arrays.
// size_of_element The size, in bytes, of the data array.
// key_type        Enum corresponding to the type of the key array.
//
// returns 0 on success, -1 on failure.
//
int32_t KeySort(void* data, void* key, uint32_t num_of_elements,
                uint32_t size_of_element, Type key_type);

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_SORT_H_
