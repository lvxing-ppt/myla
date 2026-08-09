package com.mlms.oes.admin.controller;

import com.mlms.oes.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作台统计接口。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbc;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todaySamples", queryCount("SELECT COUNT(*) FROM sample WHERE DATE(created_at) = CURDATE()"));
        data.put("pendingReview", queryCount("SELECT COUNT(*) FROM organism_result WHERE review_status = 'PENDING'"));
        data.put("onlineInstruments", queryCount("SELECT COUNT(*) FROM instrument_registry WHERE status = 'ONLINE'"));
        data.put("criticalAlerts", queryCount("SELECT COUNT(*) FROM critical_value_alert WHERE notify_status = 'PENDING'"));
        return R.ok(data);
    }

    private long queryCount(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result != null ? result : 0;
    }
}
