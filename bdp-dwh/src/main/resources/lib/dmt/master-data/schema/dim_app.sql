-- app维度表

DROP TABLE IF EXISTS dmt.dim_app;

CREATE TABLE IF NOT EXISTS dmt.dim_app(
    dwid BIGINT,
    id BIGINT,
    name STRING,
    description STRING,
    version STRING,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    eff_flag BOOLEAN
)
STORED AS parquet;
