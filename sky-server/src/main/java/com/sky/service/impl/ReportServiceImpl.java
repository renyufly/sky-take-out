package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
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

    @Autowired
    private WorkspaceService workspaceService;


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

    /**
     * 导出近30天的Excel报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) {

        // 查询近30天的日期
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 查询概览运营数据 （Impl实现类中也可以注入别的service）
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX));

        // 反射获得类对象，类加载器 获得一个输入流对象
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/template.xlsx");

        try {

            //基于提供好的模板文件输入流对象 创建 一个新的Excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);

            // 获得Excel文件中的一个Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");
            // 获取行 单元格并填充时间数据（行索引从0开始）
            sheet.getRow(1).getCell(1).setCellValue(begin + "to" + end);

            // 获得第4行对象
            XSSFRow row = sheet.getRow(3);
            // 获取单元格 并 填充数据（营业额、有效订单率、新增用户数）
            row.getCell(2).setCellValue(businessData.getTurnover());

            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());

            row.getCell(6).setCellValue(businessData.getNewUsers());

            // 切换行 并 填充数据
            row = sheet.getRow(4);

            row.getCell(2).setCellValue(businessData.getValidOrderCount());

            row.getCell(4).setCellValue(businessData.getUnitPrice()); // 平均客单价


            for(int i=0; i<30; i++) {
                // 遍历每天的数据
                LocalDate data = begin.plusDays(i);

                // 准备明细数据 （按天查询）
                businessData = workspaceService.getBusinessData(LocalDateTime.of(data, LocalTime.MIN),
                        LocalDateTime.of(data, LocalTime.MAX));

                // 填充明细数据
                row = sheet.getRow(7 + i);

                row.getCell(1).setCellValue(data.toString());


                row.getCell(2).setCellValue(businessData.getTurnover());

                row.getCell(3).setCellValue(businessData.getValidOrderCount());

                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());

                row.getCell(5).setCellValue(businessData.getUnitPrice());

                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            // 通过输出流将文件下载到客户端浏览器中
            ServletOutputStream outputStream = response.getOutputStream();

            // 写回浏览器
            excel.write(outputStream);

            // 关闭资源
            outputStream.flush();
            outputStream.close();
            excel.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * 获取订单数
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {

        Map map = new HashMap<>();

        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", status);

        return orderMapper.countByMap(map);
    }


    /**
     * 获取用户数
     * @param beginTime
     * @param endTime
     * @return
     */
    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {

        Map map = new HashMap<>();

        map.put("begin", beginTime);
        map.put("end", endTime);


        return userMapper.countByMap(map);
    }


}
