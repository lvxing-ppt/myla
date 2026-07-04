package com.myla.lis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.lis.entity.OutboundMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * MYLA 系统 LIS 出站消息数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 OutboundMessage 实体的 CRUD 操作方法。
 * 用于消息消费者持久化消息状态、更新重试信息和记录发送结果。
 */
@Mapper
public interface OutboundMessageMapper extends BaseMapper<OutboundMessage> {
}
