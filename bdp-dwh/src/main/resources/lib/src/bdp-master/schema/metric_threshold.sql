# 创建表 metric_threshold (tmp->src)

DROP TABLE IF EXISTS src.bdp_master_metric_threshold;

CREATE TABLE IF NOT EXISTS src.bdp_master_metric_threshold(
    server_id BIGINT,
    metric_mame STRING,
    amber_threshold INT,
    red_threshold INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (timestamp STRING)
STORED AS parquet;