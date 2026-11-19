package com.aireview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aireview.entity.AttachmentBlob;
import org.apache.ibatis.annotations.Param;

/**
 * 数据库存储模式的字节读写。读取走 BaseMapper.selectById（实体 content: byte[] 由 MyBatis
 * 按列映射）；插入单条专用 SQL 写在 XML 里，列名全部显式、无 ${} 拼接。
 *
 * <p>注意：不要定义返回 byte[] 的 select —— MyBatis-Plus 会把数组返回类型当成多值查询
 * （executeForMany），单参数 convertToArray 抛 argument type mismatch。
 */
public interface AttachmentBlobMapper extends BaseMapper<AttachmentBlob> {

    /** 插入一条二进制记录。 */
    int insertBlob(@Param("blob") AttachmentBlob blob);
}
