package com.aiknowledge.repository;

import com.aiknowledge.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 产品数据访问层
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * 根据分类查询产品
     */
    List<Product> findByCategory(String category);

    /**
     * 根据状态查询产品
     */
    List<Product> findByStatus(String status);

    /**
     * 根据名称模糊查询
     */
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:keyword%")
    List<Product> findByNameContaining(@Param("keyword") String keyword);

    /**
     * 查询库存不足的产品
     */
    @Query("SELECT p FROM Product p WHERE p.stock < :threshold")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);
}
