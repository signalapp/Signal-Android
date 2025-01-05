package org.thoughtcrime.securesms.util;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ListPartitionTest {

  @Test public void testPartitionEven() {
    List<Integer> list = new LinkedList<>();

    for (int i=0;i<100;i++) {
      list.add(i);
    }

    List<List<Integer>> partitions = Util.partition(list, 10);

    assertEquals(10, partitions.size());

    int counter = 0;

    for (int i=0;i<partitions.size();i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(10, partition.size());

      for (int j=0;j<partition.size();j++) {
        assertEquals(counter++, (int)partition.get(j));
      }
    }
  }

  @Test public void testPartitionOdd() {
    List<Integer> list = new LinkedList<>();

    for (int i=0;i<100;i++) {
      list.add(i);
    }

    list.add(100);

    List<List<Integer>> partitions = Util.partition(list, 10);

    assertEquals(11, partitions.size());

    int counter = 0;

    for (int i=0;i<partitions.size()-1;i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(10, partition.size());

      for (int j=0;j<partition.size();j++) {
        assertEquals(counter++, (int)partition.get(j));
      }
    }

    assertEquals(1, partitions.get(10).size());
    assertEquals(100, (int)partitions.get(10).get(0));
  }

  @Test public void testPathological() {
    List<Integer> list = new LinkedList<>();

    for (int i=0;i<100;i++) {
      list.add(i);
    }

    List<List<Integer>> partitions = Util.partition(list, 1);

    assertEquals(100, partitions.size());

    int counter = 0;

    for (int i=0;i<partitions.size();i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(1, partition.size());

      for (int j=0;j<partition.size();j++) {
        assertEquals(counter++, (int)partition.get(j));
      }
    }
  }
}
