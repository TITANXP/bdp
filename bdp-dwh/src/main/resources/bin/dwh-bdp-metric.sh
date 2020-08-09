#!/usr/bin/env bash
# bdp-metric (src -> dwh)

export BDP_DWH_HOME="$(cd "`dirname $(readlink -nf "$0")`"/..; pwd -P)"
export DWH_BDP_MASTER_HOME="$BDP_DWH_HOME/lib/dwh/bdp-metric"
export SUBJECT="dwh :: bdp-metric"
export UNDER_LAYER_SUBJECT="src :: bdp-metric"

source "BDP_DWH_HOME/bin/util.sh"
source "DWH_BDP_MASTER_HOME/bin/spark-actions.sh"

# ------------------------------------------------   Common Methods   ------------------------------------------------ #

showUsage(){
    printHeading "MODULE: [ $(echo "$SUBJECT" | tr 'a-z' 'A-Z') ] USAGE"

    echo "# 说明：创建metric表的schema"
    echo "$0 create-metric"
    echo

    echo "# 说明：从src导入指定时间范围内的metric数据到dwh"
    echo "$0 build-metric START_TIME END_TIME"
    echo

    echo "# 示例：从src导入2018-09-01的metric数据到dwh"
    echo "$0 build-metric '2018-09-01T00:00+0800' '2018-09-02T00:00+0800'"
    echo
}

# ----------------------------------------------    main    ---------------------------------------------- #

case $1 in
    (create-metric)
        createMetric
    ;;
    (build-metric)
        shift
        buildMetric "$@"
    ;;
    (help)
        showUsage
    ;;
    (*)
        showUsage
    ;;
esac