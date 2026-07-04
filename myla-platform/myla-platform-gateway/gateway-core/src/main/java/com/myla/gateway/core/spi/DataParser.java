package com.myla.gateway.core.spi;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.core.exception.ParseException;
import java.util.List;

public interface DataParser {
    String getParserId();
    List<UnifiedResult> parse(byte[] frame) throws ParseException;
}
