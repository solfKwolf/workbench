package com.workbench.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** @Mapper 注解使 MyBatis-Plus starter 自动扫描注册（无需 @MapperScan） */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
