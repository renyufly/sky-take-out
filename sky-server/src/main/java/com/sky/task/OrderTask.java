package com.sky.task;


import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 自定义定时任务类：实现订单状态的定时处理
 */

@Component
@Slf4j
public class OrderTask {


    @Autowired
    private OrderMapper orderMapper;


    /**
     * 处理支付超时订单 (每过 1 min 自动执行)
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        log.info("处理支付超时订单: {}", LocalDateTime.now());


        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        // 查询 订单状态为“待支付” 且 下单时间 < "当前时间 - 15 min" （即超过15min还未支付的）
        List<Orders> ordersList = orderMapper.getByStatusAndOrdertimeLT(Orders.PENDING_PAYMENT, time);


        if(ordersList != null && ordersList.size() > 0) {

            ordersList.forEach(orders -> {

                // 修改订单的状态为“已取消”，并设置取消理由、取消时间
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("支付超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());

                orderMapper.update(orders);
            });


        }


    }

    /**
     * 处理派送中订单 (每天凌晨1点自动执行)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        log.info("处理派送中订单: {}", LocalDateTime.now());


        // 查询 1 h 以上还处于“派送中”的订单
        LocalDateTime time = LocalDateTime.now().plusHours(-1);

        List<Orders> ordersList = orderMapper.getByStatusAndOrdertimeLT(Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now());


        // 设置订单为“已完成”
        if(ordersList != null && ordersList.size() > 0) {

            ordersList.forEach(orders -> {

                orders.setStatus(Orders.COMPLETED);

                orderMapper.update(orders);
            });

        }

    }


}
