package com.myla.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myla.common.api.event.LabEvent;
import com.myla.common.core.exception.BusinessException;
import com.myla.common.core.constant.ResultCode;
import com.myla.sample.entity.Sample;
import com.myla.sample.mapper.SampleMapper;
import com.myla.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MYLA 系统样本服务实现类。
 * 继承 MyBatis-Plus 的 ServiceImpl 以获得内置 CRUD 方法，
 * 实现样本登记、状态流转、按条码/sampleId 查询和分页查询等业务逻辑。
 *
 * 样本登记流程（事务性操作）：
 * 1. 校验条码唯一性（重复条码抛出 DUPLICATE_BARCODE 异常）
 * 2. 生成内部编号，格式：yyyyMMdd-xxxx（日期 + 四位流水号）
 * 3. 设置初始状态为 REGISTERED，记录接收时间
 * 4. 持久化样本信息
 * 5. 记录样本流转日志到 sample_tracking 表
 * 6. 发布 SAMPLE_REGISTERED 领域事件到工作流模块
 *
 * 状态变更流程（事务性操作）：
 * 1. 根据 ID 查询样本，不存在则抛出异常
 * 2. 校验当前状态与 fromStatus 一致（乐观锁校验）
 * 3. 更新状态为 toStatus
 * 4. 记录流转日志
 * 5. 根据目标状态映射并发布对应的领域事件
 *
 * 领域事件映射：
 * - INOCULATED -> SAMPLE_RECEIVED
 * - APPROVED -> RESULT_APPROVED
 * - RELEASED -> RESULT_RELEASED_TO_LIS
 * - 其他状态 -> SAMPLE_REGISTERED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleServiceImpl extends ServiceImpl<SampleMapper, Sample> implements SampleService {

    private final SampleMapper sampleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 登记新样本。
     * 校验条码唯一性后，生成内部编号并持久化样本信息。
     * 同时记录流转日志和发布领域事件。
     *
     * @param sample 待登记的样本实体
     * @return 登记成功的样本实体
     * @throws BusinessException 当条码重复时抛出 DUPLICATE_BARCODE
     */
    @Override
    @Transactional
    public Sample register(Sample sample) {
        // Check duplicate barcode
        Long count = lambdaQuery().eq(Sample::getBarcode, sample.getBarcode()).count();
        if (count > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_BARCODE);
        }

        // Generate sample_id: yyyyMMdd-xxxx
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", nextSeq(today));
        sample.setSampleId(today + "-" + seq);
        sample.setStatus("REGISTERED");
        sample.setReceiveTime(LocalDateTime.now());

        save(sample);

        // Save tracking log
        jdbcTemplate.update(
            "INSERT INTO sample_tracking (sample_id, to_status, operator, comment, created_at) VALUES (?, ?, ?, ?, ?)",
            sample.getId(), "REGISTERED", "SYSTEM", "Sample registered", LocalDateTime.now()
        );

        // Publish event
        rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.SAMPLE_REGISTERED);
        log.info("Sample registered: sampleId={}, barcode={}", sample.getSampleId(), sample.getBarcode());

        return sample;
    }

    /**
     * 变更样本状态。
     * 校验状态流转合法性后更新状态，记录流转日志并发布领域事件。
     *
     * @param id         样本主键ID
     * @param fromStatus 当前状态（乐观锁校验）
     * @param toStatus   目标状态
     * @param operator   操作人
     * @param comment    操作备注
     * @throws BusinessException 当样本不存在或当前状态不匹配时抛出
     */
    @Override
    @Transactional
    public void updateStatus(Long id, String fromStatus, String toStatus, String operator, String comment) {
        Sample sample = getById(id);
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        if (!sample.getStatus().equals(fromStatus)) {
            throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS);
        }

        sample.setStatus(toStatus);
        updateById(sample);

        // Save tracking log
        jdbcTemplate.update(
            "INSERT INTO sample_tracking (sample_id, from_status, to_status, operator, comment, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            sample.getId(), fromStatus, toStatus, operator, comment, LocalDateTime.now()
        );

        // Publish event
        rabbitTemplate.convertAndSend("myla.workflow", "lab.event",
            LabEvent.valueOf(toStatusToEvent(toStatus)));
        log.info("Sample status updated: id={}, {} -> {}", sample.getSampleId(), fromStatus, toStatus);
    }

    /**
     * 根据条码查询样本。
     * 使用 Lambda 查询进行精确匹配，不存在时抛出异常。
     *
     * @param barcode 样本条码
     * @return 样本实体
     * @throws BusinessException 当样本不存在时抛出 SAMPLE_NOT_FOUND
     */
    @Override
    public Sample getByBarcode(String barcode) {
        Sample sample = lambdaQuery().eq(Sample::getBarcode, barcode).one();
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        return sample;
    }

    /**
     * 根据业务编号（sampleId）查询样本。
     * 使用 Lambda 查询进行精确匹配，不存在时抛出异常。
     *
     * @param sampleId 样本业务编号
     * @return 样本实体
     * @throws BusinessException 当样本不存在时抛出 SAMPLE_NOT_FOUND
     */
    @Override
    public Sample getBySampleId(String sampleId) {
        Sample sample = lambdaQuery().eq(Sample::getSampleId, sampleId).one();
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        return sample;
    }

    /**
     * 按状态分页查询样本列表。
     * 按创建时间降序排列，支持分页。
     *
     * @param status   样本状态
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @return 样本分页结果
     */
    @Override
    public Page<Sample> pageByStatus(String status, int pageNum, int pageSize) {
        return lambdaQuery()
            .eq(Sample::getStatus, status)
            .orderByDesc(Sample::getCreatedAt)
            .page(new Page<>(pageNum, pageSize));
    }

    /**
     * 生成当天下一个样本序号。
     * 查询当天已有样本数量，返回下一个序号（从1开始自增）。
     *
     * @param today 当天日期字符串，格式：yyyyMMdd
     * @return 下一个序号（1-based）
     */
    private int nextSeq(String today) {
        String prefix = today + "-";
        Long count = lambdaQuery()
            .likeRight(Sample::getSampleId, today)
            .count();
        return (int) (count + 1);
    }

    /**
     * 将样本状态映射为对应的领域事件名称。
     * 映射关系：
     * - INOCULATED -> SAMPLE_RECEIVED
     * - APPROVED -> RESULT_APPROVED
     * - RELEASED -> RESULT_RELEASED_TO_LIS
     * - 其他状态 -> SAMPLE_REGISTERED（默认）
     *
     * @param status 样本目标状态
     * @return 对应的领域事件名称
     */
    private String toStatusToEvent(String status) {
        return switch (status) {
            case "INOCULATED" -> "SAMPLE_RECEIVED";
            case "APPROVED" -> "RESULT_APPROVED";
            case "RELEASED" -> "RESULT_RELEASED_TO_LIS";
            default -> "SAMPLE_REGISTERED";
        };
    }
}
