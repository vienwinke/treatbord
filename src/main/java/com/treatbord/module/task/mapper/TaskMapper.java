package com.treatbord.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务 Mapper：原子扣减（防超卖）是关键操作。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 原子扣减名额（防超卖核心，docs/API_DESIGN.md §4.1）：
     * 仅当任务仍在接取期（OPEN/IN_PROGRESS）、未满员、且未过接取/完成截止（DB NOW()）时 +1，
     * 返回 0 行表示不可接取。
     * 说明：OPEN=尚未有人接取，IN_PROGRESS=有人接取但未满员，两者都应可继续扣减。
     */
    @Update("UPDATE task SET claimed_count = claimed_count + 1, version = version + 1 " +
            "WHERE id = #{taskId} AND claimed_count < quota " +
            "AND status IN ('OPEN', 'IN_PROGRESS') " +
            "AND claim_deadline > NOW() AND deadline > NOW() AND deleted = 0")
    int incrementClaimedCount(@Param("taskId") Long taskId);

    /**
     * 仅校验「当前是否可接取」，**不改名额** —— 专供「驳回后重新接取」这一条路径：
     * 该用户的名额在驳回时并没有回减（回减只发生在取消 / 超时取消），
     * 所以复活时不能再扣一次，否则 claimed_count 会虚高。
     * 判定条件刻意与 incrementClaimedCount 保持一致（含用 DB NOW() 判截止）。
     */
    @Select("SELECT COUNT(*) FROM task WHERE id = #{taskId} " +
            "AND status IN ('OPEN', 'IN_PROGRESS') " +
            "AND claim_deadline > NOW() AND deadline > NOW() AND deleted = 0")
    int countClaimable(@Param("taskId") Long taskId);

    /**
     * 名额回减（取消接取时），仅当已计数时 -1。
     */
    @Update("UPDATE task SET claimed_count = claimed_count - 1, version = version + 1 " +
            "WHERE id = #{taskId} AND claimed_count > 0 AND deleted = 0")
    int decrementClaimedCount(@Param("taskId") Long taskId);

    /**
     * CAS 式状态流转（防竞态）：仅当仍是旧状态时更新。
     */
    @Update("UPDATE task SET status = #{toStatus}, version = version + 1 " +
            "WHERE id = #{taskId} AND status = #{fromStatus} AND deleted = 0")
    int casStatus(@Param("taskId") Long taskId,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus);

}