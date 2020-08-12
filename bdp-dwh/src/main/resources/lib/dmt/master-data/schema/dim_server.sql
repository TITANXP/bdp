-- server维度表

DROP TABLE IF EXISTS dmt.dim_server;

CREATE TABLE IF NOT EXISTS dmt.dim_server(
    dwid BIGINT,
    id BIGINT,
    app_id BIGINT,
    hostname STRING,
    cpu_cores INT,
    memory INT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    eff_flag BOOLEAN
)
STORED AS parquet;