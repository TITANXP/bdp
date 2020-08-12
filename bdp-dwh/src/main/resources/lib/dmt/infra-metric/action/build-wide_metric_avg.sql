-- 导入数据 metric宽表（dwh -> dmt)

INSERT OVERWRITE TABLE dmt.wide_metric_avg PARTITION(creation_date)
SELECT
    a.dwid AS app_dwid,
    a.name AS app_name,
    a.description AS app_description,
    a.version AS app_version,
    s.dwid AS server_dwid,
    s.hostname AS server_hostname,
    s.cpu_cores AS server_cpu_cores,
    s.memory AS server_memory,
    m.dwid AS metric_index_dwid,
    m.name AS metric_name,
    m.description AS metric_description,
    m.category AS metric_category,
    t.dwid AS metric_threshold_dwid,
    t.amber_threshold AS amber_threshold,
    t.red_threshold AS red_threshold,
    h.dwid AS hour_dwid,
    h.db_date AS db_date,
    h.db_hour as db_hour,
    h.year as year,
    h.month as month,
    h.day as day,
    h.hour as hour,
    h.quarter as quarter,
    h.week as week,
    h.day_name as day_name,
    h.month_name as month_name,
    h.weekend_flag as weekend_flag,
    avg_value,
    rag,
    creation_date
FROM
    dmt.sum_metric_avg ma
        JOIN dmt.dim_app a ON a.dwid = ma.app_dwid
        JOIN dmt.dim_server s ON s.dwid = ma.server_dwid
        JOIN dmt.dim_metric_index m ON m.dwid = ma.metric_index_dwid
        -- TODO:dwid?
        JOIN dmt.dim_metric_threshold t ON t.server_id = s.id AND t.metric_name = m.name
        JOIN dmt.dim_hour h ON h.dwid = ma.hour_dwid
WHERE
        ma.creation_date >= '@startDate@' AND ma.creation_date <= '@endDate@';