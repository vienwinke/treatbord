package com.treatbord.module.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.notify.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 通知 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 批量标记已读（仅当前用户） */
    @Update("UPDATE notification SET is_read = 1 " +
            "WHERE user_id = #{userId} AND is_read = 0 AND deleted = 0")
    int markAllRead(@Param("userId") Long userId);
}