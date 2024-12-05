package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入数据
     * @param orders
     */
    void insert(Orders orders);


    /**
     * 根据订单号（Number）查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);


    /**
     * 根据订单号和user_id查询订单
     * @param outTradeNo
     * @param userId
     * @return
     */
    @Select("select * from orders where number = #{outTradeNo} and user_id = #{userId}")
    Orders getByNumberAndUserId(String outTradeNo, Long userId);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);


    /**
     * 订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据订单id（主键）查询订单信息
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据订单状态查询数量
     * @param status
     * @return
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);


    /**
     * 根据订单状态、下单时间查询订单
     * @param status
     * @param time
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{time}")
    List<Orders> getByStatusAndOrdertimeLT(Integer status, LocalDateTime time);


    /**
     * 根据集合参数查询金额合计
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据集合参数查询订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);


    /**
     * 查询销量排名top10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(LocalDate begin, LocalDate end);
}
