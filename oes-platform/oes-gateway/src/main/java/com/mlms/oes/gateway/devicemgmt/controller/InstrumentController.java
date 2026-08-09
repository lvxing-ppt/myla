package com.mlms.oes.gateway.devicemgmt.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlms.oes.common.core.constant.ResultCode;
import com.mlms.oes.common.core.util.R;
import com.mlms.oes.gateway.devicemgmt.entity.InstrumentRegistry;
import com.mlms.oes.gateway.devicemgmt.event.InstrumentRegisteredEvent;
import com.mlms.oes.gateway.devicemgmt.service.InstrumentMgmtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 仪器管理 REST 接口 — 列表/详情/状态/动态注册/注销。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final InstrumentMgmtService mgmtService;
    private final ApplicationEventPublisher eventPublisher;
    private static final ObjectMapper json = new ObjectMapper();

    public InstrumentController(InstrumentMgmtService mgmtService, ApplicationEventPublisher eventPublisher) {
        this.mgmtService = mgmtService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public R<List<InstrumentRegistry>> list(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) return R.ok(mgmtService.listByStatus(status));
        return R.ok(mgmtService.listAll());
    }

    @GetMapping("/{instrumentId}")
    public R<InstrumentRegistry> getById(@PathVariable String instrumentId) {
        InstrumentRegistry reg = mgmtService.getByInstrumentId(instrumentId);
        return reg != null ? R.ok(reg) : R.fail(ResultCode.NOT_FOUND);
    }

    @PutMapping("/{instrumentId}/status")
    public R<Void> updateStatus(@PathVariable String instrumentId, @RequestBody Map<String, String> body) {
        mgmtService.updateStatus(instrumentId, body.get("status"), body.getOrDefault("message", ""));
        return R.ok();
    }

    @DeleteMapping("/{instrumentId}")
    public R<Void> unregister(@PathVariable String instrumentId) {
        mgmtService.unregister(instrumentId);
        return R.ok();
    }

    /**
     * 动态注册 + 热加载 — 无需改 YAML，无需重启。
     */
    @PostMapping("/register")
    public R<InstrumentRegistry> register(@RequestBody Map<String, Object> body) {
        String driverId = (String) body.get("driverId");
        String instrumentId = (String) body.get("instrumentId");
        int port = body.containsKey("port") ? ((Number) body.get("port")).intValue() : 0;

        if (driverId == null || instrumentId == null || port == 0) {
            return R.fail(ResultCode.BAD_REQUEST, "driverId, instrumentId, port required");
        }

        // 1. 持久化配置到 DB
        mgmtService.register(instrumentId, driverId, "Dynamic", "Dynamic");
        InstrumentRegistry reg = mgmtService.getByInstrumentId(instrumentId);
        try {
            Map<String, Object> chCfg = new LinkedHashMap<>();
            chCfg.put("type", "TCP");
            chCfg.put("port", port);
            chCfg.put("splitterType", body.getOrDefault("splitterType", ""));
            chCfg.put("parserType", body.getOrDefault("parserType", ""));
            reg.setChannelConfig(json.writeValueAsString(chCfg));
            reg.setStatus("REGISTERED");
        } catch (JsonProcessingException e) {
            return R.fail(ResultCode.INTERNAL_ERROR, "JSON error: " + e.getMessage());
        }

        // 2. 发布事件 → GatewayBootstrap 监听并热加载驱动
        eventPublisher.publishEvent(new InstrumentRegisteredEvent(this, instrumentId));
        log.info("Instrument {} registered (driver={}, port={}), hot-loading...", instrumentId, driverId, port);

        return R.ok(reg);
    }
}
