package com.aireview.mapper;

import com.aireview.dto.TableStatVO;
import java.util.List;

/** 实例数据概览统计，只读。 */
public interface PersistenceStatMapper {

    /** information_schema 中指定表的行数估算与占用字节。 */
    List<TableStatVO> selectTableStats(List<String> tables);
}
