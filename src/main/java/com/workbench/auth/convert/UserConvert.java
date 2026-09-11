package com.workbench.auth.convert;

import com.workbench.auth.dto.UserVO;
import com.workbench.auth.entity.User;
import org.mapstruct.Mapper;

/** componentModel=spring：生成实现类并注册为 Bean，通过构造器注入使用（非 INSTANCE 静态方式） */
@Mapper(componentModel = "spring")
public interface UserConvert {

    /** 同名字段自动映射；password 不在 UserVO 中，天然不会带出 */
    UserVO toVO(User user);
}
