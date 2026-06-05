package org.thoughtcrime.securesms.util.adapter.mapping;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;

public class MappingModelList extends ArrayList<MappingModel<?>> {

  public MappingModelList() { }

  public MappingModelList(@NonNull Collection<? extends MappingModel<?>> c) {
    super(c);
  }

  public static @NonNull MappingModelList singleton(@NonNull MappingModel<?> model) {
    MappingModelList list = new MappingModelList();
    list.add(model);
    return list;
  }

  public static @NonNull Collector<MappingModel<?>, MappingModelList, MappingModelList> collect() {
    return new Collector<MappingModel<?>, MappingModelList, MappingModelList>() {
      @Override
      public Supplier<MappingModelList> supplier() {
        return MappingModelList::new;
      }

      @Override
      public BiConsumer<MappingModelList, MappingModel<?>> accumulator() {
        return MappingModelList::add;
      }

      @Override
      public BinaryOperator<MappingModelList> combiner() {
        return (left, right) -> {
          left.addAll(right);
          return left;
        };
      }

      @Override
      public Function<MappingModelList, MappingModelList> finisher() {
        return Function.identity();
      }

      @Override
      public Set<Characteristics> characteristics() {
        return Sets.immutableEnumSet(Characteristics.IDENTITY_FINISH);
      }
    };
  }

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

      @Override public Set<Characteristics> characteristics() {
        return Collections.emptySet();
      }

      @Override public BinaryOperator<MappingModelList> combiner() {
        return (x, y) -> {x.addAll(y); return x;};
      }

      @Override
      public @NonNull Function<MappingModelList, MappingModelList> finisher() {
        return mappingModels -> mappingModels;
      }
    };
  }
}
