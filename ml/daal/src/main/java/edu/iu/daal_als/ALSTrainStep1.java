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

package edu.iu.daal_als;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.OutputStreamWriter;

import edu.iu.harp.example.DoubleArrPlus;
import edu.iu.harp.example.IntArrPlus;
import edu.iu.harp.partition.Partition;
import edu.iu.harp.partition.PartitionStatus;
import edu.iu.harp.partition.Partitioner;
import edu.iu.harp.partition.Table;
import edu.iu.harp.resource.DoubleArray;
import edu.iu.harp.resource.IntArray;
import edu.iu.harp.resource.ByteArray;
import edu.iu.harp.resource.LongArray;

import edu.iu.data_transfer.*;

// packages from Daal 
import com.intel.daal.algorithms.implicit_als.PartialModel;
import com.intel.daal.algorithms.implicit_als.prediction.ratings.*;
import com.intel.daal.algorithms.implicit_als.training.*;
import com.intel.daal.algorithms.implicit_als.training.init.*;

import com.intel.daal.data_management.data.NumericTable;
import com.intel.daal.data_management.data.CSRNumericTable;
import com.intel.daal.data_management.data.HomogenNumericTable;
import com.intel.daal.data_management.data.SOANumericTable;
import com.intel.daal.data_management.data.KeyValueDataCollection;

import com.intel.daal.data_management.data_source.DataSource;
import com.intel.daal.data_management.data_source.FileDataSource;
import com.intel.daal.services.DaalContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ALSTrainStep1 {

    private DistributedStep1Local algo;
    private long nFactor;
    private long numThreads;
    private int self_id;
    private DistributedPartialResultStep4 inputData;
    private static DaalContext daal_Context = new DaalContext();
    private ALSDaalCollectiveMapper collect_mapper;

    protected static final Log LOG = LogFactory.getLog(ALSTrainStep1.class);

    public ALSTrainStep1(long nFactor, long numThreads, 
            DistributedPartialResultStep4 inputData, ALSDaalCollectiveMapper collect_mapper)
    {
        this.algo = new DistributedStep1Local(daal_Context, Double.class, TrainingMethod.fastCSR);
        this.nFactor = nFactor;
        this.numThreads = numThreads;
        this.self_id = self_id;
        this.inputData = inputData;
        this.collect_mapper = collect_mapper;

        this.algo.parameter.setNFactors(this.nFactor);
        // this.algo.parameter.setNumThreads(this.numThreads);

        // Set input objects for the algorithm 
        this.algo.input.set(PartialModelInputId.partialModel,
                inputData.get(DistributedPartialResultStep4Id.outputOfStep4ForStep1));

    }

    public DistributedPartialResultStep1 compute()
    {
        return this.algo.compute();
    }

    public Table<ByteArray> communicate(DistributedPartialResultStep1 res) throws IOException
    {

        try {

            byte[] serialStep1LocalResult = serializeStep1Result(res);
            ByteArray step1LocalResult_harp = new ByteArray(serialStep1LocalResult, 0, serialStep1LocalResult.length);
            Table<ByteArray> step1LocalResult_table = new Table<>(0, new ByteArrPlus());
            step1LocalResult_table.addPartition(new Partition<>(this.collect_mapper.getSelfID(), step1LocalResult_harp));

            //reduce to master node with id 0
            this.collect_mapper.allgather("als", "sync-partial-res", step1LocalResult_table);
            return step1LocalResult_table;

        } catch (Exception e) 
        {
            LOG.error("Fail to serilization.", e);
            return null;
        }

    }

    private byte[] serializeStep1Result(DistributedPartialResultStep1 res) throws IOException {
        // Create an output stream to serialize the numeric table 
        ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(outputByteStream);

        // Serialize the partialResult table into the output stream 
        res.pack();
        outputStream.writeObject(res);

        // Store the serialized data in an array 
        byte[] buffer = outputByteStream.toByteArray();
        return buffer;
    }

}

