# 创建表 server (src -> dwh)

DROP TABLE IF EXISTS dwh.bdp_master_server;

CREATE TABLE IF NOT EXISTS dwh.bdp_master_server(
    id BIGINT,
    app_id BIGINT,
    hostname STRING,
    cpu_cores INT,
    memory INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
STORED AS parquet;