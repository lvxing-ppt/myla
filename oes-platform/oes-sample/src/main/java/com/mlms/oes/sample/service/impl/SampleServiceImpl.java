package com.mlms.oes.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mlms.oes.common.api.event.LabEvent;
import com.mlms.oes.common.core.exception.BusinessException;
import com.mlms.oes.common.core.constant.ResultCode;
import com.mlms.oes.sample.entity.Sample;
import com.mlms.oes.sample.entity.SampleBarcode;
import com.mlms.oes.sample.mapper.SampleBarcodeMapper;
import com.mlms.oes.sample.mapper.SampleMapper;
import com.mlms.oes.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.time.format.DateTimeFormatter;

/**
 * MLMS 系统样本服务实现类。
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
    private final SampleBarcodeMapper barcodeMapper;
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
        // Check duplicate barcode (via sample_barcode table)
        String barcode = sample.getBarcode();
        if (barcode != null && !barcode.isBlank()) {
            Long count = barcodeMapper.selectCount(
                new LambdaQueryWrapper<SampleBarcode>().eq(SampleBarcode::getBarcode, barcode));
            if (count > 0) throw new BusinessException(ResultCode.DUPLICATE_BARCODE);
        }

        // Generate sample_id: yyyyMMdd-xxxx
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", nextSeq(today));
        sample.setSampleId(today + "-" + seq);
        sample.setStatus("ORDER_RECEIVED");

        save(sample);

        // Insert sample_barcode
        if (barcode != null && !barcode.isBlank()) {
            SampleBarcode sb = new SampleBarcode();
            sb.setSampleId(sample.getId());
            sb.setBarcode(barcode);
            sb.setSource("LIS");
            sb.setIsPrimary(1);
            barcodeMapper.insert(sb);
        }

        jdbcTemplate.update(
            "INSERT INTO sample_tracking (sample_id, to_status, operator, comment, created_at) VALUES (?,?,?,?,?)",
            sample.getId(), "ORDER_RECEIVED", "SYSTEM", "Order received from LIS", LocalDateTime.now());

        // Publish event
        rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.SAMPLE_REGISTERED);
        log.info("Sample registered: sampleId={}, barcode={}", sample.getSampleId(), barcode);

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
    // ==================== 样本状态机 ====================
    //
    //  物理标本流转（sample.status）:
    //
    //  ORDER_RECEIVED ──→ ACCEPTED ──→ GRAM_STAINED ──→ INOCULATED ──→ INCUBATING
    //       │                │              │               │               │
    //       └── REJECTED ←──┴──────────────┴───────────────┴───────────────┘
    //                                                                       │
    //                                                          ┌────────────┘
    //                                                          ▼
    //                                                   ORGANISM_ISOLATED ──→ COMPLETED
    //                                                          │
    //                                                   ┌──────┴──────┐
    //                                                   ▼             ▼
    //                                          CULTURE_NEGATIVE  CULTURE_CONTAMINATED
    //
    //  数据审核（organism_result.reviewStatus）—— 独立流程:
    //  PENDING → TECH_APPROVED → CLINICAL_APPROVED → RELEASED
    //

    /** 标本流转路径（当前状态 → 允许的目标状态） */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
        "ORDER_RECEIVED",       Set.of("ACCEPTED", "REJECTED"),
        "ACCEPTED",             Set.of("GRAM_STAINED", "REJECTED"),
        "GRAM_STAINED",         Set.of("INOCULATED", "REJECTED"),
        "INOCULATED",           Set.of("INCUBATING", "REJECTED"),
        "INCUBATING",           Set.of("ORGANISM_ISOLATED", "CULTURE_NEGATIVE",
                                       "CULTURE_CONTAMINATED", "REJECTED"),
        "ORGANISM_ISOLATED",    Set.of("COMPLETED")
    );

    /** 终态集合（不可再变更） */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
        "COMPLETED", "REJECTED", "CULTURE_NEGATIVE", "CULTURE_CONTAMINATED", "CANCELLED");

    /** 状态 → 领域事件 */
    private static final Map<String, String> STATUS_EVENT = Map.of(
        "ACCEPTED",              "SAMPLE_RECEIVED",
        "ORGANISM_ISOLATED",     "ORGANISM_IDENTIFIED",
        "COMPLETED",             "AST_RESULT_RECEIVED",
        "CULTURE_NEGATIVE",      "CULTURE_NEGATIVE",
        "CULTURE_CONTAMINATED",  "CULTURE_CONTAMINATED",
        "REJECTED",              "SAMPLE_MISMATCH"
    );

    @Override
    @Transactional
    public void updateStatus(Long id, String fromStatus, String toStatus, String operator, String comment) {
        Sample sample = getById(id);
        if (sample == null) throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        if (!sample.getStatus().equals(fromStatus)) {
            throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS,
                "Expected " + fromStatus + " but is " + sample.getStatus());
        }
        if (TERMINAL_STATUSES.contains(sample.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS,
                "Status " + sample.getStatus() + " is terminal and cannot be changed");
        }

        // 校验合法路径
        Set<String> allowed = VALID_TRANSITIONS.getOrDefault(fromStatus, Set.of());
        if (!allowed.contains(toStatus)) {
            throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS,
                "Illegal: " + fromStatus + " → " + toStatus +
                ". Allowed: " + String.join(", ", allowed));
        }

        sample.setStatus(toStatus);
        // 签收时记录物理接收时间
        if ("ACCEPTED".equals(toStatus)) {
            sample.setReceiveTime(LocalDateTime.now());
        }
        updateById(sample);

        jdbcTemplate.update(
            "INSERT INTO sample_tracking (sample_id,from_status,to_status,operator,comment,created_at) VALUES (?,?,?,?,?,?)",
            sample.getId(), fromStatus, toStatus, operator, comment, LocalDateTime.now());

        // 发布领域事件（仅已映射的状态，无映射则静默跳过）
        String event = STATUS_EVENT.get(toStatus);
        if (event != null) {
            rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.valueOf(event));
        }
        log.info("Sample {} → {} (by {})", fromStatus, toStatus, operator);
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
        Sample sample = getByBarcodeOrNull(barcode);
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        return sample;
    }

    @Override
    public Sample getByBarcodeOrNull(String barcode) {
        return lambdaQuery().eq(Sample::getBarcode, barcode).one();
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
        Long count = lambdaQuery()
            .likeRight(Sample::getSampleId, today)
            .count();
        return (int) (count + 1);
    }

}
