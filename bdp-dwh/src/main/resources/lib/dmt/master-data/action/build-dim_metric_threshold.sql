-- 导入数据 dim_metric_threshold

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

-- CREATE OR REPLACE TEMPORARY VIEW updated_and_added_records AS
CREATE OR REPLACE VIEW updated_and_added_records AS
SELECT
    -- s.`(creation_time|update_time|imported_time)?+.+`
    server_id, metric_name, amber_threshold, red_threshold, update_date
FROM
    src.bdp_master_metric_threshold s
WHERE
    s.update_date >='@startDate@' AND s.update_date < '@endDate@';


insert OVERWRITE TABLE dmt.dim_metric_threshold
select
    *
FROM(
    SELECT
        -- m.`(valid_to|eff_flag)?+.+`,
        m.dwid, m.server_id, m.metric_name, m.amber_threshold, m.red_threshold, m.valid_from,
        CASE WHEN m.eff_flag = TRUE AND u.server_id IS NOT NULL AND u.metric_name IS NOT NULL THEN
            u.update_date
        ELSE
            m.valid_to
        END
        AS valid_to,
        CASE WHEN m.eff_flag = TRUE AND u.server_id IS NOT NULL AND u.metric_name IS NOT NULL THEN
            FALSE
        ELSE
            m.eff_flag
        END
        AS eff_flag
    FROM
        dmt.dim_metric_threshold m
    LEFT JOIN
        updated_and_added_records u
    ON
        m.server_id = u.server_id and m.metric_name = u.metric_name

    UNION ALL

    SELECT
        ROW_NUMBER() over (ORDER BY 0) + m.max_id AS dwid,
        -- u.`(update_date)?+.+`,
        u.server_id, u.metric_name, u.amber_threshold, u.red_threshold,
        u.update_date AS valid_from,
        NULL AS valid_to,
        TRUE AS eff_flag
    FROM
        updated_and_added_records u
    CROSS JOIN
        (SELECT COALESCE(MAX(dwid),0) AS max_id FROM dmt.dim_metric_threshold) m
) a;