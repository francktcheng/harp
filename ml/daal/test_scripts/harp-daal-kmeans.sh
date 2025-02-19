#!/bin/bash

## root path of harp  
cd ../../../
export HARP_ROOT=$(pwd)
cd ${HARP_ROOT}

if [ -z ${HADOOP_HOME+x} ];then
    echo "HADOOP not setup"
    exit
fi

cp ${HARP_ROOT}/ml/daal/target/harp-daal-0.1.0.jar ${HADOOP_HOME}

cd ${HADOOP_HOME}

# check that safemode is not enabled 
hdfs dfsadmin -safemode get | grep -q "ON"
if [[ "$?" = "0"  ]]; then
    hdfs dfsadmin -safemode leave
fi

## copy required third_party native libs to HDFS
hdfs dfs -mkdir -p /Hadoop
hdfs dfs -mkdir -p /Hadoop/Libraries
hdfs dfs -rm /Hadoop/Libraries/*
hdfs dfs -put ${HARP_ROOT}/third_party/daal-2018/lib/intel64_lin/libJavaAPI.so /Hadoop/Libraries/
hdfs dfs -put ${HARP_ROOT}/third_party/tbb/lib/intel64_lin/gcc4.4/libtbb* /Hadoop/Libraries/

export LIBJARS=${HARP_ROOT}/third_party/daal-2018/lib/daal.jar

hdfs dfs -mkdir -p /Hadoop/kmeans-input

## log directory
mkdir -p ${HADOOP_HOME}/Harp-DAAL-Kmeans
logDir=${HADOOP_HOME}/Harp-DAAL-Kmeans

## parameters
# num of training data points
Pts=10000
# num of training data centroids
Ced=10
# feature vector dimension
Dim=10
# file per mapper
File=5
# iteration times
ITR=100
# memory allocated to each mapper (MB)
Mem=110000
GenData=true
# num of mappers (nodes)
Node=2
# num of threads on each mapper(node)
Thd=16
Dataset=kmeans-P$Pts-C$Ced-D$Dim-F$File-N$Node

echo "Test-daal-kmeans-P$Pts-C$Ced-D$Dim-F$File-ITR$ITR-N$Node-Thd$Thd Start" 
hadoop jar harp-daal-0.1.0.jar edu.iu.daal_kmeans.regroupallgather.KMeansDaalLauncher -libjars ${LIBJARS} $Pts $Ced $Dim $File $Node $Thd $ITR $Mem /Hadoop/kmeans-input/$Dataset /tmp/kmeans $GenData 2>$logDir/Test-daal-kmeans-P$Pts-C$Ced-D$Dim-F$File-ITR$ITR-N$Node-Thd$Thd.log
echo "Test-daal-kmeans-P$Pts-C$Ced-D$Dim-F$File-ITR$ITR-N$Node-Thd$Thd End" 

