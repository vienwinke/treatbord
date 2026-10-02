package com.treatbord.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.file.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {

    /**
     * 绑定 mediaCheckAsync 的 trace_id（提交检测成功后立即落库）。
     * 这是回调能定位到文件的唯一线索：不落库，回调就无处可写。
     */
    @Update("UPDATE file SET sec_trace_id = #{traceId} " +
            "WHERE id = #{fileId} AND deleted = 0")
    int bindSecTraceId(@Param("fileId") Long fileId, @Param("traceId") String traceId);

    /**
     * 按 trace_id 回填内容安全结果（微信消息推送回调）。
     *
     * ⚠️ 带 `sec_status <> 2` 守卫：**一旦判为违规就不允许被后续推送降级**。
     * 回调整理/重放/伪造（签名通过的前提下）都不该把已违规的文件放回来。
     */
    @Update("UPDATE file SET sec_status = #{secStatus}, sec_checked_at = NOW() " +
            "WHERE sec_trace_id = #{traceId} AND deleted = 0 AND sec_status <> 2")
    int updateSecResultByTraceId(@Param("traceId") String traceId,
                                 @Param("secStatus") int secStatus);
}