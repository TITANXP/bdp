-- metric_index维度表

DROP TABLE IF EXISTS dmt.dim_metric_index;

CREATE TABLE IF NOT EXISTS dmt.dim_metric_index(
    dwid BIGINT,
    id BIGINT,
    name STRING,
    description STRING,
    category STRING,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    eff_flag BOOLEAN
)
STORED AS parquet;