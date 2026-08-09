package com.mlms.oes.gateway.devicemgmt.service;

import com.mlms.oes.gateway.devicemgmt.entity.InstrumentRegistry;
import java.util.List;

/**
 * 仪器管理服务接口。
 */
public interface InstrumentMgmtService {

    /** 注册仪器（或更新已存在的注册信息） */
    InstrumentRegistry register(String instrumentId, String driverId,
                                 String manufacturer, String model);

    /** 更新仪器状态 + 刷新心跳时间 */
    void updateStatus(String instrumentId, String status, String message);

    /** 获取所有已注册仪器 */
    List<InstrumentRegistry> listAll();

    /** 按状态筛选仪器 */
    List<InstrumentRegistry> listByStatus(String status);

    /** 获取单个仪器信息 */
    InstrumentRegistry getByInstrumentId(String instrumentId);

    /** 注销/删除仪器 */
    void unregister(String instrumentId);
}
