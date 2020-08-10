-- 创建表 server (tmp->src)

DROP TABLE IF EXISTS src.bdp_master_server;

CREATE TABLE IF NOT EXISTS src.bdp_master_server(
    id BIGINT,
    app_id BIGINT,
    hostname STRING,
    cpu_cores INT,
    memory INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (update_date STRING)
STORED AS parquet;