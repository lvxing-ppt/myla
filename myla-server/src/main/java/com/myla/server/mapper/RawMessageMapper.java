package com.myla.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.result.entity.RawMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * raw_message 表 Mapper。
 */
@Mapper
public interface RawMessageMapper extends BaseMapper<RawMessage> {
}
