-- metric均值汇总表
-- 每小时的均值

DROP TABLE IF EXISTS dmt.sum_metric_avg;

CREATE TABLE IF NOT EXISTS dmt.sum_metric_avg(
    app_dwid BIGINT,
    server_dwid BIGINT,
    metric_index_dwid BIGINT,
    metric_threshold_dwid BIGINT,
    hour_dwid BIGINT,
    avg_value INT,
    rag STRING
)
PARTITIONED BY (creation_date STRING)
STORED AS parquet;