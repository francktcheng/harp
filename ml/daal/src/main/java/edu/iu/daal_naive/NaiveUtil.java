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

package edu.iu.daal_naive;

import it.unimi.dsi.fastutil.ints.IntArrays;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import edu.iu.harp.schdynamic.DynamicScheduler;
import edu.iu.harp.partition.Partition;
import edu.iu.harp.partition.Table;
import edu.iu.harp.resource.DoubleArray;

import edu.iu.data_gen.*;

public class NaiveUtil{
	protected static final Log LOG = LogFactory
		.getLog(NaiveUtil.class);

	public static List<List<double[]>> loadPoints(
			List<String> fileNames, int pointsPerFile,
			int cenVecSize, Configuration conf,
			int numThreads) {
		long startTime = System.currentTimeMillis();
		List<PointLoadTask> tasks =
			new LinkedList<>();
		List<List<double[]>> arrays = new LinkedList<List<double[]>>();
		for (int i = 0; i < numThreads; i++) {
			tasks.add(new PointLoadTask(cenVecSize, conf));
		}
		DynamicScheduler<String, List<double[]>, PointLoadTask> compute =
			new DynamicScheduler<>(tasks);
		for (String fileName : fileNames) {
			compute.submit(fileName);
		}
		compute.start();
		compute.stop();
		while (compute.hasOutput()) {
			List<double[]> output = compute.waitForOutput();
			if (output != null) {
				arrays.add(output);
			}
		}
		long endTime = System.currentTimeMillis();
		System.out.println("File read (ms): "
				+ (endTime - startTime)
				+ ", number of point arrays: "
				+ arrays.size());
		return arrays;
			} 

	static void generatePoints(int numOfDataPoints,
			int vectorSize, int numPointFiles, int nClasses,
			String localInputDir, FileSystem fs,
			Path dataDir) throws IOException,
				InterruptedException, ExecutionException {

					int pointsPerFile =
						numOfDataPoints / numPointFiles;
					System.out.println("Writing " + pointsPerFile
							+ " vectors to a file");
					// Check data directory
					if (fs.exists(dataDir)) {
						fs.delete(dataDir, true);
					}
					// Check local directory
					File localDir = new File(localInputDir);
					// If existed, regenerate data
					if (localDir.exists()
							&& localDir.isDirectory()) {
						for (File file : localDir.listFiles()) {
							file.delete();
						}
						localDir.delete();
							}
					boolean success = localDir.mkdir();
					if (success) {
						System.out.println("Directory: "
								+ localInputDir + " created");
					}
					if (pointsPerFile == 0) {
						throw new IOException("No point to write.");
					}
					// Create random data points
					int poolSize =
						Runtime.getRuntime().availableProcessors();
					ExecutorService service =
						Executors.newFixedThreadPool(poolSize);
					List<Future<?>> futures =
						new LinkedList<Future<?>>();
					for (int k = 0; k < numPointFiles; k++) {
						Future<?> f = service.submit(new DataGenNaiveBayes(localInputDir, Integer.toString(k), pointsPerFile, vectorSize, nClasses));
						futures.add(f); // add a new thread
					}
					for (Future<?> f : futures) {
						f.get();
					}
					// Shut down the executor service so that this
					// thread can exit
					service.shutdownNow();
					// Wrap to path object
					Path localInput = new Path(localInputDir);
					fs.copyFromLocalFile(localInput, dataDir);
	}

	static void generateTestPoints(int numOfDataPoints,
			int vectorSize, int nClasses,
			String localInputDir, FileSystem fs,
			Path dataDir) throws IOException,
				InterruptedException, ExecutionException 
	{

					// Check data directory
					if (fs.exists(dataDir)) {
						fs.delete(dataDir, true);
					}
					// Check local directory
					File localDir = new File(localInputDir);
					// If existed, regenerate data
					if (localDir.exists()
							&& localDir.isDirectory()) {
						for (File file : localDir.listFiles()) {
							file.delete();
						}
						localDir.delete();

							}
					boolean success = localDir.mkdir();
					if (success) {
						System.out.println("Directory: "
								+ localInputDir + " created");
					}

					// generate test points
					BufferedWriter writer = new BufferedWriter(new FileWriter(localInputDir + File.separator + "testdata"));
					Random random = new Random();

					double point = 0;
					int label = 0;
					for (int i = 0; i < numOfDataPoints; i++) {
						for (int j = 0; j < vectorSize; j++) {
							point = random.nextDouble()*2 -1;
							writer.write(String.valueOf(point));
							writer.write(",");
						}

						label = random.nextInt(nClasses);
						writer.write(String.valueOf(label));
						writer.newLine();
					}

					writer.close();
					System.out.println("Write test data file");

					// Wrap to path object
					Path localInput = new Path(localInputDir);
					fs.copyFromLocalFile(localInput, dataDir);

	}

	static void generateGroundTruth(int numOfDataPoints,
			int nClasses, String localInputDir, FileSystem fs,
			Path dataDir) throws IOException,
				InterruptedException, ExecutionException 
	{

					// Check data directory
					if (fs.exists(dataDir)) {
						fs.delete(dataDir, true);
					}
					// Check local directory
					File localDir = new File(localInputDir);
					// If existed, regenerate data
					if (localDir.exists()
							&& localDir.isDirectory()) {
						for (File file : localDir.listFiles()) {
							file.delete();
						}
						localDir.delete();

							}
					boolean success = localDir.mkdir();
					if (success) {
						System.out.println("Directory: "
								+ localInputDir + " created");
					}

					// generate test points
					BufferedWriter writer = new BufferedWriter(new FileWriter(localInputDir + File.separator + "groundtruth"));
					Random random = new Random();

					// double point = 0;
					int label = 0;
					for (int i = 0; i < numOfDataPoints; i++) {
						// for (int j = 0; j < vectorSize; j++) {
						// 	point = random.nextDouble()*2 -1;
						// 	writer.write(String.valueOf(point));
						// 	writer.write(",");
						// }
						label = random.nextInt(nClasses);
						writer.write(String.valueOf(label));
						writer.newLine();
					}

					writer.close();
					System.out.println("Write groundtruth data file");

					// Wrap to path object
					Path localInput = new Path(localInputDir);
					fs.copyFromLocalFile(localInput, dataDir);

	}

	public static void generateData(
			int numDataPoints, int numTestPoints, int vectorSize, int numPointFiles, int nClasses,
			 FileSystem fs, String localDir, Path trainDir, Path testDir, Path groundTrueDir)
			throws IOException, InterruptedException,
							  ExecutionException 
	{
	   System.out.println("Generating training data..... ");
	   generatePoints(numDataPoints, vectorSize,
			   numPointFiles, nClasses, localDir, fs, trainDir);

	   System.out.println("Generating test data..... ");
	   generateTestPoints(numTestPoints, vectorSize, nClasses, localDir, fs, testDir);

	   System.out.println("Generating groundtruth data..... ");
	   generateGroundTruth(numTestPoints, nClasses, localDir, fs, groundTrueDir);

	   DeleteFileFolder(localDir);

    }

	public static void DeleteFileFolder(String path) {

      File file = new File(path);
      if(file.exists())
      {
          do{
              delete(file);
          }while(file.exists());
      }else
      {
          System.out.println("File or Folder not found : "+path);
      }

  }

  private static void delete(File file)
  {
      if(file.isDirectory())
      {
          String fileList[] = file.list();
          if(fileList.length == 0)
          {
              System.out.println("Deleting Directory : "+file.getPath());
              file.delete();
          }else
          {
              int size = fileList.length;
              for(int i = 0 ; i < size ; i++)
              {
                  String fileName = fileList[i];
                  System.out.println("File path : "+file.getPath()+" and name :"+fileName);
                  String fullPath = file.getPath()+"/"+fileName;
                  File fileOrFolder = new File(fullPath);
                  System.out.println("Full Path :"+fileOrFolder.getPath());
                  delete(fileOrFolder);
              }
          }
      }else
      {
          System.out.println("Deleting file : "+file.getPath());
          file.delete();
      }
  }
}
