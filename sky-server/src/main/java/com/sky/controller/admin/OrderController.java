package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 订单相关接口
 */
@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Api(tags = "管理端-订单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;


    /**
     * 订单搜索
     * @return
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("订单搜索");

        PageResult pageResult = orderService.conditionSearch(ordersPageQueryDTO);

        return Result.success(pageResult);
    }


    /**
     * 各状态订单数量统计
     * @return
     */
    @GetMapping("/statistics")
    @ApiOperation("各状态订单数量统计")
    public Result<OrderStatisticsVO> statistics() {
        log.info("各状态订单数量统计");

        OrderStatisticsVO orderStatisticsVO = orderService.statistics();

        return Result.success(orderStatisticsVO);

    }



    /**
     * 查询订单详情
     * @return
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        log.info("查询订单详情: {}", id);

        OrderVO orderVO = orderService.details(id);

        return Result.success(orderVO);
    }


    /**
     * 接单
     * @return
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单: {}", ordersConfirmDTO);

        orderService.confirm(ordersConfirmDTO);

        return Result.success();

    }


    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {

        log.info("拒单");

        orderService.rejection(ordersRejectionDTO);

        return Result.success();
    }


    /**
     * 取消订单
     * @return
     */
    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("取消订单: {}", ordersCancelDTO);

        orderService.cancel(ordersCancelDTO);

        return Result.success();
    }


    /**
     * 派送订单
     * @param id
     * @return
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result delivery(@PathVariable Long id) {
        log.info("派送订单: {}", id);

        orderService.delivery(id);


        return Result.success();
    }


    /**
     * 完成订单
     * @param id
     * @return
     */
    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result complete(@PathVariable("id") Long id) {
        log.info("完成订单: {}", id);

        orderService.complete(id);

        return Result.success();
    }


}
