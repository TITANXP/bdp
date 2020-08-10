-- 创建表 app (src -> dwh)

DROP TABLE IF EXISTS dwh.bdp_master_app;

CREATE TABLE IF NOT EXISTS dwh.bdp_master_app(
    id BIGINT,
    name STRING,
    description STRING,
    version STRING,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
STORED AS parquet;