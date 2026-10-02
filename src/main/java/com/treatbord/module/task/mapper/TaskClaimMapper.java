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
     * 是否存在有效接取。
     * 注意：写路径（ClaimService.claim）已改为「先 selectOne 查既有行」再决定插行或复活，
     * 不再依赖本方法做防重复 —— 因为它排除了 CANCELLED/REJECTED，
     * 会漏掉「既有终态行 → insert 撞唯一键」这一情形。保留给读取类场景使用。
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

    /**
     * 重新接取：**复用既有行**（复活），而不是插新行。
     *
     * 为什么必须复用：唯一索引 uk_claim_task_user(task_id, user_id) 不含 status/deleted，
     * 同一用户同一任务在库里**只可能存在一行**。旧实现「取消后重新接取」直接 insert，
     * 必然撞唯一键，被全局异常处理器翻译成误导性的 409「状态冲突，非法流转」——
     * 用户被永久锁死在该任务外。
     *
     * 复活时一并清掉上一次的提交 / 审核痕迹，并把 reward 快照刷新为当前任务价
     * （与「接取时固化 reward」的既有约定一致）。
     */
    @Update("UPDATE task_claim SET status = #{toStatus}, reward = #{reward}, " +
            "submitted_at = NULL, reviewed_at = NULL, review_deadline = NULL, review_note = NULL " +
            "WHERE id = #{claimId} AND status = #{fromStatus} AND deleted = 0")
    int reactivate(@Param("claimId") Long claimId,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("reward") java.math.BigDecimal reward);
}