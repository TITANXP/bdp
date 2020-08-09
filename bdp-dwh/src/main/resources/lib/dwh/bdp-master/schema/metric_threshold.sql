# 创建表 metric_threshold (src -> dwh)

DROP TABLE IF EXISTS dwh.bdp_master_metric_threshold;

CREATE TABLE IF NOT EXISTS dwh.bdp_master_metric_threshold(
    server_id BIGINT,
    metric_name STRING,
    amber_threshold INT,
    red_threshold INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
STORED AS parquet;