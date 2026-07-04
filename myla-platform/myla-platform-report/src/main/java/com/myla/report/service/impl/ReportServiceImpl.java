package com.myla.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.report.service.ReportService;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告服务实现 — 使用 Apache POI 生成 Excel 检验报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== Excel 样式常量 ====================

    @Override
    public void exportSampleReport(String barcode, HttpServletResponse response) throws IOException {
        // 按 barcode 尝试匹配（从 raw_message 字段模糊查找）
        OrganismResult orgResult = findLatestByBarcode(barcode);
        if (orgResult == null) {
            response.setStatus(404);
            response.getWriter().write("{\"error\":\"未找到样本 " + barcode + " 的检验结果\"}");
            return;
        }

        Workbook wb = buildReportWorkbook(orgResult);

        String filename = "检验报告_" + barcode + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        wb.write(response.getOutputStream());
        wb.close();
        log.info("Report exported: barcode={}, organism={}", barcode, orgResult.getOrganismName());
    }

    @Override
    public String generateSampleReport(String barcode) throws IOException {
        OrganismResult orgResult = findLatestByBarcode(barcode);
        if (orgResult == null) return null;

        Workbook wb = buildReportWorkbook(orgResult);
        String dir = System.getProperty("java.io.tmpdir") + "myla-reports/";
        new File(dir).mkdirs();
        String path = dir + "report_" + barcode + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        try (FileOutputStream fos = new FileOutputStream(path)) {
            wb.write(fos);
        }
        wb.close();
        log.info("Report generated: {}", path);
        return path;
    }

    // ==================== Excel 构建 ====================

    private Workbook buildReportWorkbook(OrganismResult orgResult) {
        Workbook wb = new XSSFWorkbook();

        // 样式
        CellStyle titleStyle = createStyle(wb, 16, true, HorizontalAlignment.CENTER);
        CellStyle headerStyle = createStyle(wb, 11, true, HorizontalAlignment.LEFT);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle labelStyle = createStyle(wb, 11, true, HorizontalAlignment.RIGHT);
        CellStyle valueStyle = createStyle(wb, 11, false, HorizontalAlignment.LEFT);
        CellStyle tableHeader = createStyle(wb, 10, true, HorizontalAlignment.CENTER);
        tableHeader.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeader.setBorderTop(BorderStyle.THIN);
        tableHeader.setBorderBottom(BorderStyle.THIN);
        tableHeader.setBorderLeft(BorderStyle.THIN);
        tableHeader.setBorderRight(BorderStyle.THIN);
        CellStyle tableCell = createStyle(wb, 10, false, HorizontalAlignment.CENTER);
        tableCell.setBorderTop(BorderStyle.THIN);
        tableCell.setBorderBottom(BorderStyle.THIN);
        tableCell.setBorderLeft(BorderStyle.THIN);
        tableCell.setBorderRight(BorderStyle.THIN);

        // ---- Sheet 1: 检验报告 ----
        Sheet sheet = wb.createSheet("检验报告");

        int r = 0; // 当前行
        // 标题行
        Row titleRow = sheet.createRow(r++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("微生物检验报告单");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        titleRow.setHeightInPoints(30);

        // 空行
        r++;

        // === 样本信息区域 ===
        addSection(sheet, r++, "样本信息", headerStyle);
        r = addKV(sheet, r, "样本条码", parseBarcode(orgResult), labelStyle, valueStyle);
        r = addKV(sheet, r, "检验仪器", orgResult.getInstrumentId(), labelStyle, valueStyle);
        r = addKV(sheet, r, "检验时间", orgResult.getTestTime() != null ? orgResult.getTestTime().format(DT_FMT) : "", labelStyle, valueStyle);
        r = addKV(sheet, r, "结果编号", orgResult.getResultId(), labelStyle, valueStyle);
        r++;

        // === 菌种鉴定区域 ===
        addSection(sheet, r++, "菌种鉴定结果", headerStyle);
        r = addKV(sheet, r, "菌种名称", orgResult.getOrganismName(), labelStyle, valueStyle);
        r = addKV(sheet, r, "菌种编码", orgResult.getOrganismCode(), labelStyle, valueStyle);
        r = addKV(sheet, r, "鉴定置信度", orgResult.getIdentificationPercent() != null ? orgResult.getIdentificationPercent() + "%" : "", labelStyle, valueStyle);
        r = addKV(sheet, r, "审核状态", orgResult.getReviewStatus(), labelStyle, valueStyle);
        r++;

        // === 药敏结果区域 ===
        List<AstResult> astResults = astResultMapper.selectList(
                new LambdaQueryWrapper<AstResult>()
                        .eq(AstResult::getOrganismResultId, orgResult.getId()));
        if (!astResults.isEmpty()) {
            addSection(sheet, r++, "药敏试验结果 (" + astResults.size() + " 项)", headerStyle);

            // 表头
            Row thRow = sheet.createRow(r++);
            String[] cols = {"抗生素名称", "MIC 值", "单位", "仪器判读", "最终判读", "专家规则备注"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = thRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(tableHeader);
            }

            // 数据行
            for (AstResult ast : astResults) {
                Row tr = sheet.createRow(r++);
                setCell(tr, 0, ast.getAntibioticName(), tableCell);
                setCell(tr, 1, ast.getMicValue() != null ? String.valueOf(ast.getMicValue()) : "", tableCell);
                setCell(tr, 2, ast.getMicUnit(), tableCell);
                setCell(tr, 3, ast.getMachineSir(), tableCell);
                setCell(tr, 4, ast.getFinalSir(), tableCell);
                setCell(tr, 5, ast.getExpertRuleComment(), tableCell);
            }
        }

        // === 页脚 ===
        r++;
        Row footerRow = sheet.createRow(r);
        Cell footerCell = footerRow.createCell(0);
        footerCell.setCellValue("报告生成时间: " + LocalDateTime.now().format(DT_FMT) +
                "  |  本报告仅供临床参考");
        footerCell.setCellStyle(createStyle(wb, 9, false, HorizontalAlignment.LEFT));

        // 列宽
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 3000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 8000);
        sheet.setColumnWidth(6, 4000);

        return wb;
    }

    // ==================== 辅助方法 ====================

    /** 在 raw_message 中模糊查找包含指定条码的最新 organism_result */
    private OrganismResult findLatestByBarcode(String barcode) {
        List<OrganismResult> list = organismResultMapper.selectList(
                new LambdaQueryWrapper<OrganismResult>()
                        .like(OrganismResult::getRawMessage, barcode)
                        .orderByDesc(OrganismResult::getCreatedAt)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 从 raw_message 中尝试提取条码 */
    private String parseBarcode(OrganismResult r) {
        if (r.getRawMessage() != null) {
            // ASTM 格式: O|1|barcode|...
            int start = r.getRawMessage().indexOf("O|1|");
            if (start >= 0) {
                int end = r.getRawMessage().indexOf("|", start + 4);
                if (end > start) return r.getRawMessage().substring(start + 4, end);
            }
        }
        return "N/A";
    }

    private void addSection(Sheet sheet, int row, String text, CellStyle style) {
        Row r = sheet.createRow(row);
        Cell c = r.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 6));
    }

    private int addKV(Sheet sheet, int row, String key, String val, CellStyle ks, CellStyle vs) {
        Row r = sheet.createRow(row);
        Cell k = r.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(ks);
        Cell v = r.createCell(1);
        v.setCellValue(val != null ? val : "");
        v.setCellStyle(vs);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 1, 3));
        return row + 1;
    }

    private void setCell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private CellStyle createStyle(Workbook wb, int fontSize, boolean bold, HorizontalAlignment align) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) fontSize);
        font.setBold(bold);
        font.setFontName("微软雅黑");
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
