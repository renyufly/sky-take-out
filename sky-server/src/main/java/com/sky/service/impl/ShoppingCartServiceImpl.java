package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;


@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;


    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {

        // Mapper层中操作的是实体类
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        // 当前用户id操作购物车
        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 查询当前商品是否在购物车中（如果在，就只修改数量）
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList != null && shoppingCartList.size() == 1) {
            shoppingCart = shoppingCartList.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);

            // 修改完商品数量后，再update回Mapper中的表
            shoppingCartMapper.updateNumberById(shoppingCart);
        } else {
            // 新添加进购物车表

            Long dishId = shoppingCart.getDishId();

            if(dishId != null) {
                // 添加的是菜品
                Dish dish = dishMapper.getById(dishId);

                // 从数据库查询到该菜品的信息，并赋值给购物车对象
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());

            } else {
                // 添加的是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCart.getSetmealId());

                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());

            }

            // 数量设置为1，插入购物车表中
            shoppingCart.setNumber(1);

            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartMapper.insert(shoppingCart);

        }


    }


    /**
     * 查看购物车
     * @return
     */
    public List<ShoppingCart> list() {

        // 往Mapper层传的是实体类或VO
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())    // 设置user_id
                .build();

        // 根据当前user_id 查询购物车中所有商品
        return shoppingCartMapper.list(shoppingCart);
    }


    /**
     * 清空购物车
     */
    public void cleanShoppingCart() {

        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());

    }


    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     */
    public void subFromShoppingCart(ShoppingCartDTO shoppingCartDTO) {

        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        shoppingCart.setUserId(BaseContext.getCurrentId());

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        // 如果购物车中该商品数量为1，就直接删除；否则将Number减一
        if(shoppingCartList != null && shoppingCartList.size() == 1) {
            shoppingCart = shoppingCartList.get(0);

            if(shoppingCart.getNumber() == 1) {
                shoppingCartMapper.delete(shoppingCart);
            } else {
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }


        }



    }
}
