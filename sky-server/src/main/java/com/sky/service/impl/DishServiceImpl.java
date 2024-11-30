package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品和对应的口味
     * @param dishDTO
     */
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        // 属性拷贝 （往Mapper层传实体类）
        BeanUtils.copyProperties(dishDTO, dish);

        // 向菜品表插入1条数据
        dishMapper.insert(dish);

        // 获得刚插完数据后的主键值（需要在xml里使用useGeneratedKey = true 且 keyProperty = "id" 赋值给dish的id属性）
        Long id = dish.getId();

        // 向口味表插入 n 条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();

        if(flavors != null && flavors.size() > 0) {
            // lambda表达式
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(id);
            });
            dishFlavorMapper.insertBatch(flavors);
        }

    }


    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 选中的批量菜品只要至少有一个是启售状态，整个操作就要取消
        for(Long id: ids) {
            // 遍历菜品id，查询得到该菜品
            Dish dish = dishMapper.getById(id);

            if(dish.getStatus() == StatusConstant.ENABLE){
                // 启售中的菜品不能被删除 （进入global异常处理器）
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        // 选中的批量菜品只要有至少一个被套餐关联，整个操作就要取消
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);

        if(setmealIds != null && setmealIds.size() > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 可以正常删除菜品
        /*for(Long id: ids) {
            dishMapper.deleteById(id);
            // 注意菜品表和口味表要同时删除
            dishFlavorMapper.deleteByDishId(id);
        }*/

        // 优化：根据菜品id批量删除菜品
        dishMapper.deleteByIds(ids);

        // 优化：根据菜品id批量删除关联的口味
        dishFlavorMapper.deleteByDishIds(ids);


    }


    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id) {

        Dish dish = dishMapper.getById(id);

        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        DishVO dishVO = new DishVO();

        BeanUtils.copyProperties(dish, dishVO);

        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }


    /**
     * 修改菜品
     * @param dishDTO
     */
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        // dish里并没有口味数据
        BeanUtils.copyProperties(dishDTO, dish);

        dishMapper.update(dish);


        // 先删除口味表中原有的数据，再添加新的口味
        dishFlavorMapper.deleteByDishId(dish.getId());

        List<DishFlavor> dishFlavors = dishDTO.getFlavors();
        if(dishFlavors != null && dishFlavors.size() > 0) {
            dishFlavors.forEach(dishFlavor -> {
                // 先给每个口味数据设置菜品id
                dishFlavor.setDishId(dish.getId());
            });
            // 批量插入口味数据
            dishFlavorMapper.insertBatch(dishFlavors);
        }





    }
}
