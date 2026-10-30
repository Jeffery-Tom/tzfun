package com.tzfun.tzfun.mapper;

import com.tzfun.tzfun.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper（注解版，由启动类 @MapperScan 扫描）。
 */
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE username = #{username}")
    User selectByUsername(String username);

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User selectById(Long id);

    @Insert("INSERT INTO `user`(username, password, role, name, phone, create_time) " +
            "VALUES(#{username}, #{password}, #{role}, #{name}, #{phone}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}
