package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import com.annimon.stream.Collector;
import com.annimon.stream.function.BiConsumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Supplier;

import java.util.ArrayList;

public class MappingModelList extends ArrayList<MappingModel<?>> {

  public static @NonNull Collector<MappingModel<?>, MappingModelList, MappingModelList> toMappingModelList() {
    return new Collector<MappingModel<?>, MappingModelList, MappingModelList>() {
      @Override
      public @NonNull Supplier<MappingModelList> supplier() {
        return MappingModelList::new;
      }

      @Override
      public @NonNull BiConsumer<MappingModelList, MappingModel<?>> accumulator() {
        return MappingModelList::add;
      }

      @Override
      public @NonNull Function<MappingModelList, MappingModelList> finisher() {
        return mappingModels -> mappingModels;
      }
    };
  }
}
