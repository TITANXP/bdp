#!/usr/bin/env bash

export BDP_COLLECT_HOME="$(cd "`dirname $(readlink -nf "$0")`"/..; pwd -P)"

BDP_COLLECT_LIB_DIR=${BDP_COLLECT_HOME}/lib
BDP_COLLECT_CONF_DIR=${BDP_COLLECT_HOME}/conf
BDP_COLLECT_PID=/tmp/${project.artifactId}.pid
BDP_COLLECT_MAIN_CLASS="${app.mainClass}"

# ------------------------------------------------   Common Methods   ------------------------------------------------ #

showUsage() {
    printHeading "BDP-COLLECT USAGE"
    echo "# 创建Kafka topics"
    echo "$0 create-topics"
    echo
    echo "# 启动程序"
    echo "$0 start"
    echo
    echo "# 终止程序"
    echo "$0 stop"
    echo
    echo "# 重新启动程序"
    echo "$0 restart"
    echo
    echo "# 监控日志输出"
    echo "$0 tail-log"
    echo
    echo "# 重新启动程序并持续监控日志输出"
    echo "$0 restart-with-logging"
    echo
}

printHeading() {
    title="$1"
    paddingWidth=$((($(tput cols)-${#title})/2-3))
    printf "\n%${paddingWidth}s"|tr ' ' '='
    printf " [ $title ] "
    printf "%${paddingWidth}s\n\n"|tr ' ' '='
}

getJavaCmd() {
  if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME/bin/java"
  else
    echo java
  fi
}

# ------------------------------------------------    Major Methods   ------------------------------------------------ #

start() {
  java=$(getJavaCmd)
  nohup $java -Duser.timezone=Asia/Shanghai -classpath "$BDP_COLLECT_LIB_DIR/*" $BDP_COLLECT_MAIN_CLASS > /dev/null 2>&1 &
  echo $! > $BDP_COLLECT_PID
}

stop() {
  if [ -f $BDP_COLLECT_PID ]; then
    # kill -0 = 查看进程是否存在
    if kill -0 `cat $BDP_COLLECT_PID` > /dev/null 2>&1; then
      kill -9 `cat $BDP_COLLECT_PID` > /dev/null 2>&1
    fi
  fi
}

restart() {
    stop
    start
}

tailLog() {
    tail -F ${app.log.home}/${project.artifactId}.log
}

createKafkaTopics() {
  source ${BDP_COLLECT_HOME}/bin/create-kafka-topic.sh
}

# -----------------------------------------------   main   -------------------------------------------- #

case $1 in
    (create-topics)
      createKafkaTopics
    ;;
    (start)
        start
    ;;
    (stop)
        stop
    ;;
    (restart)
        restart
    ;;
    (tail-log)
        tailLog
    ;;
    (restart-with-logging)
        restart
        tailLog
    ;;
    (help)
        showUsage
    ;;
    (*)
        showUsage
    ;;
esac