/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>
#include <string.h>

#include <algorithm>

#include "webrtc/base/timeutils.h"
#include "webrtc/system_wrappers/include/sort.h"

// Excellent work polluting the global namespace Visual Studio...
#undef max
#undef min
#include <limits>

template<typename KeyType>
struct LotsOfData
{
    KeyType key;
    char data[64];
};

template<typename DataType>
int Compare(const void* dataX, const void* dataY)
{
    DataType dataX = (DataType)*(const DataType*)dataX;
    DataType dataY = (DataType)*(const DataType*)dataY;
    if (dataX > dataY)
    {
        return 1;
    }
    else if (dataX < dataY)
    {
        return -1;
    }

    return 0;
};

template<typename DataType, typename KeyType>
int CompareKey(const void* dataX, const void* dataY)
{
    KeyType keyX = ((const DataType*)dataX)->key;
    KeyType keyY = ((const DataType*)dataY)->key;
    if (keyX > keyY)
    {
        return 1;
    }
    else if (keyX < keyY)
    {
        return -1;
    }

    return 0;
}

template<typename DataType>
struct KeyLessThan
{
    bool operator()(const DataType &dataX, const DataType &dataY) const
    {
        return dataX.key < dataY.key;
    }
};

const char* TypeEnumToString(webrtc::Type type)
{
    switch (type)
    {
        using namespace webrtc;
        case TYPE_Word8:
            return "Word8";
        case TYPE_UWord8:
            return "UWord8";
        case TYPE_Word16:
            return "Word16";
        case TYPE_UWord16:
            return "UWord16";
        case TYPE_Word32:
            return "Word32";
        case TYPE_UWord32:
            return "UWord32";
        case TYPE_Word64:
            return "Word64";
        case TYPE_UWord64:
            return "UWord64";
        case TYPE_Float32:
            return "Float32";
        case TYPE_Float64:
            return "Float64";
        default:
            return "Unrecognized";
    }
}

template<typename Type>
Type TypedRand()
{
    if (std::numeric_limits<Type>::is_integer)
    {
        double floatRand = static_cast<double>(rand()) / RAND_MAX;
        if (std::numeric_limits<Type>::is_signed)
        {
            floatRand -= 0.5;
        }

        // Uniform [-max()/2, max()/2] for signed
        //         [0, max()] for unsigned
        return static_cast<Type>(floatRand * std::numeric_limits<Type>::max());
    }
    else // Floating point
    {
        // Uniform [-0.5, 0.5]
        // The outer cast is to remove template warnings.
        return static_cast<Type>((static_cast<Type>(rand()) / RAND_MAX) - 0.5);
    }
}

template<typename KeyType>
void RunSortTest(webrtc::Type sortType, bool keySort)
{
    enum { DataLength = 1000 };
    enum { NumOfTests = 10000 };
    KeyType key[DataLength];
    KeyType keyRef[DataLength];
    LotsOfData<KeyType> data[DataLength];
    LotsOfData<KeyType> dataRef[DataLength];
    int32_t retVal = 0;

    if (keySort)
    {
        printf("Running %s KeySort() tests...\n", TypeEnumToString(sortType));
    }
    else
    {
        printf("Running %s Sort() tests...\n", TypeEnumToString(sortType));
    }

    int64_t accTicks;
    for (int i = 0; i < NumOfTests; i++)
    {
        for (int j = 0; j < DataLength; j++)
        {
            key[j] = TypedRand<KeyType>();
            data[j].key = key[j];
            // Write index to payload. We use this later for verification.
            sprintf(data[j].data, "%d", j);
        }

        memcpy(dataRef, data, sizeof(data));
        memcpy(keyRef, key, sizeof(key));

        retVal = 0;
        int64_t t0 = rtc::TimeNanos();
        if (keySort)
        {
            retVal = webrtc::KeySort(data, key, DataLength, sizeof(LotsOfData<KeyType>),
                sortType);

            //std::sort(data, data + DataLength, KeyLessThan<KeyType>());
            //qsort(data, DataLength, sizeof(LotsOfData<KeyType>),
            //    CompareKey<LotsOfData<KeyType>, KeyType>);
        }
        else
        {
            retVal = webrtc::Sort(key, DataLength, sortType);

            //std::sort(key, key + DataLength);
            //qsort(key, DataLength, sizeof(KeyType), Compare<KeyType>);
        }
        int64_t t1 = rtc::TimeNanos();
        accTicks += (t1 - t0);

        if (retVal != 0)
        {
            printf("Test failed at iteration %d:\n", i);
            printf("Sort returned an error. ");
            printf("It likely does not support the requested type\nExiting...\n");
            exit(0);
        }

        // Reference sort.
        if (!keySort)
        {
            std::sort(keyRef, keyRef + DataLength);
        }

        if (keySort)
        {
            for (int j = 0; j < DataLength - 1; j++)
            {
                if (data[j].key > data[j + 1].key)
                {
                    printf("Test failed at iteration %d:\n", i);
                    printf("Keys are not monotonically increasing\nExiting...\n");
                    exit(0);
                }

                int index = atoi(data[j].data);
                if (index < 0 || index >= DataLength || data[j].key != dataRef[index].key)
                {
                    printf("Test failed at iteration %d:\n", i);
                    printf("Payload data is corrupt\nExiting...\n");
                    exit(0);
                }
            }
        }
        else
        {
            for (int j = 0; j < DataLength - 1; j++)
            {
                if (key[j] > key[j + 1])
                {
                    printf("Test failed at iteration %d:\n", i);
                    printf("Data is not monotonically increasing\nExiting...\n");
                    exit(0);
                }
            }

            if (memcmp(key, keyRef, sizeof(key)) != 0)
            {
                printf("Test failed at iteration %d:\n", i);
                printf("Sort data differs from std::sort reference\nExiting...\n");
                exit(0);
            }
        }
    }

    printf("Compliance test passed over %d iterations\n", NumOfTests);

    int64_t executeTime = accTicks / rtc::kNumNanosecsPerMillisec;
    printf("Execute time: %.2f s\n\n", (float)executeTime / 1000);
}

int main()
{
    // Seed rand().
    srand(42);
    bool keySort = false;
    for (int i = 0; i < 2; i++) {
        RunSortTest<int8_t>(webrtc::TYPE_Word8, keySort);
        RunSortTest<uint8_t>(webrtc::TYPE_UWord8, keySort);
        RunSortTest<int16_t>(webrtc::TYPE_Word16, keySort);
        RunSortTest<uint16_t>(webrtc::TYPE_UWord16, keySort);
        RunSortTest<int32_t>(webrtc::TYPE_Word32, keySort);
        RunSortTest<uint32_t>(webrtc::TYPE_UWord32, keySort);
        RunSortTest<int64_t>(webrtc::TYPE_Word64, keySort);
        RunSortTest<uint64_t>(webrtc::TYPE_UWord64, keySort);
        RunSortTest<float>(webrtc::TYPE_Float32, keySort);
        RunSortTest<double>(webrtc::TYPE_Float64, keySort);

        keySort = !keySort;
    }

    printf("All tests passed\n");

    return 0;
}
