package com.treatbord.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treatbord.common.BusinessException;
import com.treatbord.common.PageResult;
import com.treatbord.common.ResultCode;
import com.treatbord.module.notify.entity.Notification;
import com.treatbord.module.notify.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务（docs/API_DESIGN.md §8）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 发送站内通知（写库失败不阻塞主流程）。
     */
    public void notify(Long userId, String type, String title, String content, Long bizId) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizId(bizId);
            n.setIsRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("站内通知发送失败 userId={} type={}", userId, type, e);
        }
    }

    /**
     * 通知列表（分页，可筛选已读状态）。
     */
    public PageResult<Notification> list(Long userId, Integer isRead, long page, long pageSize) {
        Page<Notification> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> qw = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .orderByDesc(Notification::getCreateTime);
        Page<Notification> result = notificationMapper.selectPage(p, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    /**
     * 标记单条已读（幂等）。
     */
    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "通知不存在或无权操作");
        }
        if (Integer.valueOf(1).equals(n.getIsRead())) {
            return; // 幂等
        }
        Notification up = new Notification();
        up.setId(id);
        up.setIsRead(1);
        notificationMapper.updateById(up);
    }

    /**
     * 全部标记已读。
     */
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }
}