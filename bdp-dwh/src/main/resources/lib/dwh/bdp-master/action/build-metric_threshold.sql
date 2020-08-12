-- 导入数据 metric_threshold (src -> dwh)

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

INSERT OVERWRITE TABLE dwh.bdp_master_metric_threshold
SELECT
    -- `(row_num|oc)?+.+`
    server_id, metric_name, amber_threshold, red_threshold, creation_time, update_time, imported_time
FROM(
    SELECT
        *,
        ROW_NUMBER() OVER(
            PARTITION BY server_id, metric_name
            ORDER BY update_time DESC, oc DESC
        ) AS row_num
    FROM (
         SELECT
            *, 0 AS oc
         FROM
            dwh.bdp_master_metric_threshold
         UNION ALL
         SELECT
            -- `(update_date)?+.+`, 1 AS oc
            server_id, metric_name, amber_threshold, red_threshold, creation_time, update_time, imported_time, 1 AS oc
         FROM
            src.bdp_master_metric_threshold
         WHERE
            update_date >= '@startDate@' AND update_date < '@endDate@'
    )a
) b
WHERE row_num = 1;