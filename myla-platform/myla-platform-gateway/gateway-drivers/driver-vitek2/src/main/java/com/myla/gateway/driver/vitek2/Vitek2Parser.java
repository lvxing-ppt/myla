package com.myla.gateway.driver.vitek2;

import com.myla.common.api.dto.AstResultDTO;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.api.enums.ResultType;
import com.myla.common.core.exception.ParseException;
import com.myla.gateway.core.spi.DataParser;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * VITEK 2 ASTM 报文解析器。
 * <p>
 * 实现 {@link DataParser} 接口，将 VITEK 2 仪器上报的 ASTM 格式报文
 * 解析为系统统一的 {@link UnifiedResult} 对象。
 * </p>
 *
 * <h3>ASTM 报文格式说明：</h3>
 * <p>VITEK 2 使用 ASTM E1394 标准格式上报数据。报文由多条记录组成，以回车符(\r)分隔。</p>
 * <ul>
 *   <li><b>O| 记录（Order Record）</b> — 医嘱/样本信息，字段 3（索引 2）为样本条码</li>
 *   <li><b>R| 记录（Result Record）</b> — 检验结果
 *     <ul>
 *       <li>字段 4（索引 3）包含 "ORGANISM" 时为菌种鉴定结果：字段 5 为菌种名称，字段 6 为置信度</li>
 *       <li>字段 4（索引 3）包含 "AST" 时为药敏结果：字段 5 为抗生素名称，字段 6 为 MIC 值，字段 7 为 SIR 判读</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author MyLA Team
 */
@Slf4j
public class Vitek2Parser implements DataParser {

    /**
     * 获取解析器唯一标识。
     * @return 固定返回 "vitek2-parser"
     */
    @Override
    public String getParserId() {
        return "vitek2-parser";
    }

    /**
     * 解析 VITEK 2 ASTM 格式数据帧。
     * <p>
     * 解析算法：
     * <ol>
     *   <li>将字节数组转为 UTF-8 字符串，去除首尾空白</li>
     *   <li>空帧直接抛出 ParseException</li>
     *   <li>按回车符(\r)分割为多行</li>
     *   <li>逐行按管道符(|)分隔字段</li>
     *   <li>O| 开头行 -> 提取样本条码</li>
     *   <li>R| 开头行：
     *     <ul>
     *       <li>字段 3 包含 "ORGANISM" -> 提取菌种名称和鉴定置信度</li>
     *       <li>字段 3 包含 "AST" -> 提取抗生素 MIC 值和 SIR 结果</li>
     *     </ul>
     *   </li>
     * </ol>
     * </p>
     *
     * @param frame ASTM 格式的完整数据帧字节数组
     * @return 解析出的 UnifiedResult 列表（通常包含 1 条结果，含菌种鉴定和多个药敏明细）
     * @throws ParseException 如果帧为空或格式不符合预期
     */
    @Override
    public List<UnifiedResult> parse(byte[] frame) throws ParseException {
        // 转为字符串并去除首尾空白
        String text = new String(frame, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new ParseException(text, "Empty frame");
        }

        UnifiedResult result = new UnifiedResult();
        result.setInstrumentId("VITEK2");
        result.setResultType(ResultType.AST);
        result.setTestTime(LocalDateTime.now());
        result.setRawMessage(text);

        // 按回车符分割记录行
        String[] lines = text.split("\r");
        for (String line : lines) {
            line = line.trim();

            // ---- Order Record (O|) ----
            if (line.startsWith("O|")) {
                String[] fields = line.split("\\|");
                // 字段索引 2 为样本条码（"|" 分隔后，索引 0="O"，索引 1 为空，索引 2=条码）
                if (fields.length > 2) {
                    result.setSampleBarcode(fields[2].trim());
                }
            }
            // ---- Result Record (R|) ----
            else if (line.startsWith("R|")) {
                String[] fields = line.split("\\|");

                // 菌种鉴定结果：字段 3 包含 "ORGANISM"
                if (fields.length > 5 && fields[3].contains("ORGANISM")) {
                    result.setOrganismName(fields[4].trim());
                    try {
                        result.setIdentificationPercent(Double.parseDouble(fields[5].trim()));
                    } catch (NumberFormatException ignored) {
                        // MIC 值解析失败时忽略，保留 null
                    }
                }

                // 药敏结果：字段 3 包含 "AST"
                if (fields.length > 6 && fields[3].contains("AST")) {
                    if (result.getAstResults() == null) {
                        result.setAstResults(new ArrayList<>());
                    }
                    AstResultDTO ast = new AstResultDTO();
                    ast.setAntibioticName(fields[4].trim());
                    try {
                        ast.setMicValue(Double.parseDouble(fields[5].trim()));
                    } catch (NumberFormatException ignored) {
                        // MIC 值解析失败时忽略，保留 null
                    }
                    ast.setMachineSIR(fields[6].trim());
                    ast.setFinalSIR(fields[6].trim());
                    result.getAstResults().add(ast);
                }
            }
        }
        return List.of(result);
    }
}
