/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// When the platform supports STL, the functions are implemented using a
// templated spreadsort algorithm (http://sourceforge.net/projects/spreadsort/),
// part of the Boost C++ library collection. Otherwise, the C standard library's
// qsort() will be used.

#include "webrtc/system_wrappers/include/sort.h"

#include <assert.h>
#include <string.h>  // memcpy

#include <new>      // nothrow new

#ifdef NO_STL
#include <stdlib.h>      // qsort
#else
#include <algorithm>    // std::sort
#include <vector>

// TODO(ajm) upgrade to spreadsort v2.
#include "webrtc/system_wrappers/source/spreadsortlib/spreadsort.hpp"
#endif

#ifdef NO_STL
#define COMPARE_DEREFERENCED(XT, YT)      \
  do {                                    \
    if ((XT) > (YT)) {                    \
      return 1;                           \
    }                                     \
    else if ((XT) < (YT)) {               \
      return -1;                          \
    }                                     \
    return 0;                             \
  } while(0)

#define COMPARE_FOR_QSORT(X, Y, TYPE)                           \
  do {                                                          \
    TYPE xT = static_cast<TYPE>(*static_cast<const TYPE*>(X));  \
    TYPE yT = static_cast<TYPE>(*static_cast<const TYPE*>(Y));  \
    COMPARE_DEREFERENCED(xT, yT);                               \
  } while(0)

#define COMPARE_KEY_FOR_QSORT(SORT_KEY_X, SORT_KEY_Y, TYPE)                   \
  do {                                                                        \
    TYPE xT = static_cast<TYPE>(                                              \
        *static_cast<TYPE*>(static_cast<const SortKey*>(SORT_KEY_X)->key_));  \
    TYPE yT = static_cast<TYPE>(                                              \
        *static_cast<TYPE*>(static_cast<const SortKey*>(SORT_KEY_Y)->key_));  \
    COMPARE_DEREFERENCED(xT, yT);                                             \
  } while(0)

#define KEY_QSORT(SORT_KEY, KEY, NUM_OF_ELEMENTS, KEY_TYPE, COMPARE_FUNC)     \
  do {                                                                        \
    KEY_TYPE* key_type = (KEY_TYPE*)(key);                                    \
    for (uint32_t i = 0; i < (NUM_OF_ELEMENTS); ++i) {                  \
      ptr_sort_key[i].key_ = &key_type[i];                                    \
      ptr_sort_key[i].index_ = i;                                             \
    }                                                                         \
    qsort((SORT_KEY), (NUM_OF_ELEMENTS), sizeof(SortKey), (COMPARE_FUNC));    \
  } while(0)
#endif

namespace webrtc {

#ifdef NO_STL
struct SortKey {
  void* key_;
  uint32_t index_;
};
#else
template<typename KeyType>
struct SortKey {
  KeyType key_;
  uint32_t index_;
};
#endif

namespace {  // Unnamed namespace provides internal linkage.

#ifdef NO_STL
int CompareWord8(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, int8_t);
}

int CompareUWord8(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, uint8_t);
}

int CompareWord16(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, int16_t);
}

int CompareUWord16(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, uint16_t);
}

int CompareWord32(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, int32_t);
}

int CompareUWord32(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, uint32_t);
}

int CompareWord64(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, int64_t);
}

int CompareUWord64(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, uint64_t);
}

int CompareFloat32(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, float);
}

int CompareFloat64(const void* x, const void* y) {
  COMPARE_FOR_QSORT(x, y, double);
}

int CompareKeyWord8(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, int8_t);
}

int CompareKeyUWord8(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, uint8_t);
}

int CompareKeyWord16(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, int16_t);
}

int CompareKeyUWord16(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, uint16_t);
}

int CompareKeyWord32(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, int32_t);
}

int CompareKeyUWord32(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, uint32_t);
}

int CompareKeyWord64(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, int64_t);
}

int CompareKeyUWord64(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, uint64_t);
}

int CompareKeyFloat32(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, float);
}

int CompareKeyFloat64(const void* sort_key_x, const void* sort_key_y) {
  COMPARE_KEY_FOR_QSORT(sort_key_x, sort_key_y, double);
}
#else
template <typename KeyType>
struct KeyLessThan {
  bool operator()(const SortKey<KeyType>& sort_key_x,
                  const SortKey<KeyType>& sort_key_y) const {
    return sort_key_x.key_ < sort_key_y.key_;
  }
};

template <typename KeyType>
struct KeyRightShift {
  KeyType operator()(const SortKey<KeyType>& sort_key,
                     const unsigned offset) const {
    return sort_key.key_ >> offset;
  }
};

template <typename DataType>
inline void IntegerSort(void* data, uint32_t num_of_elements) {
  DataType* data_type = static_cast<DataType*>(data);
  boost::integer_sort(data_type, data_type + num_of_elements);
}

template <typename DataType, typename IntegerType>
inline void FloatSort(void* data, uint32_t num_of_elements) {
  DataType* data_type = static_cast<DataType*>(data);
  IntegerType c_val = 0;
  boost::float_sort_cast(data_type, data_type + num_of_elements, c_val);
}

template <typename DataType>
inline void StdSort(void* data, uint32_t num_of_elements) {
  DataType* data_type = static_cast<DataType*>(data);
  std::sort(data_type, data_type + num_of_elements);
}

template<typename KeyType>
inline int32_t SetupKeySort(void* key,
                            SortKey<KeyType>*& ptr_sort_key,
                            uint32_t num_of_elements) {
  ptr_sort_key = new(std::nothrow) SortKey<KeyType>[num_of_elements];
  if (ptr_sort_key == NULL) {
    return -1;
  }

  KeyType* key_type = static_cast<KeyType*>(key);
  for (uint32_t i = 0; i < num_of_elements; i++) {
    ptr_sort_key[i].key_ = key_type[i];
    ptr_sort_key[i].index_ = i;
  }

  return 0;
}

template<typename KeyType>
inline int32_t TeardownKeySort(void* data,
                               SortKey<KeyType>* ptr_sort_key,
                               uint32_t num_of_elements,
                               uint32_t size_of_element) {
  uint8_t* ptr_data = static_cast<uint8_t*>(data);
  uint8_t* ptr_data_sorted =
      new(std::nothrow) uint8_t[num_of_elements * size_of_element];
  if (ptr_data_sorted == NULL) {
    return -1;
  }

  for (uint32_t i = 0; i < num_of_elements; i++) {
    memcpy(ptr_data_sorted + i * size_of_element, ptr_data +
           ptr_sort_key[i].index_ * size_of_element, size_of_element);
  }
  memcpy(ptr_data, ptr_data_sorted, num_of_elements * size_of_element);
  delete[] ptr_sort_key;
  delete[] ptr_data_sorted;
  return 0;
}

template<typename KeyType>
inline int32_t IntegerKeySort(void* data, void* key,
                              uint32_t num_of_elements,
                              uint32_t size_of_element) {
  SortKey<KeyType>* ptr_sort_key;
  if (SetupKeySort<KeyType>(key, ptr_sort_key, num_of_elements) != 0) {
    return -1;
  }

  boost::integer_sort(ptr_sort_key, ptr_sort_key + num_of_elements,
                      KeyRightShift<KeyType>(), KeyLessThan<KeyType>());

  if (TeardownKeySort<KeyType>(data, ptr_sort_key, num_of_elements,
                               size_of_element) != 0) {
    return -1;
  }

  return 0;
}

template<typename KeyType>
inline int32_t StdKeySort(void* data, void* key,
                          uint32_t num_of_elements,
                          uint32_t size_of_element) {
  SortKey<KeyType>* ptr_sort_key;
  if (SetupKeySort<KeyType>(key, ptr_sort_key, num_of_elements) != 0) {
    return -1;
  }

  std::sort(ptr_sort_key, ptr_sort_key + num_of_elements,
            KeyLessThan<KeyType>());

  if (TeardownKeySort<KeyType>(data, ptr_sort_key, num_of_elements,
                               size_of_element) != 0) {
    return -1;
  }

  return 0;
}
#endif
}

int32_t Sort(void* data, uint32_t num_of_elements, Type type) {
  if (data == NULL) {
    return -1;
  }

#ifdef NO_STL
  switch (type) {
    case TYPE_Word8:
      qsort(data, num_of_elements, sizeof(int8_t), CompareWord8);
      break;
    case TYPE_UWord8:
      qsort(data, num_of_elements, sizeof(uint8_t), CompareUWord8);
      break;
    case TYPE_Word16:
      qsort(data, num_of_elements, sizeof(int16_t), CompareWord16);
      break;
    case TYPE_UWord16:
      qsort(data, num_of_elements, sizeof(uint16_t), CompareUWord16);
      break;
    case TYPE_Word32:
      qsort(data, num_of_elements, sizeof(int32_t), CompareWord32);
      break;
    case TYPE_UWord32:
      qsort(data, num_of_elements, sizeof(uint32_t), CompareUWord32);
      break;
    case TYPE_Word64:
      qsort(data, num_of_elements, sizeof(int64_t), CompareWord64);
      break;
    case TYPE_UWord64:
      qsort(data, num_of_elements, sizeof(uint64_t), CompareUWord64);
      break;
    case TYPE_Float32:
      qsort(data, num_of_elements, sizeof(float), CompareFloat32);
      break;
    case TYPE_Float64:
      qsort(data, num_of_elements, sizeof(double), CompareFloat64);
      break;
    default:
      return -1;
  }
#else
  // Fall back to std::sort for 64-bit types and floats due to compiler
  // warnings and VS 2003 build crashes respectively with spreadsort.
  switch (type) {
    case TYPE_Word8:
      IntegerSort<int8_t>(data, num_of_elements);
      break;
    case TYPE_UWord8:
      IntegerSort<uint8_t>(data, num_of_elements);
      break;
    case TYPE_Word16:
      IntegerSort<int16_t>(data, num_of_elements);
      break;
    case TYPE_UWord16:
      IntegerSort<uint16_t>(data, num_of_elements);
      break;
    case TYPE_Word32:
      IntegerSort<int32_t>(data, num_of_elements);
      break;
    case TYPE_UWord32:
      IntegerSort<uint32_t>(data, num_of_elements);
      break;
    case TYPE_Word64:
      StdSort<int64_t>(data, num_of_elements);
      break;
    case TYPE_UWord64:
      StdSort<uint64_t>(data, num_of_elements);
      break;
    case TYPE_Float32:
      StdSort<float>(data, num_of_elements);
      break;
    case TYPE_Float64:
      StdSort<double>(data, num_of_elements);
      break;
  }
#endif
  return 0;
}

int32_t KeySort(void* data, void* key, uint32_t num_of_elements,
                uint32_t size_of_element, Type key_type) {
  if (data == NULL) {
    return -1;
  }

  if (key == NULL) {
    return -1;
  }

  if ((uint64_t)num_of_elements * size_of_element > 0xffffffff) {
    return -1;
  }

#ifdef NO_STL
  SortKey* ptr_sort_key = new(std::nothrow) SortKey[num_of_elements];
  if (ptr_sort_key == NULL) {
    return -1;
  }

  switch (key_type) {
    case TYPE_Word8:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, int8_t,
                CompareKeyWord8);
      break;
    case TYPE_UWord8:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, uint8_t,
                CompareKeyUWord8);
      break;
    case TYPE_Word16:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, int16_t,
                CompareKeyWord16);
      break;
    case TYPE_UWord16:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, uint16_t,
                CompareKeyUWord16);
      break;
    case TYPE_Word32:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, int32_t,
                CompareKeyWord32);
      break;
    case TYPE_UWord32:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, uint32_t,
                CompareKeyUWord32);
      break;
    case TYPE_Word64:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, int64_t,
                CompareKeyWord64);
      break;
    case TYPE_UWord64:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, uint64_t,
                CompareKeyUWord64);
      break;
    case TYPE_Float32:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, float,
                CompareKeyFloat32);
      break;
    case TYPE_Float64:
      KEY_QSORT(ptr_sort_key, key, num_of_elements, double,
                CompareKeyFloat64);
      break;
    default:
      return -1;
  }

  // Shuffle into sorted position based on index map.
  uint8_t* ptr_data = static_cast<uint8_t*>(data);
  uint8_t* ptr_data_sorted =
      new(std::nothrow) uint8_t[num_of_elements * size_of_element];
  if (ptr_data_sorted == NULL) {
    return -1;
  }

  for (uint32_t i = 0; i < num_of_elements; i++) {
    memcpy(ptr_data_sorted + i * size_of_element, ptr_data +
           ptr_sort_key[i].index_ * size_of_element, size_of_element);
  }
  memcpy(ptr_data, ptr_data_sorted, num_of_elements * size_of_element);

  delete[] ptr_sort_key;
  delete[] ptr_data_sorted;

  return 0;
#else
  // Fall back to std::sort for 64-bit types and floats due to compiler
  // warnings and errors respectively with spreadsort.
  switch (key_type) {
    case TYPE_Word8:
      return IntegerKeySort<int8_t>(data, key, num_of_elements,
                                    size_of_element);
    case TYPE_UWord8:
      return IntegerKeySort<uint8_t>(data, key, num_of_elements,
                                     size_of_element);
    case TYPE_Word16:
      return IntegerKeySort<int16_t>(data, key, num_of_elements,
                                     size_of_element);
    case TYPE_UWord16:
      return IntegerKeySort<uint16_t>(data, key, num_of_elements,
                                      size_of_element);
    case TYPE_Word32:
      return IntegerKeySort<int32_t>(data, key, num_of_elements,
                                     size_of_element);
    case TYPE_UWord32:
      return IntegerKeySort<uint32_t>(data, key, num_of_elements,
                                      size_of_element);
    case TYPE_Word64:
      return StdKeySort<int64_t>(data, key, num_of_elements,
                                 size_of_element);
    case TYPE_UWord64:
      return StdKeySort<uint64_t>(data, key, num_of_elements,
                                  size_of_element);
    case TYPE_Float32:
      return StdKeySort<float>(data, key, num_of_elements, size_of_element);
    case TYPE_Float64:
      return StdKeySort<double>(data, key, num_of_elements, size_of_element);
  }
  assert(false);
  return -1;
#endif
}

}  // namespace webrtc
