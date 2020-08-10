-- 创建表 metric_index (src -> dwh)

DROP TABLE IF EXISTS dwh.bdp_master_metric_index;

CREATE TABLE IF NOT EXISTS dwh.bdp_master_metric_index(
    id BIGINT,
    name STRING,
    description STRING,
    category STRING,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
STORED AS parquet;
