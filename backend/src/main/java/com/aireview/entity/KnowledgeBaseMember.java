package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_base_member")
public class KnowledgeBaseMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    private Long userId;

    /** 角色码，对应 {@link MemberRole#name()}。 */
    private String memberRole;

    private LocalDateTime createdAt;
}
