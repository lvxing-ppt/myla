package com.mlms.oes.lis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlms.oes.lis.entity.LisConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LIS 配置数据访问层。
 * 继承 MyBatis-Plus BaseMapper 获得内置 CRUD，
 * 提供按医院编码查询和查询所有已启用入站配置的方法。
 */
@Mapper
public interface LisConfigMapper extends BaseMapper<LisConfig> {

    /**
     * 根据医院编码查询 LIS 配置。
     * @param hospitalCode 医院编码
     * @return LIS 配置实体，不存在返回 null
     */
    @Select("SELECT * FROM lis_config WHERE hospital_code = #{hospitalCode}")
    LisConfig selectByHospitalCode(String hospitalCode);

    /**
     * 查询所有已启用的、配置了入站通道的 LIS 配置。
     * channel_type IN ('HL7', 'ASTM') AND enabled = 1
     * @return 已启用的入站配置列表
     */
    @Select("SELECT * FROM lis_config WHERE channel_type IN ('HL7','ASTM') AND enabled = 1")
    List<LisConfig> selectEnabledInbound();
}
