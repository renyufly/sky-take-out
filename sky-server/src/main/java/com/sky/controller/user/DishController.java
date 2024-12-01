package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {

        // 先向Redis缓存中查询  (存的是分类id对应下所有启售的菜品)
        String redis_key = "dish_" +  categoryId;

        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(redis_key);

        if(list != null && list.size() > 0) {
            return Result.success(list);
        }

        // ↓ Redis中没有对应数据，再向数据库中查


        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询启售中的菜品

        // 查询当前分类id下的启售的所有菜品（附带口味），返回的是DishVO类型
        list = dishService.listWithFlavor(dish);

        // ↓ 从数据库中查询到的数据再存入Redis缓存
        redisTemplate.opsForValue().set(redis_key, list);


        return Result.success(list);
    }

}
