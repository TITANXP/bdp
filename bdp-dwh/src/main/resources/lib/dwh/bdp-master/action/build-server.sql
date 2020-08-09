# 导入数据 server (src -> dwh)

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

INSERT OVERWRITE TABLE dwh.bdp_master_server
SELECT
    `(row_num|oc)?+.+`
FROM(
    SELECT
        *,
        ROW_NUMBER() OVER(
            PARTITION BY id
            ORDER BY update_time DESC, oc DESC
        ) AS row_num
    FROM(
        SELECT
            *, 0 AS oc
        FROM
            dwh.bdp_master_server
        UNION ALL
        SELECT
            `(update_date)?+.+`, 1 AS oc
        FROM
            dwh.bdp_master_server
        WHERE
            update_date >= '@startDate@' AND update_date < '@endDate@'
    ) a
)
WHERE row_num = 1;