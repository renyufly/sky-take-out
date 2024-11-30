package com.sky.controller.user;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;


/**
 * 店铺操作  (指定下Bean的名称，避免同名类的冲突)
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺操作相关接口")
@Slf4j
public class ShopController {

    // redis的key
    public static final String KEY = "SHOP_STATUS";


    /**
     * 使用Redis
     */
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 查询店铺状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("查询店铺状态")
    public Result<Integer> getStatus() {

        // 直接调用redisTemplate，使用 get key
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);

        log.info("查询店铺状态: {}", status == 1 ? "营业中" : "打烊中");

        return Result.success(status);
    }


}
