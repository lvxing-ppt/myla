package com.mlms.oes.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlms.oes.sample.entity.*;
import com.mlms.oes.sample.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平板 & 玻片管理服务。
 * 平板/玻片关联 sample，通过中间表 lab_plate_order / lab_slide_order 与 lis_order 建立多对多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlateSlideService {

    private final LabPlateMapper plateMapper;
    private final LabSlideMapper slideMapper;
    private final LabPlateOrderMapper plateOrderMapper;
    private final LabSlideOrderMapper slideOrderMapper;

    /** 生成平板编号: P-{sampleId后8位}-{序号} */
    public String generatePlateId(Long sampleId) {
        String suffix = String.format("%08d", sampleId % 100_000_000);
        long count = plateMapper.selectCount(
            new LambdaQueryWrapper<LabPlate>().eq(LabPlate::getSampleId, sampleId));
        return String.format("P-%s-%02d", suffix, count + 1);
    }

    /** 生成玻片编号: S-{sampleId后8位}-{序号} */
    public String generateSlideId(Long sampleId) {
        String suffix = String.format("%08d", sampleId % 100_000_000);
        long count = slideMapper.selectCount(
            new LambdaQueryWrapper<LabSlide>().eq(LabSlide::getSampleId, sampleId));
        return String.format("S-%s-%02d", suffix, count + 1);
    }

    /** 为标本创建平板，并关联到订单 */
    @Transactional
    public LabPlate createPlate(Long sampleId, Long orderId, String mediaType, String mediaLot) {
        LabPlate plate = new LabPlate();
        plate.setPlateId(generatePlateId(sampleId));
        plate.setSampleId(sampleId);
        plate.setMediaType(mediaType);
        plate.setMediaLot(mediaLot);
        plate.setStatus("INOCULATED");
        plate.setInoculateTime(LocalDateTime.now());
        plateMapper.insert(plate);

        LabPlateOrder po = new LabPlateOrder();
        po.setPlateId(plate.getId());
        po.setOrderId(orderId);
        plateOrderMapper.insert(po);

        log.info("Plate created: plateId={}, sampleId={}, orderId={}, media={}",
                plate.getPlateId(), sampleId, orderId, mediaType);
        return plate;
    }

    /** 为标本创建玻片，并关联到订单 */
    @Transactional
    public LabSlide createSlide(Long sampleId, Long orderId, String stainType, String stainLot) {
        LabSlide slide = new LabSlide();
        slide.setSlideId(generateSlideId(sampleId));
        slide.setSampleId(sampleId);
        slide.setStainType(stainType);
        slide.setStainLot(stainLot);
        slide.setStatus("PREPARED");
        slideMapper.insert(slide);

        LabSlideOrder so = new LabSlideOrder();
        so.setSlideId(slide.getId());
        so.setOrderId(orderId);
        slideOrderMapper.insert(so);

        log.info("Slide created: slideId={}, sampleId={}, orderId={}, stain={}",
                slide.getSlideId(), sampleId, orderId, stainType);
        return slide;
    }

    /** 查标本下所有平板 */
    public List<LabPlate> listPlatesBySample(Long sampleId) {
        return plateMapper.selectList(
            new LambdaQueryWrapper<LabPlate>().eq(LabPlate::getSampleId, sampleId));
    }

    /** 查标本下所有玻片 */
    public List<LabSlide> listSlidesBySample(Long sampleId) {
        return slideMapper.selectList(
            new LambdaQueryWrapper<LabSlide>().eq(LabSlide::getSampleId, sampleId));
    }

    /** 查订单关联的平板 ID 列表 */
    public List<Long> listPlateIdsByOrder(Long orderId) {
        return plateOrderMapper.selectList(
            new LambdaQueryWrapper<LabPlateOrder>().eq(LabPlateOrder::getOrderId, orderId))
            .stream().map(LabPlateOrder::getPlateId).collect(Collectors.toList());
    }

    /** 查订单关联的玻片 ID 列表 */
    public List<Long> listSlideIdsByOrder(Long orderId) {
        return slideOrderMapper.selectList(
            new LambdaQueryWrapper<LabSlideOrder>().eq(LabSlideOrder::getOrderId, orderId))
            .stream().map(LabSlideOrder::getSlideId).collect(Collectors.toList());
    }
}
