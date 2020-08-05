#!/usr/bin/env bash

export BDP_IMPORT_HOME="$(cd "`dirname $(readlink -nf "$0")`"/..; pwd -P)"
export SUBJECT="bdp-master-import"

source "$BDP_IMPORT_HOME/bin/util.sh"
# ------------------------------------------------   Common Methods   ------------------------------------------------ #

showUsage(){
    printHeading "MODULE: [ $(echo "$SUBJECT" | tr 'a-z' 'A-Z') ] USAGE"

    echo "# 说明：创建app表的schema，并从数据源导入指定时间范围内的app数据到tmp的对应表"
    echo "$0 init-app START_TIME END_TIME"

    echo "# --示例：创建app表的schema，从数据源导入2018-09-01的app数据到tmp的对应表"
    echo "    $0 init-app '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建app表的schema"
    echo "$0 create-app"
    echo

    echo "# 说明：从数据源导入指定时间范围内的app数据到tmp的对应表"
    echo "$0 import-app START_TIME END_TIME"

    echo "# --示例：从数据源导入2018-09-01的app数据到tmp的对应表"
    echo "    $0 import-app '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建server表的schema，并从数据源导入指定时间范围内的server数据到tmp的对应表"
    echo "$0 init-server START_TIME END_TIME"

    echo "# --示例：创建server表的schema，并从数据源导入2018-09-01的server数据到tmp的对应表"
    echo "    $0 init-server '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建server表的schema"
    echo "$0 create-server"
    echo

    echo "# 说明：从数据源导入指定时间范围内的server数据到tmp, 构建server的缓慢变化维度表"
    echo "$0 import-server START_TIME END_TIME"

    echo "# --示例：从数据源导入2018-09-01的server数据到tmp的对应表"
    echo "    $0 import-server '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建metric-index表的schema，从数据源导入指定时间范围内的metric-index数据到tmp的对应表"
    echo "$0 init-metric-index START_TIME END_TIME"

    echo "# --示例：创建metric-index表的schema，从数据源导入2018-09-01的metric-index数据到tmp的对应表"
    echo "    $0 init-metric-index '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建metric-index表的schema"
    echo "$0 create-metric-index"
    echo

    echo "# 说明：从数据源导入指定时间范围内的metric-index数据到tmp, 构建metric-index的缓慢变化维度表"
    echo "$0 import-metric-index START_TIME END_TIME"

    echo "# --示例：从数据源导入2018-09-01的metric-index数据到tmp的对应表"
    echo "    $0 import-metric-index '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建metric-threshold表的schema，从数据源导入指定时间范围内的metric-threshold数据到tmp的对应表"
    echo "$0 init-metric-threshold START_TIME END_TIME"

    echo "# --示例：创建metric-threshold表的schema，从数据源导入2018-09-01的metric-threshold数据到tmp的对应表"
    echo "    $0 init-metric-threshold '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo

    echo "# 说明：创建metric-threshold表的schema"
    echo "$0 create-metric-threshold"
    echo

    echo "# 说明：从数据源导入指定时间范围内的metric-threshold数据到tmp的对应表"
    echo "$0 import-metric-threshold START_TIME END_TIME"

    echo "# --示例：从数据源导入2018-09-01的metric-threshold数据到tmp的对应表"
    echo "    $0 import-metric-threshold '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo
}

# ------------------------------------------------    Major Methods   ------------------------------------------------ #

# 在hive创建指定表
createToTmp(){
  srcTable="$1"
  sinkTable="$2"
  jobname="create table ${sinkTable}"

  printHeading "$jobname"

  sqoop create-hive-table \
  -D mapred.job.name="$jobname" \
  --connect '${bdp.master.jdbc.url}' \
  --username '${bdp.master.jdbc.user}' \
  --password '${bdp.master.jdbc.password}' \
  --table "$srcTable" \
  --hive-table "$sinkTable" \
  --hive-overwrite
}

# 向hive导入指定表的数据
importToTmp(){
  srcTable="$1"
  sinkTable="$2"
  splitColumn="$3"
  validateTime "$4"
  validateTime "$5"
  startTime=$(date -d "$4" +"%F %T")
  endTime=$(date -d "$5" +"%F %T")
  sinkTablePath="$SINK_DATA_BASE_DIR/$sinkTable/"
  jobname="subject: $SUBJECT -- import [ $srcTable ] data form dataSource to tmp layer via sqoop"

  printHeading "${jobname}"

  sqoop import \
  -D mapred.job.name="${jobname}" \
  --connect '${bdp.master.jdbc.url}' \
  --username '${bdp.master.jdbc.user}' \
  --password '${bdp.master.jdbc.password}' \
  --table "$srcTable" \
  --where "update_time between '$startTime' and '$endTime'" \
  --split-by "$splitColumn" \
  --hive-import \
  --hive-overwrite \
  --hive-table "$sinkTable" \
  --target-dir "$sinkTablePath" \
  --outdir "/tmp" \
  --delete-target-dir
}

# 具体表的操作
# app
createAppToTmp(){
  createToTmp "app" "tmp.bdp_master_app"
}

importAppToTmp(){
  importToTmp "app" "tmp.bdp_master_app" "id" "$1" "$2"
}

# server
createServerToTmp(){
  createToTmp "server" "tmp.bdp_master_server"
}

importServerToTmp(){
  importToTmp "server" "tmp.bdp_master_server" "id" "$1" "$2"
}

# metric_index
createMetricIndexToTmp(){
  createToTmp "metric_index" "tmp.bdp_master_metric_index"
}

importMetricIndexToTmp(){
    importToTmp "metric_index" "tmp.bdp_master_metric_index" "id" "$1" "$2"
}

# metric_threshold
createMetricThresholdToTmp(){
    createToTmp "metric_threshold" "tmp.bdp_master_metric_threshold"
}

importMetricThresholdToTmp(){
    importToTmp "metric_threshold" "tmp.bdp_master_metric_threshold" "server_id" "$1" "$2"
}
# ------------------------------------------------   main   ------------------------------------------------ #

case $1 in
  (init-all)
    createAppToTmp
    createServerToTmp
    createMetricIndexToTmp
    createMetricThresholdToTmp
    shift
    importAppToTmp "$@"
    importServerToTmp "$@"
    importMetricIndexToTmp "$@"
    importMetricThresholdToTmp "$@"
  ;;
  (create-all)
    createAppToTmp
    createServerToTmp
    createMetricIndexToTmp
    createMetricThresholdToTmp
  ;;
  (import-all)
    shift
    importAppToTmp "$@"
    importServerToTmp "$@"
    importMetricIndexToTmp "$@"
    importMetricThresholdToTmp "$@"
  ;;
  (init-app)
    createAppToTmp
    shift
    importAppToTmp "$@"
  ;;
  (create-app)
    createAppToTmp
  ;;
  (import-app)
    shift
    importAppToTmp "$@"
  ;;
  (init-server)
      createServerToTmp
      shift
      importServerToTmp "$@"
  ;;
  (create-server)
      createServerToTmp
  ;;
  (import-server)
      shift
      importServerToTmp "$@"
  ;;
  (init-metric-index)
      createMetricIndexToTmp
      shift
      importMetricIndexToTmp "$@"
  ;;
  (create-metric-index)
      createMetricIndexToTmp
  ;;
  (import-metric-index)
      shift
      importMetricIndexToTmp "$@"
  ;;
  (init-metric-threshold)
      createMetricThresholdToTmp
      shift
      importMetricThresholdToTmp "$@"
  ;;
  (create-metric-threshold)
      createMetricThresholdToTmp
  ;;
  (import-metric-threshold)
      shift
      importMetricThresholdToTmp "$@"
  ;;
  (help)
      showUsage
  ;;
  (*)
      showUsage
  ;;
esac