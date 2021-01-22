package org.session.libsession.messaging.jobs

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class JobData {

    val EMPTY: JobData = Builder().build()

    @JsonProperty
    private var strings: Map<String, String>

    @JsonProperty
    private var stringArrays: Map<String, Array<String>>

    @JsonProperty
    private var integers: Map<String, Int>

    @JsonProperty
    private var integerArrays: Map<String, IntArray>

    @JsonProperty
    private var longs: Map<String, Long>

    @JsonProperty
    private var longArrays: Map<String, LongArray>

    @JsonProperty
    private var floats: Map<String, Float>

    @JsonProperty
    private var floatArrays: Map<String, FloatArray>

    @JsonProperty
    private var doubles: Map<String, Double>

    @JsonProperty
    private var doubleArrays: Map<String, DoubleArray>

    @JsonProperty
    private var booleans: Map<String, Boolean>

    @JsonProperty
    private var booleanArrays: Map<String, BooleanArray>

    @JsonProperty
    private var byteArrays: Map<String, ByteArray>

    constructor(@JsonProperty("strings") strings: MutableMap<String, String>,
                @JsonProperty("stringArrays") stringArrays: Map<String, Array<String>>,
                @JsonProperty("integers") integers: Map<String, Int>,
                @JsonProperty("integerArrays") integerArrays: Map<String, IntArray>,
                @JsonProperty("longs") longs: Map<String, Long>,
                @JsonProperty("longArrays") longArrays: Map<String, LongArray>,
                @JsonProperty("floats") floats: Map<String, Float>,
                @JsonProperty("floatArrays") floatArrays: Map<String, FloatArray>,
                @JsonProperty("doubles") doubles: Map<String, Double>,
                @JsonProperty("doubleArrays") doubleArrays: Map<String, DoubleArray>,
                @JsonProperty("booleans") booleans: Map<String, Boolean>,
                @JsonProperty("booleanArrays") booleanArrays: Map<String, BooleanArray>,
                @JsonProperty("byteArrays") byteArrays: Map<String, ByteArray>) {
        this.strings = strings
        this.stringArrays = stringArrays
        this.integers = integers
        this.integerArrays = integerArrays
        this.longs = longs
        this.longArrays = longArrays
        this.floats = floats
        this.floatArrays = floatArrays
        this.doubles = doubles
        this.doubleArrays = doubleArrays
        this.booleans = booleans
        this.booleanArrays = booleanArrays
        this.byteArrays = byteArrays
    }

    fun hasString(key: String): Boolean {
        return strings.containsKey(key)
    }

    fun getString(key: String): String {
        throwIfAbsent(strings, key)
        return strings[key]!!
    }

    fun getStringOrDefault(key: String, defaultValue: String?): String? {
        return if (hasString(key)) getString(key) else defaultValue
    }


    fun hasStringArray(key: String): Boolean {
        return stringArrays.containsKey(key)
    }

    fun getStringArray(key: String): Array<String>? {
        throwIfAbsent(stringArrays, key)
        return stringArrays[key]
    }


    fun hasInt(key: String): Boolean {
        return integers.containsKey(key)
    }

    fun getInt(key: String): Int {
        throwIfAbsent(integers, key)
        return integers[key]!!
    }

    fun getIntOrDefault(key: String, defaultValue: Int): Int {
        return if (hasInt(key)) getInt(key) else defaultValue
    }


    fun hasIntegerArray(key: String): Boolean {
        return integerArrays.containsKey(key)
    }

    fun getIntegerArray(key: String): IntArray? {
        throwIfAbsent(integerArrays, key)
        return integerArrays[key]
    }


    fun hasLong(key: String): Boolean {
        return longs.containsKey(key)
    }

    fun getLong(key: String): Long {
        throwIfAbsent(longs, key)
        return longs[key]!!
    }

    fun getLongOrDefault(key: String, defaultValue: Long): Long {
        return if (hasLong(key)) getLong(key) else defaultValue
    }


    fun hasLongArray(key: String): Boolean {
        return longArrays.containsKey(key)
    }

    fun getLongArray(key: String): LongArray? {
        throwIfAbsent(longArrays, key)
        return longArrays[key]
    }


    fun hasFloat(key: String): Boolean {
        return floats.containsKey(key)
    }

    fun getFloat(key: String): Float {
        throwIfAbsent(floats, key)
        return floats[key]!!
    }

    fun getFloatOrDefault(key: String, defaultValue: Float): Float {
        return if (hasFloat(key)) getFloat(key) else defaultValue
    }


    fun hasFloatArray(key: String): Boolean {
        return floatArrays.containsKey(key)
    }

    fun getFloatArray(key: String): FloatArray? {
        throwIfAbsent(floatArrays, key)
        return floatArrays[key]
    }


    fun hasDouble(key: String): Boolean {
        return doubles.containsKey(key)
    }

    fun getDouble(key: String): Double {
        throwIfAbsent(doubles, key)
        return doubles[key]!!
    }

    fun getDoubleOrDefault(key: String, defaultValue: Double): Double {
        return if (hasDouble(key)) getDouble(key) else defaultValue
    }


    fun hasDoubleArray(key: String): Boolean {
        return floatArrays.containsKey(key)
    }

    fun getDoubleArray(key: String): DoubleArray? {
        throwIfAbsent(doubleArrays, key)
        return doubleArrays[key]
    }


    fun hasBoolean(key: String): Boolean {
        return booleans.containsKey(key)
    }

    fun getBoolean(key: String): Boolean {
        throwIfAbsent(booleans, key)
        return booleans[key]!!
    }

    fun getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
        return if (hasBoolean(key)) getBoolean(key) else defaultValue
    }


    fun hasBooleanArray(key: String): Boolean {
        return booleanArrays.containsKey(key)
    }

    fun getBooleanArray(key: String): BooleanArray? {
        throwIfAbsent(booleanArrays, key)
        return booleanArrays[key]
    }

    fun hasByteArray(key: String): Boolean {
        return byteArrays.containsKey(key)
    }

    fun getByteArray(key: String): ByteArray {
        throwIfAbsent(byteArrays, key)
        return byteArrays[key]!!
    }

    fun hasParcelable(key: String): Boolean {
        return byteArrays!!.containsKey(key)
    }

    /*fun <T : Parcelable?> getParcelable(key: String, creator: Parcelable.Creator<T>): T {
        throwIfAbsent(byteArrays!!, key)
        val bytes = byteArrays[key]
        return ParcelableUtil.unmarshall(bytes, creator)
    }*/

    private fun throwIfAbsent(map: Map<*, *>, key: String) {
        check(map.containsKey(key)) { "Tried to retrieve a value with key '$key', but it wasn't present." }
    }


    class Builder {
        private val strings: MutableMap<String, String> = HashMap()
        private val stringArrays: MutableMap<String, Array<String>> = HashMap()
        private val integers: MutableMap<String, Int> = HashMap()
        private val integerArrays: MutableMap<String, IntArray> = HashMap()
        private val longs: MutableMap<String, Long> = HashMap()
        private val longArrays: MutableMap<String, LongArray> = HashMap()
        private val floats: MutableMap<String, Float> = HashMap()
        private val floatArrays: MutableMap<String, FloatArray> = HashMap()
        private val doubles: MutableMap<String, Double> = HashMap()
        private val doubleArrays: MutableMap<String, DoubleArray> = HashMap()
        private val booleans: MutableMap<String, Boolean> = HashMap()
        private val booleanArrays: MutableMap<String, BooleanArray> = HashMap()
        private val byteArrays: MutableMap<String, ByteArray> = HashMap()
        fun putString(key: String, value: String?): Builder {
            value?.let { strings[key] = value }
            return this
        }

        fun putStringArray(key: String, value: Array<String>): Builder {
            stringArrays[key] = value
            return this
        }

        fun putInt(key: String, value: Int): Builder {
            integers[key] = value
            return this
        }

        fun putIntArray(key: String, value: IntArray): Builder {
            integerArrays[key] = value
            return this
        }

        fun putLong(key: String, value: Long): Builder {
            longs[key] = value
            return this
        }

        fun putLongArray(key: String, value: LongArray): Builder {
            longArrays[key] = value
            return this
        }

        fun putFloat(key: String, value: Float): Builder {
            floats[key] = value
            return this
        }

        fun putFloatArray(key: String, value: FloatArray): Builder {
            floatArrays[key] = value
            return this
        }

        fun putDouble(key: String, value: Double): Builder {
            doubles[key] = value
            return this
        }

        fun putDoubleArray(key: String, value: DoubleArray): Builder {
            doubleArrays[key] = value
            return this
        }

        fun putBoolean(key: String, value: Boolean): Builder {
            booleans[key] = value
            return this
        }

        fun putBooleanArray(key: String, value: BooleanArray): Builder {
            booleanArrays[key] = value
            return this
        }

        fun putByteArray(key: String, value: ByteArray): Builder {
            byteArrays[key] = value
            return this
        }

        /*fun putParcelable(key: String, value: Parcelable): Builder {
            val bytes: ByteArray = ParcelableUtil.marshall(value)
            byteArrays[key] = bytes
            return this
        }*/

        fun build(): JobData {
            return JobData(strings,
                    stringArrays,
                    integers,
                    integerArrays,
                    longs,
                    longArrays,
                    floats,
                    floatArrays,
                    doubles,
                    doubleArrays,
                    booleans,
                    booleanArrays,
                    byteArrays)
        }
    }

    interface Serializer {
        fun serialize(data: JobData): String
        fun deserialize(serialized: String): JobData
    }

}