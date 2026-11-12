package com.gjq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.entity.Category;
import java.util.List;

public interface CategoryService extends IService<Category> {
    List<Category> getAllCategories();
}
