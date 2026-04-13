package com.aiknowledge.service;

import com.aiknowledge.model.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 根据ID查询订单
     */
    Optional<Order> findById(String id);

    /**
     * 查询所有订单
     */
    List<Order> findAll();

    /**
     * 根据客户姓名查询
     */
    List<Order> findByCustomerName(String customerName);

    /**
     * 根据状态查询
     */
    List<Order> findByStatus(String status);

    /**
     * 根据产品ID查询
     */
    List<Order> findByProductId(String productId);

    /**
     * 查询客户的某状态订单
     */
    List<Order> findByCustomerAndStatus(String customerName, String status);

    /**
     * 统计某状态订单数量
     */
    Long countByStatus(String status);

    /**
     * 保存订单
     */
    Order save(Order order);

    /**
     * 删除订单
     */
    void deleteById(String id);
}
