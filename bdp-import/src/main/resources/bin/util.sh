#!/usr/bin/env bash

# HDFS上的表数据存放目录
export TMP_DATA_BASE_DIR="/data/tmp"

# 居中打印标题
printHeading(){
  title="$1"
  paddingWidth=$((($(tput cols) - ${#title}) / 2 - 3))
  printf "\n%${paddingWidth}s" | tr ' ' '='
  printf "  $title  "
  printf "%${paddingWidth}s\n\n" | tr ' ' '='
}

# 验证时间是否合法
validateTime(){
  if [ "$1" = "" ]
  then
    echo "time is missing!"
    exit 1
  fi
  TIME="$1"
  date -d "$TIME" >/dev/null 2>&1
  if [ "$?" != 0 ]
  then
    echo "时间格式错误:$TIME"
    exit 1
  fi
}