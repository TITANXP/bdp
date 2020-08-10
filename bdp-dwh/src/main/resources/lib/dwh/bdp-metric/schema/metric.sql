-- 创建表 metric (src -> dwh)
DROP TABLE IF EXISTS dwh.bdp_metric_metric;

CREATE TABLE IF NOT EXISTS dwh.bdp_metric_metric(
    id BIGINT,
    name STRING,
    hostname STRING,
    value BIGINT,
    `timestamp` TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (creation_date STRING)
STORED AS parquet;
