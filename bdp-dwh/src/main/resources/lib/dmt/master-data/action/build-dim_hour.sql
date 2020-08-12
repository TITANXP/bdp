-- 导入数据 dim_hour
-- 此表数据不会变化

INSERT OVERWRITE TABLE dmt.dim_hour
SELECT
    dwid,
    db_date,
    db_hour,
    year,
    month,
    day,
    hour,
    quarter,
    week,
    day_name,
    month_name,
    weekend_flag
FROM
    tmp.dim_hour;