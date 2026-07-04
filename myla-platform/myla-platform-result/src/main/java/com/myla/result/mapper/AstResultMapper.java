package com.myla.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.result.entity.AstResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * MYLA 系统药敏结果数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 AstResult 实体的 CRUD 操作方法。
 * 用于药敏试验结果的持久化操作，包括批量写入和按细菌鉴定结果ID关联查询。
 */
@Mapper
public interface AstResultMapper extends BaseMapper<AstResult> {
}
