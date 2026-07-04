package com.myla.gateway.devicemgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.gateway.devicemgmt.entity.InstrumentRegistry;
import com.myla.gateway.devicemgmt.mapper.InstrumentRegistryMapper;
import com.myla.gateway.devicemgmt.service.InstrumentMgmtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪器管理服务实现。
 * <p>
 * 维护 instrument_registry 表，记录仪器注册信息、实时状态和心跳时间。
 * 支持在线状态查询和离线仪器检测。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentMgmtServiceImpl implements InstrumentMgmtService {

    private final InstrumentRegistryMapper registryMapper;

    @Override
    @Transactional
    public InstrumentRegistry register(String instrumentId, String driverId,
                                        String manufacturer, String model) {
        // 已存在则更新，不存在则新增
        InstrumentRegistry existing = registryMapper.selectOne(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .eq(InstrumentRegistry::getInstrumentId, instrumentId));

        if (existing != null) {
            existing.setDriverId(driverId);
            existing.setManufacturer(manufacturer);
            existing.setModel(model);
            existing.setLastSeenAt(LocalDateTime.now());
            registryMapper.updateById(existing);
            log.info("Instrument re-registered: {}", instrumentId);
            return existing;
        }

        InstrumentRegistry reg = new InstrumentRegistry();
        reg.setInstrumentId(instrumentId);
        reg.setDriverId(driverId);
        reg.setManufacturer(manufacturer);
        reg.setModel(model);
        reg.setStatus("ONLINE");
        reg.setRegisteredAt(LocalDateTime.now());
        reg.setLastSeenAt(LocalDateTime.now());
        registryMapper.insert(reg);
        log.info("Instrument registered: {} (driver={})", instrumentId, driverId);
        return reg;
    }

    @Override
    public void updateStatus(String instrumentId, String status, String message) {
        InstrumentRegistry reg = registryMapper.selectOne(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .eq(InstrumentRegistry::getInstrumentId, instrumentId));

        if (reg != null) {
            reg.setStatus(status);
            reg.setLastSeenAt(LocalDateTime.now());
            registryMapper.updateById(reg);
            log.info("Instrument status updated: {} → {} ({})", instrumentId, status, message);
        } else {
            // 自动注册未在 registry 中的仪器
            log.warn("Instrument {} not registered, auto-registering", instrumentId);
            InstrumentRegistry newReg = new InstrumentRegistry();
            newReg.setInstrumentId(instrumentId);
            newReg.setDriverId("unknown");
            newReg.setStatus(status);
            newReg.setRegisteredAt(LocalDateTime.now());
            newReg.setLastSeenAt(LocalDateTime.now());
            registryMapper.insert(newReg);
        }
    }

    @Override
    public List<InstrumentRegistry> listAll() {
        return registryMapper.selectList(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .orderByDesc(InstrumentRegistry::getLastSeenAt));
    }

    @Override
    public List<InstrumentRegistry> listByStatus(String status) {
        return registryMapper.selectList(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .eq(InstrumentRegistry::getStatus, status)
                .orderByDesc(InstrumentRegistry::getLastSeenAt));
    }

    @Override
    public InstrumentRegistry getByInstrumentId(String instrumentId) {
        return registryMapper.selectOne(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .eq(InstrumentRegistry::getInstrumentId, instrumentId));
    }

    @Override
    public void unregister(String instrumentId) {
        registryMapper.delete(
            new LambdaQueryWrapper<InstrumentRegistry>()
                .eq(InstrumentRegistry::getInstrumentId, instrumentId));
        log.info("Instrument unregistered: {}", instrumentId);
    }

    /**
     * 心跳离线检测 — 每 2 分钟执行一次。
     * <p>
     * 扫描所有状态为 ONLINE 的仪器，如果 lastSeenAt 超过 2 分钟未更新，
     * 判定为离线。不主动探测仪器，只检查心跳时间戳。
     * </p>
     */
    @Scheduled(fixedDelay = 120_000)  // 每 2 分钟
    public void detectOfflineInstruments() {
        List<InstrumentRegistry> onlineList = listByStatus("ONLINE");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        int offlineCount = 0;

        for (InstrumentRegistry reg : onlineList) {
            if (reg.getLastSeenAt() != null && reg.getLastSeenAt().isBefore(threshold)) {
                reg.setStatus("OFFLINE");
                registryMapper.updateById(reg);
                offlineCount++;
                log.warn("Instrument OFFLINE detected: {} (last seen: {})",
                    reg.getInstrumentId(), reg.getLastSeenAt());
            }
        }

        if (offlineCount > 0) {
            log.warn("Heartbeat check: {} instrument(s) marked OFFLINE", offlineCount);
        } else {
            log.debug("Heartbeat check: all {} ONLINE instruments healthy", onlineList.size());
        }
    }
}
