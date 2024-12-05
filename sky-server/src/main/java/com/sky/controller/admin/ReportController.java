package com.sky.controller.admin;


import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计报表相关接口
 */
@RestController
@RequestMapping("/admin/report")
@Slf4j
@Api(tags = "统计报表相关接口")
public class ReportController {

    @Autowired
    private ReportService reportService;


    /**
     * 营业额统计
     * @return
     */
    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd")LocalDate begin,
                                                       @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        // 用注解来描述日期格式，用于封装传入的参数
        log.info("营业额统计: {}, {}", begin, end);

        TurnoverReportVO turnoverReportVO = reportService.getTurnover(begin, end);

        return Result.success(turnoverReportVO);

    }


    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                               @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {

        log.info("用户统计: {}, {}", begin, end);

        UserReportVO userReportVO = reportService.getUserStatistics(begin, end);


        return Result.success(userReportVO);

    }


    /**
     * 订单统计接口
     * @return
     */
    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                                  @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {

        log.info("订单统计: {}, {}", begin, end);

        OrderReportVO orderReportVO = reportService.getOrdersStatistics(begin, end);

        return Result.success(orderReportVO);
    }


    /**
     * 查询销量排名top10
     * @param begin
     * @param end
     * @return
     */
    @GetMapping("/top10")
    @ApiOperation("查询销量排名top10")
    public Result<SalesTop10ReportVO> top10(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("查询销量排名top10: {}, {}", begin, end);


        SalesTop10ReportVO salesTop10ReportVO = reportService.getSalesTop10(begin, end);

        return Result.success(salesTop10ReportVO);

    }


    /**
     * 导出Excel报表
     * @return
     */
    @GetMapping("/export")
    @ApiOperation("导出Excel报表")
    public Result export(HttpServletResponse response) {
        log.info("导出Excel报表接口");

        reportService.exportBusinessData(response);

        return Result.success();
    }


}
