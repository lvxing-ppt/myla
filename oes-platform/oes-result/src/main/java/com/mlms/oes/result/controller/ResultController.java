package com.mlms.oes.result.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mlms.oes.common.core.constant.ResultCode;
import com.mlms.oes.common.core.util.R;
import com.mlms.oes.result.entity.AstResult;
import com.mlms.oes.result.entity.OrganismResult;
import com.mlms.oes.result.mapper.AstResultMapper;
import com.mlms.oes.result.mapper.OrganismResultMapper;
import com.mlms.oes.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 检验结果 REST 控制器 — 列表/详情/三级审核。
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;
    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;

    @GetMapping
    public R<Page<OrganismResult>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OrganismResult> p = new Page<>(page, size);
        return R.ok(organismResultMapper.selectPage(p,
            new LambdaQueryWrapper<OrganismResult>()
                .eq(OrganismResult::getReviewStatus, status)
                .orderByDesc(OrganismResult::getCreatedAt)));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        OrganismResult org = organismResultMapper.selectById(id);
        if (org == null) return R.fail(ResultCode.NOT_FOUND);
        List<AstResult> astList = astResultMapper.selectList(
            new LambdaQueryWrapper<AstResult>().eq(AstResult::getOrganismResultId, id));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organismResult", org);
        data.put("astResults", astList);
        return R.ok(data);
    }

    /**
     * 三级审核 — 角色+状态机双重校验。
     * PENDING →(TECHNICIAN) TECH_APPROVED →(REVIEWER) CLINICAL_APPROVED →(DIRECTOR) RELEASED
     */
    @PutMapping("/{id}/review")
    public R<Void> review(@PathVariable Long id, @RequestBody Map<String, String> body,
                           @RequestHeader(value = "X-Reviewer", defaultValue = "SYSTEM") String reviewer,
                           @RequestHeader(value = "X-Role", defaultValue = "ROLE_TECHNICIAN") String role) {
        resultService.reviewResult(id,
            body.get("action"),
            body.getOrDefault("reviewer", reviewer),
            role);
        return R.ok();
    }
}
