-- 导入数据 dim_server

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

-- CREATE OR REPLACE TEMPORARY VIEW updated_and_added_records AS
CREATE OR REPLACE VIEW updated_and_added_records AS
SELECT
    -- s.`(creation_time|update_time|imported_time)`
    id, app_id, hostname, cpu_cores, memory, update_date
FROM
    src.bdp_master_server s
WHERE
    s.update_date >='@startDate@' AND s.update_date < '@endDate@';


INSERT OVERWRITE TABLE dmt.dim_server
SELECT
    *
FROM(
    SELECT
        -- m.`(valid_to|eff_flag)?+.+`,
        m.dwid, m.id, m.app_id, m.hostname, m.cpu_cores, m.memory, m.valid_from,
        CASE WHEN m.eff_flag = TRUE AND u.id IS NOT NULL THEN
            u.update_date
        ELSE
            m.valid_to
        END
        AS valid_to,
        CASE WHEN m.eff_flag = TRUE AND u.id IS NOT NULL THEN
            FALSE
        ELSE
            m.eff_flag
        END
        AS eff_flag
    FROM
        dmt.dim_server m
    LEFT JOIN
        updated_and_added_records u
    ON
        m.id = u.id

    UNION ALL

    SELECT
        ROW_NUMBER() over (ORDER BY 0) + m.max_id AS dwid,
        -- u.`(update_date)?+.+`,
        u.id, u.app_id, u.hostname, u.cpu_cores, u.memory,
        u.update_date AS valid_from,
        NULL AS valid_to,
        TRUE AS eff_flag
    FROM
        updated_and_added_records u
    CROSS JOIN
        (SELECT COALESCE(MAX(dwid), 0) AS max_id  FROM dmt.dim_server) m
) a;