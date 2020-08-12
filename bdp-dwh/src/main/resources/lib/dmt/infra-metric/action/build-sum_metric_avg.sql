-- 导入数据 metric均值汇总表（dwh -> dmt)

-- 注册UDF
CREATE TEMPORARY FUNCTION gen_rag AS 'org.bdp.dwh.udf.GenRag' USING JAR '${app.home}/jar/${project.build.finalName}.jar';

INSERT OVERWRITE TABLE dmt.sum_metric_avg PARTITION(creation_date)
SELECT
    ma.app_dwid,
    ma.server_dwid,
    ma.metric_index_dwid,
    t.dwid AS metric_threshold_dwid,
    ma.hour_dwid,
    ma.avg_value,
    gen_rag(ma.avg_value, t.amber_threshold, t.red_threshold) AS rag,
    ma.creation_date
FROM (
     SELECT m.app_dwid,
            m.server_dwid,
            m.metric_index_dwid,
            m.hour_dwid,
            CAST(ROUND(AVG(m.`value`)) AS INT) AS avg_value,
            m.creation_date
     FROM dmt.fact_metric m
     WHERE m.creation_date >= '@startDate@' AND m.creation_date < '@endDate'
     GROUP BY m.creation_date, m.app_dwid, m.server_dwid, m.metric_index_dwid, m.hour_dwid
)ma
JOIN dmt.dim_server s ON s.dwid = ma.server_dwid
JOIN dmt.dim_metric_index dm ON dm.dwid = ma.metric_index_dwid
-- 由于metric事实表中没有metric_threshold的dwid，所以需要join server和metric_index来确定metric_threshold
JOIN dmt.dim_metric_threshold t ON t.server_id = s.id AND t.metric_name = dm.name
WHERE
    s.valid_from <= FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) AND (s.valid_to > FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) OR s.valid_to IS NULL) AND
    dm.valid_from <= FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) AND (dm.valid_to > FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) OR dm.valid_to IS NULL) AND
    t.valid_from <= FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) AND (t.valid_to > FROM_UNIXTIME(UNIX_TIMESTAMP(ma.creation_date, 'yyyy-mm-dd')) OR t.valid_to IS NULL);