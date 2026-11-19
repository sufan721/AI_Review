package com.aireview.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 逻辑导出/导入：表名由白名单枚举在 XML 的 {@code <choose>} 分支里决定，
 * 绝不接受外部传入的表名参与 SQL 拼接（避免注入与列顺序漂移）。
 */
public interface BackupDumpMapper {

    /** 按白名单表名读取整张表；行按 camelCase 别名返回。 */
    List<Map<String, Object>> selectRows(@Param("table") String table);

    /** 恢复前清空整张表。 */
    int deleteRows(@Param("table") String table);

    /** 分批写回，rows 的 key 与 selectRows 的别名一致。 */
    int insertRows(@Param("table") String table, @Param("rows") List<Map<String, Object>> rows);
}
