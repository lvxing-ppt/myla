package com.myla.gateway.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.core.exception.ParseException;
import com.myla.gateway.core.spi.DataParser;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JSON 格式解析器 — 将 JSON 字节数组反序列化为 UnifiedResult。
 * <p>
 * 适用于仪器直接发送 JSON 格式数据的场景。配合 {@link com.myla.gateway.splitter.RawPassthroughSplitter} 使用。
 * </p>
 *
 * <h3>子类扩展：</h3>
 * <p>如果仪器 JSON 字段名和 UnifiedResult 不一致，重写 {@link #parse} 方法做字段映射。</p>
 */
public class JsonResultParser implements DataParser {

    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public String getParserId() {
        return "json-parser";
    }

    @Override
    public List<UnifiedResult> parse(byte[] frame) throws ParseException {
        try {
            String text = new String(frame, StandardCharsets.UTF_8).trim();
            // 兼容数组格式 [{...}] 和单对象格式 {...}
            if (text.startsWith("[")) {
                return json.readValue(text,
                    json.getTypeFactory().constructCollectionType(List.class, UnifiedResult.class));
            } else {
                return List.of(json.readValue(text, UnifiedResult.class));
            }
        } catch (Exception e) {
            throw new ParseException(new String(frame, StandardCharsets.UTF_8),
                "JSON parse failed: " + e.getMessage());
        }
    }
}
