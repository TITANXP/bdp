-- 创建表 metric_index (tmp->src)

DROP TABLE IF EXISTS src.bdp_master_metric_index;

CREATE TABLE IF NOT EXISTS src.bdp_master_metric_index(
    id BIGINT,
    name STRING,
    description STRING,
    category STRING,
    creation_time timestamp,
    update_time timestamp,
    imported_time timestamp
)
PARTITIONED BY (update_date STRING)
STORED AS parquet;