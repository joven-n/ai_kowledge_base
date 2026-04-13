package com.aiknowledge.repository;

import com.aiknowledge.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单数据访问层
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * 根据客户姓名查询订单
     */
    List<Order> findByCustomerName(String customerName);

    /**
     * 根据订单状态查询
     */
    List<Order> findByStatus(String status);

    /**
     * 根据产品ID查询订单
     */
    List<Order> findByProductId(String productId);

    /**
     * 根据客户和状态查询
     */
    @Query("SELECT o FROM Order o WHERE o.customerName = :customerName AND o.status = :status")
    List<Order> findByCustomerAndStatus(@Param("customerName") String customerName, @Param("status") String status);

    /**
     * 统计某状态的订单数量
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(@Param("status") String status);
}
