package com.treatbord.module.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.submission.entity.TaskSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提交凭证 Mapper。
 */
@Mapper
public interface TaskSubmissionMapper extends BaseMapper<TaskSubmission> {

    /** 查询某接取的最新一条有效提交 */
    @Select("SELECT * FROM task_submission WHERE claim_id = #{claimId} AND deleted = 0 " +
            "ORDER BY submit_time DESC, id DESC LIMIT 1")
    TaskSubmission selectLatestByClaimId(@Param("claimId") Long claimId);
}