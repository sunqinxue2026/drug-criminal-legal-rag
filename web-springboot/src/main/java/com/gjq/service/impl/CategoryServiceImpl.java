package com.gjq.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.entity.Category;
import com.gjq.mapper.CategoryMapper;
import com.gjq.service.CategoryService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> getAllCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSort);
        return this.list(wrapper);
    }
}
