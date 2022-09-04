package org.thoughtcrime.securesms.util.adapter.mapping

import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

class MappingModelList : ArrayList<MappingModel<*>?> {
    constructor() {}
    constructor(c: Collection<MappingModel<*>?>) : super(c) {}

    companion object {
        @JvmStatic
        fun singleton(model: MappingModel<*>): MappingModelList {
            val list = MappingModelList()
            list.add(model)
            return list
        }

        @JvmStatic
        fun collect(): Collector<MappingModel<*>, MappingModelList, MappingModelList> {
            return object : Collector<MappingModel<*>, MappingModelList, MappingModelList> {
                override fun supplier(): Supplier<MappingModelList> {
                    return java.util.function.Supplier { MappingModelList() }
                }

                override fun accumulator(): BiConsumer<MappingModelList, MappingModel<*>> {
                    return java.util.function.BiConsumer { obj: MappingModelList, e: MappingModel<*> -> obj.add(e) }
                }

                override fun combiner(): BinaryOperator<MappingModelList> {
                    return BinaryOperator { left: MappingModelList, right: MappingModelList ->
                        left.addAll(right)
                        left
                    }
                }

                override fun finisher(): Function<MappingModelList, MappingModelList> {
                    return Function.identity()
                }

                override fun characteristics(): Set<Collector.Characteristics> {
                    return setOf(Collector.Characteristics.IDENTITY_FINISH)
                }
            }
        }

        /*fun toMappingModelList(): Collector<MappingModel<*>, MappingModelList, MappingModelList> {
            return object : Collector<MappingModel<*>, MappingModelList, MappingModelList?> {
                override fun supplier(): Supplier<MappingModelList> {
                    return Supplier { MappingModelList() }
                }

                override fun accumulator(): BiConsumer<MappingModelList, MappingModel<*>> {
                    return BiConsumer { obj: MappingModelList, e: MappingModel<*> -> obj.add(e) }
                }

                override fun finisher(): Function<MappingModelList, MappingModelList?> {
                    return Function { mappingModels: MappingModelList? -> mappingModels }
                }
            }
        }*/
    }
}