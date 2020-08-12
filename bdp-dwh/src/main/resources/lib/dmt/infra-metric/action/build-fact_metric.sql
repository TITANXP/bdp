-- 导入数据 metric事实表（dwh -> dmt)

INSERT OVERWRITE TABLE dmt.fact_metric PARTITION(creation_date)
SELECT
    m.id, a.dwid AS app_dwid, s.dwid AS server_dwid, mi.dwid AS metric_index_dwid, d.dwid AS hour_dwid, m.`timestamp`, m.value, m.creation_date
FROM
    dwh.bdp_metric_metric m
    JOIN dmt.dim_server s on m.hostname = s.hostname
    JOIN dmt.dim_app a on a.id = s.app_id
    JOIN dmt.dim_metric_index mi on mi.name = m.name
    JOIN dmt.dim_hour d on FROM_UNIXTIME(UNIX_TIMESTAMP(m.`timestamp`), 'yyyy-MM-dd HH:00:00') = d.db_hour
WHERE
    m.`timestamp` >= s.valid_from AND (m.`timestamp` < s.valid_to OR s.valid_to IS NULL) AND
    m.`timestamp` >= a.valid_from AND (m.`timestamp` < a.valid_to OR a.valid_to IS NULL) AND
    m.`timestamp` >= mi.valid_from AND (m.`timestamp` < mi.valid_to OR mi.valid_to IS NULL) AND
    m.creation_date >= '@startDate@' AND m.creation_date < '@endDate@';