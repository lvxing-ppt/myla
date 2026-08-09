package com.mlms.oes.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlms.oes.result.entity.OrganismResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * MLMS 系统细菌鉴定结果数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 OrganismResult 实体的 CRUD 操作方法。
 * 用于细菌鉴定结果的持久化操作，支持按 resultId 和 sampleId 查询。
 */
@Mapper
public interface OrganismResultMapper extends BaseMapper<OrganismResult> {
}
