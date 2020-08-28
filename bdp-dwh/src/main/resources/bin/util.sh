#!/usr/bin/env bash

export TMP_DATA_BASE_DIR="/data/tmp"
export BDP_DWH_JAR_DIR="$BDP_DWH_HOME/jar"

BDP_DWH_DEPENDENCY_JARS=""
for JAR in $(ls ${BDP_DWH_JAR_DIR})
do
  BDP_DWH_DEPENDENCY_JARS="$BDP_DWH_JAR_DIR/$JAR,$BDP_DWH_DEPENDENCY_JARS"
done
# 添加mysql-connector-java-5.1.27-bin.jar
#for JAR in $(ls ${SPARK_HOME}/lib/mysql*)
#do
#  BDP_DWH_DEPENDENCY_JARS="$JAR,$BDP_DWH_DEPENDENCY_JARS"
#done
export BDP_DWH_DEPENDENCY_JARS=${BDP_DWH_DEPENDENCY_JARS%,}

# -----------------------------------------------    Public Methods   ------------------------------------------------ #

sparkSql(){
  spark-sql \
  --master yarn \
  --deploy-mode client \
  --name "$jobName" \
  --num-executors "${spark.num.executors}" \
  --executor-cores "${spark.executor.cores}" \
  --driver-memory 512M \
  --executor-memory "${spark.executor.memory}" \
  --conf spark.sql.warehouse.dir=${app.hdfs.user.home}/spark-warehouse \
  --conf spark.sql.crossJoin.enable=true \
  --conf spark.sql.shuffle.partitions=8 \
  --hiveconf hive.metastore.execute.setugi=true \
  --hiveconf hive.exec.dynamic.partition=true \
  --hiveconf hive.exec.dynamic.partition.mode=nonstrict \
  --hiveconf hive.exec.max.dynamic.partitions=10000 \
  --hiveconf hive.exec.max.dynamic.partitions.prenode=10000 \
  --hiveconf hive.mapred.supports.subdirectories=true \
  --hiveconf mapreduce.input.fileinputformat.input.dir.recursive=true \
  --jars "$BDP_DWH_DEPENDENCY_JARS"

  #--conf spark.sql.warehouse.dir
  #   在某些大数据发行版本中，spark.sql.warehouse.dir的默认路径只有spark用户才能有写的权限，但我们提交作业的账号是应用的专有账号，没有权限在默认的路径下写数据
  #   所以我们通常会在应用专有账号的HDFS home目录下指定warehouse的路径。
  # hive.metastore.execute.setugi：
  #   非安全模式，设置为true会令metastore以客户端的用户和组权限执行DFS操作，默认是false，这个属性需要服务端和客户端同时设置；
  #--jars
  #   声明项目依赖的jar包，通过,分割，本项目中依赖的jar包主要是项目的UDF
}

# 通过spark-sql执行sql文件
execSql(){
  jobName="$1"
  sqlFile="$2"
  printHeading "${jobName}"
  spark-sql \
  --master yarn \
  --deploy-mode client \
  --name "$jobName" \
  --num-executors "${spark.num.executors}" \
  --executor-cores "${spark.executor.cores}" \
  --executor-memory "${spark.executor.memory}" \
  --conf spark.sql.warehouse.dir=${app.hdfs.user.home}/spark-warehouse \
  --conf spark.sql.crossJoin.enable=true \
  --hiveconf hive.metastore.execute.setugi=true \
  --hiveconf hive.exec.dynamic.partition=true \
  --hiveconf hive.exec.dynamic.partition.mode=nostrict \
  --hiveconf hive.exec.max.dynamic.partitions=10000 \
  --hiveconf hive.exec.max.dynamic.partitions.pernode=10000 \
  --hiveconf hive.mapred.supports.subdirectories=true \
  --hiveconf mapreduce.input.fileinputformat.input.dir.recursive=true \
  --jars "$BDP_DWH_DEPENDENCY_JARS" \
  -f "$sqlFile"
}
# -----------------------------------------------   Private Methods   ------------------------------------------------ #

printHeading(){
    title="$1"
    paddingWidth=$((($(tput cols)-${#title})/2-3))
    printf "\n%${paddingWidth}s"|tr ' ' '='
    printf "  $title  "
    printf "%${paddingWidth}s\n\n"|tr ' ' '='
}

validateTime(){
    if [ "$1" = "" ]
    then
        echo "Time is missing!"
        exit 1
    fi
    TIME=$1
    date -d "$TIME" >/dev/null 2>&1
    if [ "$?" != "0" ]
    then
        echo "Invalid Time: $TIME"
        exit 1
    fi
}