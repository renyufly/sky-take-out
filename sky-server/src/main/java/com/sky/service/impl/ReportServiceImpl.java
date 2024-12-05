package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ReportServiceImpl implements ReportService {


    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;


    /**
     * 获取营业额数据
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        // 存放从begin到end范围内的每天日期 （没有具体到时间）
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while(!begin.equals(end)) {
            // 计算日期
            begin = begin.plusDays(1);

            dateList.add(begin);
        }


        // 营业额
        List<Double> turnoverList = new ArrayList<>();

        for(LocalDate date: dateList) {
            // 根据要查的每一天去查营业额
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            // of() 方法是 LocalDateTime 类的一个静态工厂方法，用于创建一个新的 LocalDateTime 实例
            // 第一个参数表示日期部分，第二个表示最小的时间
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);


            Map map = new HashMap<>();

            map.put("status", Orders.COMPLETED);
            map.put("begin", beginTime);
            map.put("end", endTime);

            //
            Double turnover = orderMapper.sumByMap(map);

            turnover = turnover == null ? 0.0 : turnover;

            turnoverList.add(turnover);
        }


        // 使用lang3包下的StringUtils.join 拼接字符串
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();

        return turnoverReportVO;
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while(!begin.equals(end)) {

            begin = begin.plusDays(1);

            dateList.add(begin);
        }

        //
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();


        for(LocalDate date: dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);


            Integer newUser = getUserCount(beginTime, endTime);

            Integer totalUser = getUserCount(null, endTime);


            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }


        UserReportVO userReportVO = UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ",")).build();


        return userReportVO;

    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {

            begin = begin.plusDays(1);

            dateList.add(begin);
        }

        //
        List<Integer> orderCountList = new ArrayList<>();
        //
        List<Integer> validOrderCountList = new ArrayList<>();

        for(LocalDate date: dateList) {

            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Integer orderCount = getOrderCount(beginTime, endTime, null);

            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);

        }

        //
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        Integer totalValidOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();


        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0) {
            orderCompletionRate = totalValidOrderCount.doubleValue() / totalOrderCount.doubleValue();
        }


        OrderReportVO orderReportVO = OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .orderCompletionRate(orderCompletionRate)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(totalValidOrderCount)
                .build();

        return orderReportVO;

    }

    /**
     * 查询销量排名top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);


        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(begin, end);

        String nameList = StringUtils.join(goodsSalesDTOList.stream()
                .map(GoodsSalesDTO::getName).collect(Collectors.toList()), ",");

        String numberList = StringUtils.join(goodsSalesDTOList.stream()
                .map(GoodsSalesDTO::getNumber).collect(Collectors.toList()), ",");


        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList).build();

    }

    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {

        Map map = new HashMap<>();

        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", status);

        return orderMapper.countByMap(map);
    }


    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {

        Map map = new HashMap<>();

        map.put("begin", beginTime);
        map.put("end", endTime);


        return userMapper.countByMap(map);
    }


}
