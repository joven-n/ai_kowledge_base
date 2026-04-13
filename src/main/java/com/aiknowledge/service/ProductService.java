package com.aiknowledge.service;

import com.aiknowledge.model.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * 产品服务接口
 */
public interface ProductService {

    /**
     * 根据ID查询产品
     */
    Optional<Product> findById(String id);

    /**
     * 查询所有产品
     */
    List<Product> findAll();

    /**
     * 根据分类查询
     */
    List<Product> findByCategory(String category);

    /**
     * 根据名称模糊查询
     */
    List<Product> findByName(String name);

    /**
     * 根据状态查询
     */
    List<Product> findByStatus(String status);

    /**
     * 查询低库存产品
     */
    List<Product> findLowStockProducts(Integer threshold);

    /**
     * 保存产品
     */
    Product save(Product product);

    /**
     * 删除产品
     */
    void deleteById(String id);
}
