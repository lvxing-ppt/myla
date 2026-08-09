package com.mlms.oes.report.controller;

import com.mlms.oes.common.core.util.R;
import com.mlms.oes.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 报告管理 REST 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 根据样本条码下载检验报告 Excel。
     * <pre>GET /api/v1/reports/sample/{barcode}/excel</pre>
     */
    @GetMapping("/sample/{barcode}/excel")
    public void downloadSampleReport(@PathVariable String barcode,
                                      HttpServletResponse response) throws IOException {
        reportService.exportSampleReport(barcode, response);
    }

    /**
     * 根据样本条码生成检验报告到服务器本地文件。
     * <pre>POST /api/v1/reports/sample/{barcode}/generate</pre>
     *
     * @return 生成的文件路径
     */
    @PostMapping("/sample/{barcode}/generate")
    public R<String> generateSampleReport(@PathVariable String barcode) {
        try {
            String path = reportService.generateSampleReport(barcode);
            if (path == null) {
                return R.fail(com.mlms.oes.common.core.constant.ResultCode.NOT_FOUND, "未找到该样本的检验结果");
            }
            return R.ok(path);
        } catch (IOException e) {
            log.error("Report generation failed for barcode={}", barcode, e);
            return R.fail(com.mlms.oes.common.core.constant.ResultCode.INTERNAL_ERROR, "报告生成失败: " + e.getMessage());
        }
    }
}
