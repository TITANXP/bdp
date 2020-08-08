#!/usr/bin/env bash

# bdp-metric(tmp -> src)

# 执行schema目录下的sql，建立src层的指定表
create(){
  target="$1"
  execSql "job name: create schema of [ $target @ $SUBJECT ]" "$SRC_BDP_METRIC_HOME/schema/$target.sql"
}

# 执行action目录下的sql文件，向src层的指定表导入数据
build(){
  target="$1"
  execSql "job name: build [ $target ] data from [ $target @ $UNDER_LAYER_SUBJECT ] to [ $target @ $SUBJECT ]" "$SRC_BDP_METRIC_HOME/action/build-$target.sql"
}

createMetric(){
  create "metric"
}

buildMetric(){
  build "metric"
}