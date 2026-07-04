package com.myla.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.workflow.entity.CriticalValueAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * MYLA 系统危急值预警数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 CriticalValueAlert 实体的 CRUD 操作方法。
 * 用于危急值预警记录的持久化操作，支持按通知状态和预警级别查询。
 */
@Mapper
public interface CriticalValueAlertMapper extends BaseMapper<CriticalValueAlert> {
}
