/*
 * Copyright 2013-2016 Indiana University
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.iu.data_transfer;

import com.intel.daal.data_management.data.HomogenNumericTable;
import com.intel.daal.data_management.data.NumericTable;
import com.intel.daal.data_management.data.SOANumericTable;
import com.intel.daal.services.DaalContext;
import edu.iu.dymoro.*;
import edu.iu.harp.partition.Partition;
import edu.iu.harp.partition.Table;
import edu.iu.harp.resource.IntArray;
import edu.iu.harp.resource.Simple;
import edu.iu.harp.resource.DoubleArray;
import edu.iu.harp.resource.IntArray;
import edu.iu.harp.resource.Array;
import edu.iu.harp.schstatic.Task;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapred.CollectiveMapper;

import java.util.Random;

// import com.intel.daal.data_management.data.HomogenBMNumericTable;

public class RotateTaskDaal<I, P extends Array<I> > extends
  Task<Integer, NumericTable> {

  protected static final Log LOG = LogFactory
    .getLog(RotateTaskDaal.class);

  private final CollectiveMapper<?, ?, ?, ?> mapper;
  //harp's table 
  private final Table<P> table;

  private final Random random;
  private final String contextName;
  private int operationID;

  private final int[] orders;
  private final int orderRowLen;
  private int curOrderID;
  private final int numWorkers;
  private Int2IntOpenHashMap dataWorkerMap;
  private Int2IntOpenHashMap rotationMap;
  private long commTime;

  //daal's table
  private NumericTable daal_table;
  //converter between harp and daal
  private HomogenTableHarpTable<I, P, Table<P> > converter;
  //feature dimension of table
  private int rdim;
  //threads used in concurrent data conversion
  private int numThreads;

  //daal Context
  private static DaalContext daal_Context = new DaalContext();

  public RotateTaskDaal(Table<P> table,
    int rdim, int numThreads, 
    CollectiveMapper<?, ?, ?, ?> mapper,
    int[] orders, String contextName) {

    this.table = table;
    random = new Random(System.currentTimeMillis());
    this.mapper = mapper;
    this.contextName = contextName;
    operationID = 0;
    numWorkers = mapper.getNumWorkers();

    if (orders != null) {
      this.orders = orders;
      orderRowLen = numWorkers * 2 - 1;
      curOrderID = 0;
      dataWorkerMap = new Int2IntOpenHashMap();
      // Initialize partition positions
      for (int i = 0; i < numWorkers; i++) {
        dataWorkerMap
          .put(i, orders[curOrderID++]);
      }
      rotationMap = new Int2IntOpenHashMap();
    } else {
      this.orders = null;
      orderRowLen = 0;
      curOrderID = 0;
      dataWorkerMap = null;
      rotationMap = null;
    }
    commTime = 0L;

    //initialize daal related vars
    this.rdim = rdim;
    this.numThreads = numThreads;

    //use standard HomogenNumericTable or optimized HomogenBMNumericTable in DAAL
    this.daal_table = new HomogenNumericTable(daal_Context, Double.class, this.rdim, table.getNumPartitions(), NumericTable.AllocationFlag.DoAllocate);

    //create the converter
    this.converter = new HomogenTableHarpTable<I, P, Table<P> >(table, this.daal_table, table.getNumPartitions(), this.rdim, this.numThreads);
   
    this.converter.HarpToDaalDouble();

  }

  public NumericTable daal_table() {
    return this.daal_table;
  }

  @Override
  public NumericTable run(Integer cmd)
    throws Exception {
    long t1 = System.currentTimeMillis();

    this.converter.DaalToHarpDouble();

    //rotate table 
    updateRotationMap();
    mapper.rotate(contextName,
      "rotate-" + table.getTableID() + "-"
        + operationID, table, rotationMap);

    int table_size = table.getNumPartitions();
    this.daal_table.freeDataMemory(); 

    this.daal_table = new HomogenNumericTable(daal_Context, Double.class, this.rdim, table_size, NumericTable.AllocationFlag.DoAllocate);
    // this.daal_table = new HomogenBMNumericTable(daal_Context, Double.class, this.rdim, table_size, NumericTable.AllocationFlag.DoAllocate);

    this.converter = new HomogenTableHarpTable<I, P, Table<P> >(table, this.daal_table, table_size, this.rdim, this.numThreads);
    this.converter.HarpToDaalDouble();

    operationID++;
    long t2 = System.currentTimeMillis();
    commTime += (t2 - t1);
    return this.daal_table;

  }

  private void updateRotationMap() {
    if (orders != null
      && curOrderID < orders.length) {
      if (curOrderID % orderRowLen == 0) {
        // The start of row
        int lastShift = orders[curOrderID - 1];
        for (int i = 0; i < numWorkers; i++) {
          // Get data i, calculate the new
          // location
          int originWorkerID =
            dataWorkerMap.get(i);
          int curWorkerID =
            (originWorkerID + lastShift)
              % numWorkers;
          int newWorkerID = orders[curOrderID];
          dataWorkerMap.put(i, newWorkerID);
          rotationMap.put(curWorkerID,
            newWorkerID);
          curOrderID++;
        }
      } else if (curOrderID % orderRowLen == numWorkers) {
        // The first shift
        for (int i = 0; i < numWorkers; i++) {
          // Get data i, calculate the new
          // location
          int curWorkerID = dataWorkerMap.get(i);
          int newWorkerID =
            (curWorkerID + orders[curOrderID])
              % numWorkers;
          rotationMap.put(curWorkerID,
            newWorkerID);
        }
        curOrderID++;
      } else {
        for (int i = 0; i < numWorkers; i++) {
          // Get data i, calculate the new
          // location
          int originWorkerID =
            dataWorkerMap.get(i);
          int curWorkerID =
            (originWorkerID + orders[curOrderID - 1])
              % numWorkers;
          int newWorkerID =
            (originWorkerID + orders[curOrderID])
              % numWorkers;
          rotationMap.put(curWorkerID,
            newWorkerID);
        }
        curOrderID++;
      }
    }
  }

  public long resetCommTime() {
    long time = commTime;
    commTime = 0L;
    return time;
  }
}
