package org.thoughtcrime.securesms.util.adapter.mapping

import com.annimon.stream.Collector
import com.annimon.stream.function.BiConsumer
import com.annimon.stream.function.Function
import com.annimon.stream.function.Supplier

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
                    return Supplier { MappingModelList() }
                }

                override fun accumulator(): BiConsumer<MappingModelList, MappingModel<*>> {
                    return BiConsumer { obj: MappingModelList, e: MappingModel<*> -> obj.add(e) }
                }

                override fun finisher(): Function<MappingModelList, MappingModelList> {
                    return Function { t -> t }
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