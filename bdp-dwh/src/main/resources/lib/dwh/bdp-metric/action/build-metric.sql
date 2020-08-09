# 导入数据 metric (src->dwh)
INSERT OVERWRITE TABLE dwh.bdp_metric_metric PARTITION(creation_date)
SELECT
    id,
    name,
    hostname,
    value,
    creation_time,
    imported_time,
    CAST(CAST(`timestamp` AS DATE) AS STRING) AS creation_date
FROM
    dwh.bdp_metric_metric
WHERE
    creation_date >= '@startDate@' and creation_date < '@endDate@';