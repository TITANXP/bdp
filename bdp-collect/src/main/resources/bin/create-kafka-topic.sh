#!/usr/bin/env bash

# 分区数量partitions需要根据集群计算资源来调整,如果是7个节点的集群可以设为12
# --replication-factor备份数，7个节点时可以设为3
kafka-topics.sh \
  --zookeeper 192.168.170.129:2181 \
  --create \
  --topic cpu.usage \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --describe \
  --topic cpu.usage

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --create \
  --topic mem.used \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  -zookeeper 192.168.170.129 \
  --describe \
  --topic mem.used

kafka-topics.sh \
  -zookeeper 192.168.170.129 \
  --create \
  --topic alert \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --describe \
  --topic alert