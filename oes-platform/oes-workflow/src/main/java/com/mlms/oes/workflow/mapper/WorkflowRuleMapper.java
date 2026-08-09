package com.mlms.oes.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlms.oes.workflow.entity.WorkflowRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * MLMS 系统工作流规则数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 WorkflowRule 实体的 CRUD 操作方法。
 * 用于工作流规则的持久化操作，LabEventConsumer 通过此 Mapper 查询匹配的已启用规则。
 */
@Mapper
public interface WorkflowRuleMapper extends BaseMapper<WorkflowRule> {
}
