package com.mlms.oes.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mlms.oes.sample.entity.Sample;

/**
 * MLMS 系统样本服务接口。
 * 继承 MyBatis-Plus 的 IService 以获得内置的 CRUD 方法，
 * 定义样本管理的核心业务操作。
 * 包括样本登记（自动生成内部编号）、状态流转、
 * 按条码/sampleId 查询以及按状态分页查询。
 */
public interface SampleService extends IService<Sample> {

    /**
     * 登记新样本。
     * 校验条码唯一性，生成内部编号（yyyyMMdd-xxxx），
     * 设置初始状态为 REGISTERED，记录流转日志，发布领域事件。
     *
     * @param sample 待登记的样本实体
     * @return 登记成功的样本实体（含生成的内部编号和初始状态）
     */
    Sample register(Sample sample);

    /**
     * 变更样本状态。
     * 校验状态流转的合法性（当前状态必须匹配 fromStatus），
     * 更新状态并记录流转日志，发布对应的领域事件。
     *
     * @param id         样本主键ID
     * @param fromStatus 当前状态（用于乐观锁校验）
     * @param toStatus   目标状态
     * @param operator   操作人
     * @param comment    操作备注
     */
    void updateStatus(Long id, String fromStatus, String toStatus, String operator, String comment);

    /**
     * 根据条码查询样本。
     *
     * @param barcode 样本条码
     * @return 样本实体
     */
    Sample getByBarcode(String barcode);

    /**
     * 根据条码查询样本，不存在返回 null（不抛异常）。
     */
    Sample getByBarcodeOrNull(String barcode);

    /**
     * 根据业务编号（sampleId）查询样本。
     *
     * @param sampleId 样本业务编号
     * @return 样本实体
     */
    Sample getBySampleId(String sampleId);

    /**
     * 按状态分页查询样本列表。
     *
     * @param status   样本状态
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @return 样本分页结果
     */
    Page<Sample> pageByStatus(String status, int pageNum, int pageSize);
}
