-- V36__migrate_assembly_config_and_drop_segments.sql
-- 将 assembly_config 中的 segmentId 引用转换为内联模式，然后删除段落库相关表

-- ═══════════════════════════════════════════════════════════
-- 步骤 1: 数据迁移 — 将 segmentId 引用解析为内联数据
-- 对于存在的 segment 记录，填充 filePath/name/segmentType
-- 对于不存在的 segmentId 引用，标记为 enabled=false，名称前缀 INVALID_SEGMENT_
-- ═══════════════════════════════════════════════════════════

UPDATE templates t
SET assembly_config = (
    SELECT jsonb_build_object(
        'segments',
        COALESCE(
            (SELECT jsonb_agg(
                CASE
                    WHEN s.id IS NOT NULL THEN
                        jsonb_build_object(
                            'filePath', s.file_path,
                            'name', s.name,
                            'segmentType', s.segment_type,
                            'position', (elem->>'position')::int,
                            'enabled', COALESCE((elem->>'enabled')::boolean, true),
                            'pageBreakBefore', COALESCE((elem->>'pageBreakBefore')::boolean, false),
                            'conditionExpression', elem->>'conditionExpression',
                            'dataScope', elem->'dataScope'
                        )
                    ELSE
                        jsonb_build_object(
                            'filePath', '',
                            'name', 'INVALID_SEGMENT_' || (elem->>'segmentId'),
                            'segmentType', NULL,
                            'position', (elem->>'position')::int,
                            'enabled', false,
                            'pageBreakBefore', COALESCE((elem->>'pageBreakBefore')::boolean, false),
                            'conditionExpression', elem->>'conditionExpression',
                            'dataScope', elem->'dataScope'
                        )
                END
                ORDER BY (elem->>'position')::int
            )
            FROM jsonb_array_elements(t.assembly_config->'segments') AS elem
            LEFT JOIN segments s ON s.id = (elem->>'segmentId')::bigint
            ),
            '[]'::jsonb
        )
    )
)
WHERE t.template_type = 'COMPOSITE'
  AND t.assembly_config IS NOT NULL
  AND t.assembly_config::text != 'null'
  AND jsonb_typeof(t.assembly_config->'segments') = 'array';

-- ═══════════════════════════════════════════════════════════
-- 步骤 2: 清理 permissions 表中的 SEGMENT 类型记录
-- ═══════════════════════════════════════════════════════════

DELETE FROM permissions WHERE resource_type = 'SEGMENT';

-- ═══════════════════════════════════════════════════════════
-- 步骤 3: 按依赖顺序删除段落库相关表
-- ═══════════════════════════════════════════════════════════

DROP TABLE IF EXISTS segment_favorites CASCADE;
DROP TABLE IF EXISTS segment_test_data CASCADE;
DROP TABLE IF EXISTS segment_reviews CASCADE;
DROP TABLE IF EXISTS segment_tag_mappings CASCADE;
DROP TABLE IF EXISTS segment_versions CASCADE;
DROP TABLE IF EXISTS segments CASCADE;
