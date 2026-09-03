package com.treatbord.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.task.entity.TaskClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 接取 Mapper。
 */
@Mapper
public interface TaskClaimMapper extends BaseMapper<TaskClaim> {

    /**
     * 是否存在有效接取（防重复，唯一索引兜底 + 显式校验）。
     */
    @Select("SELECT COUNT(*) FROM task_claim WHERE task_id = #{taskId} AND user_id = #{userId} " +
            "AND status IN ('CLAIMED','SUBMITTED','APPROVED') AND deleted = 0")
    int countActiveClaim(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * CAS 式接取状态流转（防竞态）。
     */
    @Update("UPDATE task_claim SET status = #{toStatus} " +
            "WHERE id = #{claimId} AND status = #{fromStatus} AND deleted = 0")
    int casStatus(@Param("claimId") Long claimId,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus);
}