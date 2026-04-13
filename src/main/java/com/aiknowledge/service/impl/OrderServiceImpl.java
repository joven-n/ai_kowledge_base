package com.aiknowledge.service.impl;

import com.aiknowledge.model.entity.Order;
import com.aiknowledge.repository.OrderRepository;
import com.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public Optional<Order> findById(String id) {
        return orderRepository.findById(id);
    }

    @Override
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Override
    public List<Order> findByCustomerName(String customerName) {
        return orderRepository.findByCustomerName(customerName);
    }

    @Override
    public List<Order> findByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    @Override
    public List<Order> findByProductId(String productId) {
        return orderRepository.findByProductId(productId);
    }

    @Override
    public List<Order> findByCustomerAndStatus(String customerName, String status) {
        return orderRepository.findByCustomerAndStatus(customerName, status);
    }

    @Override
    public Long countByStatus(String status) {
        return orderRepository.countByStatus(status);
    }

    @Override
    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        orderRepository.deleteById(id);
    }
}
