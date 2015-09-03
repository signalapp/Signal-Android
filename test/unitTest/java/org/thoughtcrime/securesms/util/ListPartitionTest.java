package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ListPartitionTest extends BaseUnitTest {

  @Test public void testPartitionEven() {
    List<Integer> list = new LinkedList<>();

    for (int i=0;i<100;i++) {
      list.add(i);
    }

    List<List<Integer>> partitions = Util.partition(list, 10);

    assertEquals(partitions.size(), 10);

    int counter = 0;

    for (int i=0;i<partitions.size();i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(partition.size(), 10);

      for (int j=0;j<partition.size();j++) {
        assertEquals((int)partition.get(j), counter++);
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

    assertEquals(partitions.size(), 11);

    int counter = 0;

    for (int i=0;i<partitions.size()-1;i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(partition.size(), 10);

      for (int j=0;j<partition.size();j++) {
        assertEquals((int)partition.get(j), counter++);
      }
    }

    assertEquals(partitions.get(10).size(), 1);
    assertEquals((int)partitions.get(10).get(0), 100);
  }

  @Test public void testPathological() {
    List<Integer> list = new LinkedList<>();

    for (int i=0;i<100;i++) {
      list.add(i);
    }

    List<List<Integer>> partitions = Util.partition(list, 1);

    assertEquals(partitions.size(), 100);

    int counter = 0;

    for (int i=0;i<partitions.size();i++) {
      List<Integer> partition = partitions.get(i);
      assertEquals(partition.size(), 1);

      for (int j=0;j<partition.size();j++) {
        assertEquals((int)partition.get(j), counter++);
      }
    }
  }
}
