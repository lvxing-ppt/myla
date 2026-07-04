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

@Slf4j
public class Vitek2Parser implements DataParser {

    @Override
    public String getParserId() {
        return "vitek2-parser";
    }

    @Override
    public List<UnifiedResult> parse(byte[] frame) throws ParseException {
        String text = new String(frame, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new ParseException(text, "Empty frame");
        }

        UnifiedResult result = new UnifiedResult();
        result.setInstrumentId("VITEK2");
        result.setResultType(ResultType.AST);
        result.setTestTime(LocalDateTime.now());
        result.setRawMessage(text);

        String[] lines = text.split("\r");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("O|")) {
                String[] fields = line.split("\\|");
                if (fields.length > 2) {
                    result.setSampleBarcode(fields[2].trim());
                }
            } else if (line.startsWith("R|")) {
                String[] fields = line.split("\\|");
                if (fields.length > 5 && fields[3].contains("ORGANISM")) {
                    result.setOrganismName(fields[4].trim());
                    try {
                        result.setIdentificationPercent(Double.parseDouble(fields[5].trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (fields.length > 6 && fields[3].contains("AST")) {
                    if (result.getAstResults() == null) {
                        result.setAstResults(new ArrayList<>());
                    }
                    AstResultDTO ast = new AstResultDTO();
                    ast.setAntibioticName(fields[4].trim());
                    try {
                        ast.setMicValue(Double.parseDouble(fields[5].trim()));
                    } catch (NumberFormatException ignored) {
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
