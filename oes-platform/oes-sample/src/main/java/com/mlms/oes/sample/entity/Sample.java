package com.mlms.oes.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MLMS 系统样本实体类。
 * 对应数据库表 sample，存储实验室检验样本的完整信息。
 * 包括样本标识（条码、内部编号）、患者信息（姓名、性别、年龄）、
 * 标本类型、采集和接收时间、状态、优先级、病区信息、诊断信息等。
 * 支持 MyBatis-Plus 的自动填充和逻辑删除。
 *
 * @author MLMS Team
 */
@Data
@TableName("sample")
public class Sample {
    /** 样本主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 实验室内部样本编号，格式：yyyyMMdd-xxxx，由系统自动生成 */
    private String sampleId;

    /** 样本条码，通常来自医院 HIS/LIS 系统的唯一标识 */
    private String barcode;

    /** 患者ID，关联医院患者信息 */
    private String patientId;

    /** 患者姓名 */
    private String patientName;

    /** 患者性别 */
    private String gender;

    /** 患者年龄 */
    private Integer age;

    /** 标本类型，如：血液、尿液、痰液、分泌物等 */
    private String specimenType;

    /** 样本采集时间 */
    private LocalDateTime collectTime;

    /** 样本接收时间（实验室接收时间） */
    private LocalDateTime receiveTime;

    /** 标本物理状态：ORDER_RECEIVED→ACCEPTED→GRAM_STAINED→INOCULATED→INCUBATING→ORGANISM_ISOLATED→COMPLETED
     *  终态分支：REJECTED(拒收) CULTURE_NEGATIVE(培养阴性) CULTURE_CONTAMINATED(污染) */
    private String status;

    /** 样本优先级：STAT-紧急，ROUTINE-常规 */
    private String priority;

    /** 病区编码 */
    private String wardCode;

    /** 病区名称 */
    private String wardName;

    /** 临床诊断信息 */
    private String diagnosis;

    /** 数据来源系统标识，如 HIS、LIS、MANUAL 等 */
    private String sourceSystem;

    /** 备注信息 */
    private String comment;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间，由 MyBatis-Plus 插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}
