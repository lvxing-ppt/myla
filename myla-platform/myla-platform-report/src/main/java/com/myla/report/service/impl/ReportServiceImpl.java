package com.myla.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.report.service.ReportService;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.sample.entity.Sample;
import com.myla.sample.mapper.SampleMapper;
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
    private final SampleMapper sampleMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== Excel 样式常量 ====================

    @Override
    public void exportSampleReport(String barcode, HttpServletResponse response) throws IOException {
        List<OrganismResult> results = findByBarcode(barcode);
        if (results.isEmpty()) {
            response.setStatus(404);
            response.getWriter().write("{\"error\":\"not found: " + barcode + "\"}");
            return;
        }
        Workbook wb = buildReportWorkbook(barcode, results);
        String filename = "Report_" + barcode + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        wb.write(response.getOutputStream());
        wb.close();
        log.info("Report exported: barcode={}, instruments={}", barcode, results.size());
    }

    @Override
    public String generateSampleReport(String barcode) throws IOException {
        List<OrganismResult> results = findByBarcode(barcode);
        if (results.isEmpty()) return null;
        Workbook wb = buildReportWorkbook(barcode, results);
        String dir = System.getProperty("java.io.tmpdir") + "myla-reports/";
        new File(dir).mkdirs();
        String path = dir + "report_" + barcode + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        try (FileOutputStream fos = new FileOutputStream(path)) { wb.write(fos); }
        wb.close();
        log.info("Report generated: {}", path);
        return path;
    }

    // ==================== Excel 构建 ====================

    /** 构建报告 Workbook — 支持多仪器数据汇总 */
    private Workbook buildReportWorkbook(String barcode, List<OrganismResult> results) {
        Workbook wb = new XSSFWorkbook();

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

        Sheet sheet = wb.createSheet("检验报告");
        int r = 0;

        // 标题
        Row titleRow = sheet.createRow(r++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("微生物检验报告单");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        titleRow.setHeightInPoints(30);
        r++;

        // 样本信息
        addSection(sheet, r++, "样本信息", headerStyle);
        r = addKV(sheet, r, "样本条码", barcode, labelStyle, valueStyle);
        r = addKV(sheet, r, "数据来源", results.size() + " 台仪器", labelStyle, valueStyle);
        r = addKV(sheet, r, "报告时间", LocalDateTime.now().format(DT_FMT), labelStyle, valueStyle);
        r++;

        // 遍历每台仪器的结果
        for (int i = 0; i < results.size(); i++) {
            OrganismResult orgResult = results.get(i);
            if (results.size() > 1) {
                addSection(sheet, r++, "【仪器 " + (i+1) + "】" + orgResult.getInstrumentId()
                    + " — " + formatTime(orgResult.getTestTime()), headerStyle);
            } else {
                addSection(sheet, r++, "仪器: " + orgResult.getInstrumentId(), headerStyle);
            }

            // 菌种鉴定
            if (orgResult.getOrganismName() != null) {
                r = addKV(sheet, r, "菌种名称", orgResult.getOrganismName(), labelStyle, valueStyle);
                if (orgResult.getIdentificationPercent() != null) {
                    r = addKV(sheet, r, "鉴定置信度", orgResult.getIdentificationPercent() + "%", labelStyle, valueStyle);
                }
            }

            // 药敏
            List<AstResult> astResults = astResultMapper.selectList(
                new LambdaQueryWrapper<AstResult>().eq(AstResult::getOrganismResultId, orgResult.getId()));
            if (!astResults.isEmpty()) {
                r++;
                Row thRow = sheet.createRow(r++);
                String[] cols = {"抗生素", "MIC", "单位", "仪器判读", "最终判读", "备注"};
                for (int j = 0; j < cols.length; j++) {
                    Cell c = thRow.createCell(j); c.setCellValue(cols[j]); c.setCellStyle(tableHeader);
                }
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
            r++;
        }

        // 页脚
        Row footerRow = sheet.createRow(r);
        footerRow.createCell(0).setCellValue("Report time: " + LocalDateTime.now().format(DT_FMT));
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 3000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 8000);

        return wb;
    }

    private String formatTime(LocalDateTime t) {
        return t != null ? t.format(DT_FMT) : "N/A";
    }

    // ==================== 辅助方法 ====================

    /** 按 barcode 查找所有关联的 organism_result（支持多仪器数据串联） */
    private List<OrganismResult> findByBarcode(String barcode) {
        Sample sample = sampleMapper.selectOne(
            new LambdaQueryWrapper<Sample>().eq(Sample::getBarcode, barcode));
        if (sample == null) return List.of();
        return organismResultMapper.selectList(
            new LambdaQueryWrapper<OrganismResult>()
                .eq(OrganismResult::getSampleId, sample.getId())
                .orderByAsc(OrganismResult::getCreatedAt));
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
