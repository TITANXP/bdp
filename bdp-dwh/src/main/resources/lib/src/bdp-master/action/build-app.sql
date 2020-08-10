-- 导入数据 app (tmp -> src)
INSERT OVERWRITE TABLE src.bdp_master_app PARTITION(update_date)
SELECT
    id,
    name,
    description,
    version,
    CAST(creation_time AS TIMESTAMP) AS creation_time,
    CAST(update_time AS TIMESTAMP) AS update_time,
    CURRENT_TIMESTAMP AS imported_time，
    CAST(CAST (update_time AS DATE ) AS STRING) AS update_date
FROM
    tmp.bdp_master_app;