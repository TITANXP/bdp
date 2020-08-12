-- 导入数据 dim_app

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

-- 在增量采集模式下，SRC层增量表中的每日分区数据就是增量数据
-- 需要在SQL中反复引用这个单日的数据集，并且不需要使用creation_tine、update_time、imported_time这些字段
-- 所以定义临时视图将某一天的“更新和新增数据”作为一个独立的数据集使用
-- CREATE OR REPLACE TEMPORARY VIEW updated_and_added_records AS
CREATE OR REPLACE VIEW updated_and_added_records AS
SELECT
    -- s.`(creation_time|update_time|imported_time)?+.+`
    id, name, description, version, update_date
FROM
    src.bdp_master_app s
WHERE
    s.update_date >= '@startDate@' AND s.update_date < '@endDate@';


-- 整体上把针对DMT层的全量表，和SRC层的增量表的操作分开，然后将两张表的处理结果union，就是最终结果集。
INSERT OVERWRITE TABLE dmt.dim_app
SELECT
    *
FROM(
    -- 针对DMT全量表的操作
    --  操作1.1: 将DMT全量表中的“更新前的数据”复制到结果集，失效日期取SRC增量表中记录的更新时间，有效标记位置为"false"
    --  操作1.2: 将DMT全量表中的“变更历史记录”复制到结果集，不做任何修改
    SELECT
        -- m.`(valid_to|eff_flag)?+.+`,
        m.dwid, m.id, m.name, m.description, m.version, m.valid_from,
        -- 处理失效时间
        CASE WHEN m.eff_flag = TRUE AND u.id IS NOT NULL THEN -- 情况2
            u.update_date -- 操作1.1
        ELSE -- 情况 4、5
            m.valid_to -- 操作1.2
        END
        AS valid_to,
        -- 处理标志位
        CASE WHEN m.eff_flag = TRUE AND u.id IS NOT NULL THEN -- 情况2
            FALSE -- 操作1.1
        ELSE -- 情况 4、5
            m.eff_flag -- 操作1.2
        END
        AS eff_flag
    FROM
        dmt.dim_app m
    LEFT JOIN
        updated_and_added_records u
    ON
        m.id = u.id

    UNION ALL

    -- 操作2: 针对SRC增量表(新增和变更数据集)的操作: 将增量数据复制到结果集，生效日期取增量记录里的更新时间，有效标记位置为"true"
    SELECT
        ROW_NUMBER() OVER(ORDER BY 0) + m.max_id AS dwid, -- 在最大dwid的基础上累加，从而为新记录生成dwid
        -- u.`(update_date)?+.+`,
        u.id, u.name, u.description, u.version,
        u.update_date AS valid_from, -- 更新日期设为生效日期
        NULL AS valid_to, -- 失效日期为null
        TRUE AS eff_flag --  生效标志位设为true
    FROM
        updated_and_added_records u
    CROSS JOIN
        -- 查出当前最大dwid，没有则取0
        (SELECT COALESCE(MAX(dwid), 0) AS max_id FROM dmt.dim_app) m
) a;
