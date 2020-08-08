# 导入数据 server (tmp -> src)
INSERT OVERWRITE TABLE src.bdp_master_server PARTITION(update_date)
SELECT
    id,
    app_id,
    hostname,
    cpu_cores,
    memory,
    CAST(creation_time AS TIMESTAMP) AS creation_time,
    CAST(update_time AS TIMESTAMP) AS update_time,
    CURRENT_TIMESTAMP AS imported_time,
    CAST(CAST(update_time AS DATE) AS STRING) AS update_date
from
    tmp.bdp_master_server;