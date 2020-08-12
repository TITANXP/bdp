-- metric事实表

DROP TABLE IF EXISTS dmt.fact_metric;

CREATE TABLE IF NOT EXISTS dmt.fact_metric(
    id BIGINT,
    app_dwid BIGINT,
    server_dwid BIGINT,
    metric_index_dwid BIGINT,
    hour_dwid BIGINT,
    `timestamp` TIMESTAMP,
    value BIGINT
)
PARTITIONED BY (creation_date STRING)
STORED AS parquet;