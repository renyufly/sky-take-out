package com.sky.controller.admin;


import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 菜品管理
 */
@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;


    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 清理Redis缓存数据
     * @param pattern
     */
    private void cleanCache(String pattern) {
        // 根据pattern获取对应匹配到的Redis中的所有key
        Set keys = redisTemplate.keys(pattern);

        // 删除这些key 及 对应的value
        redisTemplate.delete(keys);

    }



    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}", dishDTO);

        dishService.saveWithFlavor(dishDTO);


        // 菜品信息修改后，清理缓存数据
        String redis_key = "dish_" + dishDTO.getCategoryId();
        // 将对应分类id数据全部清除，这样下次用户查询时是加入了新的菜品
        cleanCache(redis_key);


        return Result.success();
    }


    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);

        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);

        return Result.success(pageResult);
    }


    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping()
    @ApiOperation("批量删除菜品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("菜品批量删除：{}", ids);

        dishService.deleteBatch(ids);

        // 修改完菜品数据后，清理Redish缓存 (直接把整个redis的数据清空)
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品：{}", id);

        DishVO dishVO = dishService.getByIdWithFlavor(id);

        return Result.success(dishVO);
    }


    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO) {

        log.info("修改菜品：{}", dishDTO);

        dishService.updateWithFlavor(dishDTO);

        // 清除 全部 的缓存 (可能修改的是菜品的分类；修改分类的操作实际很少，所以就不复杂化处理判断是否修改了分类)
        cleanCache("dish_*");


        return Result.success();
    }


    /**
     * 根据分类id查询菜品集合
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品集合")
    public Result<List<Dish>> list(Long categoryId) {
        log.info("根据分类id查询菜品集合: {}", categoryId);

        List<Dish> dishList = dishService.list(categoryId);


        return Result.success(dishList);
    }

    /**
     * 菜品启售停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品启售停售")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("菜品启售停售:{}, {}", status, id);

        dishService.startOrStop(status, id);


        // 直接清除所有缓存 （也可以通过指定分类id删除对应的。但传入参数是菜品id，还需要再次查询数据库）
        cleanCache("dish_*");


        return Result.success();
    }


}
