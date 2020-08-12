-- 导入数据 app (src -> dwh)

-- 便于直接将select的结果覆盖回dwh.bdp_master_server,
-- 因为select语句选择的一部分结果集来自dwh.bdp_master_server,如果直接覆盖回dwh.bdp_master_server,在spark-sql中是不允许的
SET spark.sql.hive.convertMetastoreParquet=false;
-- 开启后可以使用正则选择字段
SET spark.sql.parser.quotedRegexColumnNames=true;

INSERT OVERWRITE TABLE dwh.bdp_master_app
SELECT
    -- `(row_num|oc)?+.+` -- 除row_num和oc外的所有字段
    id, name, description, version, creation_time, update_time, imported_time
FROM(
    SELECT
        *,
        ROW_NUMBER() OVER (      -- 基于id进行分组，并在分组内根据更新时间和oc进行降序排列，然后赋予一个序号
            PARTITION BY id      -- 通过row_number()函数，可以很容易找到合并之后id相同的记录中最新的一天，这条记录的row_num=1
            ORDER BY update_time DESC, oc DESC
        ) AS row_num
    FROM(
        -- 将SRC层的增量数据，与DWH层的全量数据进行合并
        SELECT
            *, 0 AS oc  -- oc (Ordering Column 排序列)
        FROM
            dwh.bdp_master_app
        UNION ALL
        SELECT
            -- `(update_date)?+.+`, 1 AS oc -- 除update_date外的所有字段,和oc
            id, name, description, version, creation_time, update_time, imported_time, 1 AS oc
        FROM
            src.bdp_master_app
        WHERE
            update_date >= '@startDate@' AND update_date < '@endDate@'
    ) a
) b
WHERE row_num = 1;

-- DWH层的全量数据oc为0，SRC层增量数据oc为1，因为如果出现数据重复导入的情况，为了避免数据筛选的不确定性，我们永远让SRC层的增量数据拥有更高的被选择权。