package com.mlms.oes.admin.controller;

import com.mlms.oes.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 字典数据接口 — 菌种/抗生素/标本类型。
 */
@RestController
@RequestMapping("/api/v1/dict")
@RequiredArgsConstructor
public class DictController {

    private final JdbcTemplate jdbc;

    @GetMapping("/organisms")
    public R<List<Map<String, Object>>> organisms() {
        return R.ok(jdbc.queryForList(
            "SELECT id, organism_code, organism_name, gram_stain, category FROM organism_dict WHERE enabled=1 ORDER BY id"));
    }

    @GetMapping("/antibiotics")
    public R<List<Map<String, Object>>> antibiotics() {
        return R.ok(jdbc.queryForList(
            "SELECT id, antibiotic_code, antibiotic_name, antibiotic_class FROM antibiotic_dict WHERE enabled=1 ORDER BY id"));
    }

    @GetMapping("/specimens")
    public R<List<Map<String, Object>>> specimens() {
        return R.ok(jdbc.queryForList(
            "SELECT id, specimen_code, specimen_name, is_sterile_site FROM specimen_dict WHERE enabled=1 ORDER BY id"));
    }
}
