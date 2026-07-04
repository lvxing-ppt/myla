package com.myla.result.controller;

import com.myla.common.core.util.R;
import com.myla.result.entity.OrganismResult;
import com.myla.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MYLA 系统检验结果 REST 控制器。
 * 提供检验结果的审核操作 HTTP 接口。
 * 所有接口均以 /api/v1/results 为前缀。
 * 支持对细菌鉴定/药敏结果进行审核（批准或拒绝）。
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    /**
     * 审核检验结果。
     * HTTP 方法：PUT
     * 接口路径：/api/v1/results/{id}/review
     * 对指定 ID 的检验结果执行审核操作（批准或拒绝），
     * 审核前校验结果状态（仅允许审核 PENDING 状态的结果），
     * 审核通过后发布领域事件通知下游模块。
     *
     * @param id   检验结果主键ID
     * @param body 请求体，包含 "action"（APPROVE-批准 / REJECT-拒绝）
     *             和 "reviewer"（审核人，默认为 SYSTEM）
     * @return 操作成功返回 OK
     */
    @PutMapping("/{id}/review")
    public R<Void> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        resultService.reviewResult(
            id,
            body.get("action"),
            body.getOrDefault("reviewer", "SYSTEM")
        );
        return R.ok();
    }
}
