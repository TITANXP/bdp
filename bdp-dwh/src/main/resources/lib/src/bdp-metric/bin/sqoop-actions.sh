#!/usr/bin/env bash

# bdp-metric (mysql -> tmp)

# sqoop自动根据MySQL的表在Hive建表
createToTmp(){
  srcTable="$1"
  sinkTable="$2"
  jobname="create table $sinkTable"

  printHeading "job name : jobname"

  sqoop create-hive-table \
  -Dmapred.job.name="$jobname"
  --connect '${bdp.metric.jdbc.url}' \
  --username '${bdp.metric.jdbc.user}' \
  --password '${bdp.metric.jdbc.password}' \
  --table "$srcTable" \
  --hive-table "$sinkTable" \
  --hive-overwrite
}

# 使用Sqoop将MySQL中指定表的数据导入到tmp层
buildToTmp(){
  srcTable="$1"
  sinkTable="$2"
  splitColumn="$3"
  validateTime "$4"
  validataTIme "$5"

  jobName="subject: $SUBJECT -- build [ $srcTable ] data from data source to tmp layer via sqoop"

  printHeading "${jobname}"

  startTime=$(date -d "$4" +"%F %T")
  endTime=$(date -d "$5" +"%F %T")

  sinkTablePath="$TMP_DATA_BASE_DIR/$sinkTable/"

  sqoop import \
  -Dmapred.job.name="${jobname}"
  --connect '${bdp.metric.jdbc.url}' \
  --username '${bdp.metric.jdbc.password}' \
  --password '${bdp.metric.jdbc.password}' \
  --table "$srcTable" \
  --where "timestamp between '$startTime' and '$endTime'" \
  --split-by "$splitColumn" \
  --hive-import \
  --hive-overwrite \
  --hive-table "$sinkTable" \
  --target-dir "$sinkTablePath" \
  --out-dir "/tmp" \
  --delete-target-dir
}

createMetricToTmp(){
  createToTmp "metric" "tmp.bdp_metric_metric"
}

buildMetricToTmp(){
  buildToTmp "metric" "tmp.bdp_metric_metric" "id" "$1" "$2"
}