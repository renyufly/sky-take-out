package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;


/**
 * 店铺操作
 */
@RestController("adminShopController")
@RequestMapping("/admin/shop")
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
     * 设置营业状态
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置营业状态: {}", status == 1 ? "营业中" : "打烊中");

        // 直接操作redisTemplate，不用去Mapper层
        redisTemplate.opsForValue().set(KEY, status);

        return Result.success();
    }


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
