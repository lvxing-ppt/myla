package com.myla.report.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 报告服务接口。
 */
public interface ReportService {

    /**
     * 根据样本条码生成检验报告 Excel 并写入 HTTP 响应。
     *
     * @param barcode  样本条码
     * @param response HTTP 响应（用于文件下载）
     */
    void exportSampleReport(String barcode, HttpServletResponse response) throws IOException;

    /**
     * 根据样本条码生成检验报告 Excel，返回文件路径。
     *
     * @param barcode 样本条码
     * @return 生成的文件绝对路径
     */
    String generateSampleReport(String barcode) throws IOException;
}
