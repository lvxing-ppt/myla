package com.myla.lis.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.outbound.Hl7MessageBuilder;
import com.myla.lis.service.LisGatewayService;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.sample.entity.Sample;
import com.myla.sample.mapper.SampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结果发布 LIS 消费者。
 * <p>
 * 监听 lis.outbound.trigger 队列，接收 RESULT_RELEASED_TO_LIS 事件，
 * 加载结果和样本数据，构造 HL7 ORU^R01 消息，调用 LisGatewayService 排队发送。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultReleasedConsumer {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final SampleMapper sampleMapper;
    private final LisConfigMapper configMapper;
    private final LisGatewayService lisGatewayService;
    private final Hl7MessageBuilder messageBuilder = new Hl7MessageBuilder();

    /**
     * 监听 lab.event 事件，仅处理 RESULT_RELEASED_TO_LIS。
     * <p>
     * 注意：LabEvent 通过 Jackson 反序列化时为字符串，不是枚举对象。
     * 因此使用 String 接收并比较。
     * </p>
     */
    @RabbitListener(queues = "lis.outbound.trigger")
    public void onLabEvent(String event) {
        if (!"RESULT_RELEASED_TO_LIS".equals(event)) {
            return; // 忽略其他事件
        }

        log.info("LIS outbound triggered by RESULT_RELEASED_TO_LIS");

        // 1. 查找所有已发布但未发送的结果（review_status = RELEASED）
        List<OrganismResult> released = organismResultMapper.selectList(
                new LambdaQueryWrapper<OrganismResult>()
                        .eq(OrganismResult::getReviewStatus, "RELEASED"));

        for (OrganismResult orgResult : released) {
            try {
                // 2. 加载样本
                Sample sample = sampleMapper.selectById(orgResult.getSampleId());
                if (sample == null) {
                    log.warn("Sample not found for organismResult.id={}", orgResult.getId());
                    continue;
                }

                // 3. 加载 AST 结果
                List<AstResult> asts = astResultMapper.selectList(
                        new LambdaQueryWrapper<AstResult>()
                                .eq(AstResult::getOrganismResultId, orgResult.getId()));

                // 4. 确定目标医院
                String hospitalCode = deriveHospitalCode(sample);

                // 5. 加载 LIS 配置
                LisConfig config = configMapper.selectByHospitalCode(hospitalCode);
                if (config == null || config.getEnabled() != 1) {
                    log.debug("No enabled LIS config for hospital={}", hospitalCode);
                    continue;
                }

                // 6. 构造 HL7 ORU^R01 消息
                String hl7 = messageBuilder.buildOruR01(orgResult, asts, sample, config);

                // 7. 排队发送
                lisGatewayService.sendResult(hospitalCode, hl7);

            } catch (Exception e) {
                log.error("Failed to build LIS message for organismResult.id={}: {}",
                        orgResult.getId(), e.getMessage());
            }
        }
    }

    /**
     * 从样本信息推导医院编码。
     * 优先使用 sourceSystem，其次使用 wardCode。
     */
    private String deriveHospitalCode(Sample sample) {
        if (sample.getSourceSystem() != null && !sample.getSourceSystem().isBlank()
                && !"LIS".equals(sample.getSourceSystem()) && !"MANUAL".equals(sample.getSourceSystem())) {
            return sample.getSourceSystem();
        }
        if (sample.getWardCode() != null && !sample.getWardCode().isBlank()) {
            return sample.getWardCode();
        }
        return "DEFAULT";
    }
}
