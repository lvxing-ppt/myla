package com.mlms.oes.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlms.oes.sample.entity.Sample;
import org.apache.ibatis.annotations.Mapper;

/**
 * MLMS 系统样本数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 Sample 实体的 CRUD 操作方法。
 * 用于样本的持久化操作，支持按条码、sampleId 和状态等字段查询。
 */
@Mapper
public interface SampleMapper extends BaseMapper<Sample> {
}
