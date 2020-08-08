# 创建表 app (tmp->src)

DROP TABLE IF EXISTS src.bdp_master_app;

CREATE TABLE IF NOT EXISTS src.bdp_master_app(
    id BIGINT,
    name STRING,
    description STRING,
    version STRING,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (update_date STRING)
STORED AS parquet;