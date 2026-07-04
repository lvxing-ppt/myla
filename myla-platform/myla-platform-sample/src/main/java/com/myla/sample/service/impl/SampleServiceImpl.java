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

@Slf4j
@Service
@RequiredArgsConstructor
public class SampleServiceImpl extends ServiceImpl<SampleMapper, Sample> implements SampleService {

    private final SampleMapper sampleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

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

    @Override
    public Sample getByBarcode(String barcode) {
        Sample sample = lambdaQuery().eq(Sample::getBarcode, barcode).one();
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        return sample;
    }

    @Override
    public Sample getBySampleId(String sampleId) {
        Sample sample = lambdaQuery().eq(Sample::getSampleId, sampleId).one();
        if (sample == null) {
            throw new BusinessException(ResultCode.SAMPLE_NOT_FOUND);
        }
        return sample;
    }

    @Override
    public Page<Sample> pageByStatus(String status, int pageNum, int pageSize) {
        return lambdaQuery()
            .eq(Sample::getStatus, status)
            .orderByDesc(Sample::getCreatedAt)
            .page(new Page<>(pageNum, pageSize));
    }

    private int nextSeq(String today) {
        String prefix = today + "-";
        Long count = lambdaQuery()
            .likeRight(Sample::getSampleId, today)
            .count();
        return (int) (count + 1);
    }

    private String toStatusToEvent(String status) {
        return switch (status) {
            case "INOCULATED" -> "SAMPLE_RECEIVED";
            case "APPROVED" -> "RESULT_APPROVED";
            case "RELEASED" -> "RESULT_RELEASED_TO_LIS";
            default -> "SAMPLE_REGISTERED";
        };
    }
}
