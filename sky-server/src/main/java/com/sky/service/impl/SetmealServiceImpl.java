package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;


    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐, 同时需要保存套餐和菜品的关联关系
     * @param setmealDTO
     */

    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {

        Setmeal setmeal = new Setmeal();

        BeanUtils.copyProperties(setmealDTO, setmeal);

        // 向Setmeal表插入数据
        setmealMapper.insert(setmeal);

        // 插入完后获取到该套餐的id (需要在xml中SQL的参数中设置)
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        // 设置套餐id
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        // 向setmeal_dish表批量插入数据
        setmealDishMapper.insertBatch(setmealDishes);


    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {

        // 获取第几页和页大小
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        // 使用PageHelper
        PageHelper.startPage(pageNum, pageSize);

        // 还要返回categoryName，所以使用VO而不是实体类
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id批量删除套餐
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {

        // 先判断套餐是不是启售，如果是启售状态，整个操作要取消
        ids.forEach(id->{
            Setmeal setmeal = setmealMapper.getById(id);
            if(StatusConstant.ENABLE == setmeal.getStatus()) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });

        /* TODO 可以优化成批量删除的语句 */
        ids.forEach(id->{
            // 删除套餐表
            setmealMapper.deleteById(id);
            // 同时删除关联表
            setmealDishMapper.deleteBySetmealId(id);
        });

    }

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    public SetmealVO getByIdWithDish(Long id) {

        SetmealVO setmealVO = new SetmealVO();

        Setmeal setmeal = setmealMapper.getById(id);

        BeanUtils.copyProperties(setmeal, setmealVO);

        // 查询关联的菜品
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

        setmealVO.setSetmealDishes(setmealDishes);

        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        // 向套餐表修改数据
        setmealMapper.update(setmeal);

        // 根据套餐id删除原来关联表中的数据
        Long setmealId = setmeal.getId();  // 注意在xml中要配置参数

        setmealDishMapper.deleteBySetmealId(setmealId);

        // 再向关联表添加新的数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            // 先设置setmealId
            setmealDish.setSetmealId(setmealId);
        });

        // 批量插入
        setmealDishMapper.insertBatch(setmealDishes);


    }

    /**
     * 套餐启售停售
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        // 如果要启售套餐，需判断套餐里有无停售菜品，若有停售则整个操作取消
        if(status == StatusConstant.ENABLE) {
            // 获取关联的所有菜品 (借助关联表与dish表的拼接)
            List<Dish> dishList = dishMapper.getBySetmealId(id);

            if(dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if(dish.getStatus() == StatusConstant.DISABLE) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }

        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();

        // 直接update数据即可
        setmealMapper.update(setmeal);


    }



    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }


}
