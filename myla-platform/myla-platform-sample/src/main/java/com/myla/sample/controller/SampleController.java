package com.myla.sample.controller;

import com.myla.common.core.util.R;
import com.myla.sample.entity.Sample;
import com.myla.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MYLA 系统样本管理 REST 控制器。
 * 提供样本登记、查询、状态变更等 HTTP 接口。
 * 所有接口均以 /api/v1/samples 为前缀。
 * 支持按主键 ID、业务 sampleId、条码 barcode 等多种方式查询样本信息，
 * 以及样本状态的流转变更操作。
 */
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    /**
     * 登记新样本。
     * HTTP 方法：POST
     * 接口路径：/api/v1/samples
     * 生成实验室内部编号（格式：yyyyMMdd-xxxx），
     * 记录样本流转日志，并发布样本登记领域事件。
     *
     * @param sample 样本信息（条码、患者信息、标本类型等）
     * @return 登记成功的样本实体（含生成的内部编号）
     */
    @PostMapping
    public R<Sample> register(@RequestBody Sample sample) {
        return R.ok(sampleService.register(sample));
    }

    /**
     * 根据主键 ID 查询样本。
     * HTTP 方法：GET
     * 接口路径：/api/v1/samples/{id}
     *
     * @param id 样本数据库主键ID
     * @return 样本实体
     */
    @GetMapping("/{id}")
    public R<Sample> getById(@PathVariable Long id) {
        return R.ok(sampleService.getById(id));
    }

    /**
     * 根据业务编号（sampleId）查询样本。
     * HTTP 方法：GET
     * 接口路径：/api/v1/samples/sampleId/{sampleId}
     *
     * @param sampleId 样本业务编号（格式：yyyyMMdd-xxxx）
     * @return 样本实体
     */
    @GetMapping("/sampleId/{sampleId}")
    public R<Sample> getBySampleId(@PathVariable String sampleId) {
        return R.ok(sampleService.getBySampleId(sampleId));
    }

    /**
     * 根据条码查询样本。
     * HTTP 方法：GET
     * 接口路径：/api/v1/samples/barcode/{barcode}
     *
     * @param barcode 样本条码（通常为医院系统的条码编号）
     * @return 样本实体
     */
    @GetMapping("/barcode/{barcode}")
    public R<Sample> getByBarcode(@PathVariable String barcode) {
        return R.ok(sampleService.getByBarcode(barcode));
    }

    /**
     * 变更样本状态。
     * HTTP 方法：PUT
     * 接口路径：/api/v1/samples/{id}/status
     * 对指定 ID 的样本进行状态流转操作，
     * 校验当前状态（fromStatus）与目标状态（toStatus）的合法性，
     * 记录流转日志，并发布对应的领域事件。
     *
     * @param id   样本主键ID
     * @param body 请求体，包含 "fromStatus"（当前状态）、"toStatus"（目标状态）、
     *             "operator"（操作人，可选，默认 SYSTEM）、"comment"（备注，可选）
     * @return 操作成功返回 OK
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sampleService.updateStatus(
            id,
            body.get("fromStatus"),
            body.get("toStatus"),
            body.getOrDefault("operator", "SYSTEM"),
            body.getOrDefault("comment", "")
        );
        return R.ok();
    }
}
