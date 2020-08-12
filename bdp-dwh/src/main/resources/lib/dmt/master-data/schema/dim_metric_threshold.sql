-- metric_treshold维度表

DROP TABLE IF EXISTS dmt.dim_metric_threshold;

CREATE TABLE IF NOT EXISTS dmt.dim_metric_threshold(
    dwid BIGINT,
    server_id BIGINT,
    metric_name STRING,
    amber_threshold INT,
    red_threshold INT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    eff_flag BOOLEAN
)
STORED AS parquet;