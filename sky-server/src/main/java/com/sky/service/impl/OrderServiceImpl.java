package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {


    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;


    /**
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 异常情况的处理（如 超出配送范围）


        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null) {
            // 收货地址为空
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        // 查询购物车
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0) {
            // 购物车为空
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        // 构造订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);

        // 剩下数据从地址簿数据中找
        orders.setPhone(addressBook.getPhone());
        orders.setAddress(addressBook.getDetail());
        orders.setConsignee(addressBook.getConsignee());

        // 生成订单号就是当前时间
        orders.setNumber(String.valueOf(System.currentTimeMillis()));

        orders.setUserId(userId);
        // 设置订单状态
        orders.setStatus(Orders.PENDING_PAYMENT);

        orders.setPayStatus(Orders.UN_PAID);

        orders.setOrderTime(LocalDateTime.now());


        // 向订单表插入数据

        orderMapper.insert(orders);  // 要获取id


        // 构造订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();

        for(ShoppingCart shoppingCart1 : shoppingCartList) {
            // 遍历购物车
            OrderDetail orderDetail = new OrderDetail();

            BeanUtils.copyProperties(shoppingCart1, orderDetail);

            orderDetail.setOrderId(orders.getId());

            orderDetailList.add(orderDetail);
        }

        // 向订单明细表批量插入数据
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空购物车
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());

        // 封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();


        return orderSubmitVO;
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 使用微信支付工具类，调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 分页查询历史订单
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {

        PageHelper.startPage(pageNum, pageSize);

        // 封装参数
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();

        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        if(page != null && page.getTotal() > 0) {
            for(Orders orders : page) {
                // 获取订单id
                Long orderId = orders.getId();

                // 向订单明细表查出对应的订单详情
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                orderVO.setOrderDetailList(orderDetailList);

                list.add(orderVO);

            }
        }


        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     * @param orderId
     * @return
     */
    public OrderVO details(Long orderId) {

        // 先根据id从Order表里查订单
        Orders orders = orderMapper.getById(orderId);

        // 再到订单明细表里取出订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());


        // 封装成OrderVO返回
        OrderVO orderVO = new OrderVO();
        // OrderVO是继承自Order实体类
        BeanUtils.copyProperties(orders, orderVO);

        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     */
    public void userCancelById(Long id) {

        Orders orderDb = orderMapper.getById(id);

        if(orderDb == null) {
            // 如果订单不存在
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }


        if(orderDb.getStatus() > 2) {
            // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }


        // 创建一个Order对象，用于往Mapper层传参
        Orders orders = new Orders();
        // 设置订单id(主键)
        orders.setId(orderDb.getId());

        if(orderDb.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 如果订单处于待接单状态下被取消，需要进行退款
            try {
                // 调用微信支付工具类的退款接口
                weChatPayUtil.refund(
                        orderDb.getNumber(),
                        orders.getNumber(),
                        new BigDecimal(0.01),
                        new BigDecimal(0.01)
                );


                // 支付状态修改为 退款
                orders.setPayStatus(Orders.REFUND);
            } catch (Exception e) {
                throw new OrderBusinessException(MessageConstant.UNKNOWN_ERROR);
            }
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());

        //
        orderMapper.update(orders);

    }


    /**
     * 再来一单
     * @param id
     */
    public void repetition(Long id) {

        // 先获取当前user_id
        Long userId = BaseContext.getCurrentId();

        // 再去订单详情表里查
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 批量创建购物车对象 并放入数据库
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(
            x -> {
                ShoppingCart shoppingCart = new ShoppingCart();
                //   (id不要复制)
                BeanUtils.copyProperties(x, shoppingCart, "id");
                //
                shoppingCart.setUserId(userId);
                shoppingCart.setCreateTime(LocalDateTime.now());

                return shoppingCart;
            }
        ).collect(Collectors.toList());

        // 批量插入
        shoppingCartMapper.insertBatch(shoppingCartList);


    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {

        //
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        // 先对订单表分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 因为返回结果还要求有订单菜品，所以是OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各状态订单数量统计
     * @return
     */
    public OrderStatisticsVO statistics() {

        // 分别查询出待接单、待派送、派送中的订单数量 并返回

        // 待接单
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        // 待派送
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        // 派送中
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();

        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {

        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)   // 原参数并没包含status
                .build();

        // 往Mapper层传的参数用实体类或VO包装
        orderMapper.update(orders);

    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        //
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());

        if(orderDB == null || !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 订单只有存在且状态为2（待接单）才可以拒单
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 判断支付状态
        Integer payStatus = orderDB.getPayStatus();

        if(payStatus == Orders.PAID) {
            // 已支付，需退款
            String refund = null;
            try {
                refund = weChatPayUtil.refund(
                        orderDB.getNumber(),  // 订单号
                        orderDB.getNumber(),
                        new BigDecimal(0.01), // 退款金额
                        new BigDecimal(0.01)
                );

                log.info("申请退款: {}", refund);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        //
        Orders orders = new Orders();

        orders.setId(orderDB.getId());
        orders.setStatus(Orders.CANCELLED);

        // 设置拒单原因、取消时间
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelReason(ordersRejectionDTO.getRejectionReason());

        orders.setCancelTime(LocalDateTime.now());

        //
        orderMapper.update(orders);

    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) {

        //
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());


        // 判断支付状态
        Integer payStatus = orderDB.getPayStatus();

        if(payStatus == Orders.PAID) {
            // 已支付，需退款
            String refund = null;
            try {
                refund = weChatPayUtil.refund(
                        orderDB.getNumber(),  // 订单号
                        orderDB.getNumber(),
                        new BigDecimal(0.01), // 退款金额
                        new BigDecimal(0.01)
                );

                log.info("申请退款: {}", refund);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        //
        Orders orders = new Orders();

        orders.setId(orderDB.getId());
        orders.setStatus(Orders.CANCELLED);

        // 设置取消原因、取消时间
        orders.setCancelReason(ordersCancelDTO.getCancelReason());

        orders.setCancelTime(LocalDateTime.now());

        //
        orderMapper.update(orders);

    }

    /**
     *
     * @param id
     */
    public void delivery(Long id) {

        Orders orderDB = orderMapper.getById(id);

        if( orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            //
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }


        Orders orders = new Orders();

        orders.setId(id);
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    public void complete(Long id) {

        Orders orderDB = orderMapper.getById(id);

        if(orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            // 校验订单是否存在，并且状态为“派送中”
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();

        orders.setId(id);
        orders.setStatus(Orders.COMPLETED);
        //
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }


    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 封装订单的菜品信息
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();


        if(!CollectionUtils.isEmpty(ordersList)) {
            for(Orders orders: ordersList) {
                //
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO
                orderVO.setOrderDishes(orderDishes);

                orderVOList.add(orderVO);
            }
        }

        return orderVOList;
    }


    private String getOrderDishesStr(Orders orders) {
        // 将该订单对应的所有菜品信息拼接在一起返回

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        List<String> orderDishList = orderDetailList.stream().map(
                x->{
                   String orderDish = x.getName() + "*" + x.getNumber() + ";";

                   return orderDish;
                }).collect(Collectors.toList());

        return String.join("", orderDishList);

    }



}
