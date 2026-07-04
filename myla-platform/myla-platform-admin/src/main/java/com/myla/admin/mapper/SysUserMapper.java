package com.myla.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * MYLA 系统用户数据访问层。
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 CRUD 操作方法，
 * 操作方法包括 insert、deleteById、updateById、selectById 等。
 * 如需复杂查询，可在此接口中声明自定义方法并在 XML 中实现。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
