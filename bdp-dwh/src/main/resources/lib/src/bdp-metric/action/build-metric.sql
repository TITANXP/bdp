-- 导入数据 metric (tmp -> src)
INSERT OVERWRITE src.bdp_metric_metric PARTITION(creation_date)
select
    id,
    name,
    hostname,
    value,
    CAST(`timestamp` AS TIMESTAMP) `timestamp`,
    CURRENT_TIMESTAMP AS imported_time, # Sqoop自动生成表时会将日期时间映射为string，所以在进入src层时需要转换为原来的类型
    CAST(CAST (`timestamp` AS DATE) AS STRING) AS creation_date # 转为string是为了用此字段来分区
from
    tmp.bdp_metric_metric;