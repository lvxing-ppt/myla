package com.myla.result.service;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.result.entity.OrganismResult;

/**
 * MYLA 系统检验结果服务接口。
 * 定义检验结果管理的核心业务操作。
 * 包括从统一格式保存仪器解析结果（含细菌鉴定和药敏数据），
 * 以及对检验结果进行审核操作。
 */
public interface ResultService {

    /**
     * 保存仪器解析后的统一检验结果。
     * 在一个事务中完成以下操作：
     * - 保存细菌鉴定结果到 organism_result 表
     * - 批量保存药敏试验结果到 ast_result 表
     * - 发布检验结果接收领域事件到工作流模块
     *
     * @param unifiedResult 统一格式的检验结果，包含细菌信息和药敏数据列表
     * @return 保存后的细菌鉴定结果实体（包含生成的 ID 和 resultId）
     */
    OrganismResult saveResult(UnifiedResult unifiedResult);

    /**
     * 审核检验结果。
     * 校验结果状态（仅 PENDING 状态可审核），
     * 根据审核动作更新状态为 APPROVED 或 REJECTED，
     * 审核通过时发布领域事件通知工作流模块。
     *
     * @param id       检验结果主键ID
     * @param action   审核动作：APPROVE-批准，REJECT-拒绝
     * @param reviewer 审核人用户名
     */
    void reviewResult(Long id, String action, String reviewer);
}
