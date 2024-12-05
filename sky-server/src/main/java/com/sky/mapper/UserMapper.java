package com.sky.mapper;


import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {


    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);


    /**
     * 插入用户数据 （只有create_time，所以不用Autofill）
     * @param user
     */
    void insert(User user);

    /**
     * 查询用户
     * @param userId
     * @return
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);


    /**
     * 查询时间内的新增用户和总用户
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
