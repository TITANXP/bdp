# 创建表 metric (tmp->src)

DROP TABLE IF EXISTS src.bdp_metric_metric;

CREATE TABLE IF NOT EXISTS src.bdp_metric_metric(
    id BIGINT,
    name STRING,
    hostname STRING,
    value BIGINT,
    `timestamp` TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (creation_date STRING)
STORED AS parquet;