-- metric宽表

DROP TABLE IF EXISTS dmt.wide_metric_avg;

CREATE TABLE IF NOT EXISTS dmt.wide_metric_avg(
    app_dwid BIGINT,
    app_name STRING,
    app_description STRING,
    app_version STRING,
    server_dwid BIGINT,
    server_hostname STRING,
    server_cpu_cores INT,
    server_memory INT,
    metric_index_dwid BIGINT,
    metric_name STRING,
    metric_description STRING,
    metric_category STRING,
    metric_threshold_dwid BIGINT,
    amber_threshold INT,
    red_threshold INT,
    hour_dwid BIGINT,
    db_date STRING,
    db_hour TIMESTAMP,
    year INT,
    month INT,
    day INT,
    hour INT,
    quarter INT,
    week INT,
    day_name STRING,
    month_name STRING,
    weekend_flag BOOLEAN,
    avg_value BIGINT,
    rag STRING
)
PARTITIONED BY (creation_date STRING)
STORED AS parquet;