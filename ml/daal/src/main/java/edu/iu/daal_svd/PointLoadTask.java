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

package edu.iu.daal_svd;

import edu.iu.data_gen.DataLoader;
import edu.iu.harp.schdynamic.Task;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;

public class PointLoadTask implements
  Task<String, double[]> {

  protected static final Log LOG = LogFactory
    .getLog(PointLoadTask.class);

  private int pointsPerFile;
  private int cenVecSize;
  private Configuration conf;

  public PointLoadTask(int pointsPerFile,
    int cenVecSize, Configuration conf) {
    this.pointsPerFile = pointsPerFile;
    this.cenVecSize = cenVecSize;
    this.conf = conf;
  }

  @Override
  public double[] run(String fileName)
    throws Exception {
    long threadId = Thread.currentThread().getId();
    System.out.println("PointLoadTaskThread "+ threadId);
    int count = 0;
    boolean isSuccess = false;
    do{
      try{
        
        // double[] array = DataLoader.loadPoints(fileName, pointsPerFile,
        //     cenVecSize, conf);
        double[] array = DataLoader.loadPointsMMDense(fileName, 
            cenVecSize, conf);

        return array;
      } catch (Exception e) {
        LOG.error("load " + fileName
          + " fails. Count=" + count, e);
        Thread.sleep(100);
        isSuccess = false;
        count++;
      }
    } while (!isSuccess && count < 100);
    LOG.error("Fail to load files.");
    return null;
  }

  /**
   * Load data points from a file.
   * 
   * @param file
   * @param conf
   * @return
   * @throws IOException
   */
  public static double[] loadPoints(String file,
    int pointsPerFile, int cenVecSize,
    Configuration conf) throws Exception {
    double[] points =
      new double[pointsPerFile * cenVecSize];
    Path pointFilePath = new Path(file);
    FileSystem fs =
      pointFilePath.getFileSystem(conf);
    FSDataInputStream in = fs.open(pointFilePath);
    try {
      for (int i = 0; i < points.length;) {
        //points[i++] = Double.MAX_VALUE;
        for (int j = 0; j < cenVecSize; j++) {
          points[i++] = in.readDouble();
        }
      }
    } finally {
      in.close();
    }
    return points;
  }
}
