# 导入数据 metric_threshold (tmp -> src)
INSERT INTO TABLE src.bdp_master_metric_threshold PARTITION(update_date)
SELECT
    server_id,
    metric_name,
    amber_threshold,
    red_threshold,
    CAST(creation_time AS TIMESTAMP) AS creation_time,
    CAST(update_time AS TIMESTAMP) AS update_time,
    CURRENT_TIMESTAMP AS imported_time,
    CAST(CAST(update_time AS DATE) AS STRING) AS update_date
FROM
    tmp.bdp_master_metric_threshold;