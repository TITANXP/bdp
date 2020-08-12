-- 时间事实表

DROP TABLE IF EXISTS dmt.dim_hour;

CREATE TABLE IF NOT EXISTS dmt.dim_hour(
    dwid BIGINT,
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
    weekend_flag BOOLEAN
)
STORED AS parquet;

-- 从dim_hour.csv导入数据
DROP TABLE IF EXISTS tmp.dim_hour;

CREATE TABLE IF NOT EXISTS tmp.dim_hour(
    dwid BIGINT,
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
    weekend_flag boolean
)
ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
STORED AS textfile
LOCATION '/data/tmp/dim_hour/';