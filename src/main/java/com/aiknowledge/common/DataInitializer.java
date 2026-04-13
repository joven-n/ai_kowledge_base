package com.aiknowledge.common;

import com.aiknowledge.model.entity.Order;
import com.aiknowledge.model.entity.Product;
import com.aiknowledge.repository.OrderRepository;
import com.aiknowledge.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 数据初始化器
 * 应用启动时自动插入产品和订单种子数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
//public class DataInitializer implements CommandLineRunner {
public class DataInitializer {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /*@Override
    @Transactional
    public void run(String... args) {
        log.info("开始初始化种子数据...");

        // 初始化产品数据（如果表为空）
        if (productRepository.count() == 0) {
            initProducts();
        }

        // 初始化订单数据（如果表为空）
        if (orderRepository.count() == 0) {
            initOrders();
        }

        log.info("种子数据初始化完成！");
    }*/

    private void initProducts() {
        log.info("初始化产品数据...");

        List<Product> products = Arrays.asList(
                Product.builder()
                        .id("P001")
                        .name("智能蓝牙耳机 Pro")
                        .category("数码配件")
                        .price(new BigDecimal("299.00"))
                        .stock(150)
                        .status("在售")
                        .description("主动降噪，续航30小时，Hi-Res音质认证")
                        .build(),
                Product.builder()
                        .id("P002")
                        .name("机械键盘 K8")
                        .category("电脑外设")
                        .price(new BigDecimal("459.00"))
                        .stock(80)
                        .status("在售")
                        .description("87键热插拔，RGB背光，三模连接")
                        .build(),
                Product.builder()
                        .id("P003")
                        .name("4K显示器 27寸")
                        .category("显示设备")
                        .price(new BigDecimal("1899.00"))
                        .stock(25)
                        .status("在售")
                        .description("IPS面板，99% sRGB色域，Type-C 65W反向供电")
                        .build(),
                Product.builder()
                        .id("P004")
                        .name("无线鼠标 M2")
                        .category("电脑外设")
                        .price(new BigDecimal("129.00"))
                        .stock(200)
                        .status("在售")
                        .description("轻量化设计，16000DPI，双模连接")
                        .build(),
                Product.builder()
                        .id("P005")
                        .name("USB-C 扩展坞")
                        .category("数码配件")
                        .price(new BigDecimal("199.00"))
                        .stock(5)
                        .status("库存紧张")
                        .description("10合1扩展，4K HDMI输出，千兆网口")
                        .build(),
                Product.builder()
                        .id("P006")
                        .name("智能音箱 Mini")
                        .category("智能家居")
                        .price(new BigDecimal("149.00"))
                        .stock(0)
                        .status("缺货")
                        .description("语音助手，智能家居控制，高音质音响")
                        .build(),
                Product.builder()
                        .id("P007")
                        .name("平板电脑支架")
                        .category("数码配件")
                        .price(new BigDecimal("79.00"))
                        .stock(300)
                        .status("在售")
                        .description("铝合金材质，多角度调节，折叠便携")
                        .build(),
                Product.builder()
                        .id("P008")
                        .name("高速固态硬盘 1TB")
                        .category("存储设备")
                        .price(new BigDecimal("599.00"))
                        .stock(60)
                        .status("在售")
                        .description("NVMe协议，读写速度3500MB/s，五年质保")
                        .build(),
                Product.builder()
                        .id("P009")
                        .name("游戏手柄 X1")
                        .category("游戏配件")
                        .price(new BigDecimal("269.00"))
                        .stock(45)
                        .status("在售")
                        .description("霍尔摇杆，蓝牙5.0，支持PC/Switch/手机")
                        .build(),
                Product.builder()
                        .id("P010")
                        .name("复古胶片相机")
                        .category("摄影器材")
                        .price(new BigDecimal("2599.00"))
                        .stock(8)
                        .status("在售")
                        .description("35mm定焦镜头，经典胶片模拟，便携设计")
                        .build()
        );

        productRepository.saveAll(products);
        log.info("已插入 {} 条产品数据", products.size());
    }

    private void initOrders() {
        log.info("初始化订单数据...");

        List<Order> orders = Arrays.asList(
                // 张三的订单
                Order.builder()
                        .id("O20240001")
                        .productId("P001")
                        .productName("智能蓝牙耳机 Pro")
                        .customerName("张三")
                        .quantity(1)
                        .totalAmount(new BigDecimal("299.00"))
                        .status("已完成")
                        .trackingNo("SF1234567890")
                        .shippedAt(LocalDateTime.now().minusDays(5))
                        .build(),
                Order.builder()
                        .id("O20240002")
                        .productId("P002")
                        .productName("机械键盘 K8")
                        .customerName("张三")
                        .quantity(1)
                        .totalAmount(new BigDecimal("459.00"))
                        .status("待发货")
                        .build(),

                // 李四的订单
                Order.builder()
                        .id("O20240003")
                        .productId("P003")
                        .productName("4K显示器 27寸")
                        .customerName("李四")
                        .quantity(2)
                        .totalAmount(new BigDecimal("3798.00"))
                        .status("已发货")
                        .trackingNo("JD9876543210")
                        .shippedAt(LocalDateTime.now().minusDays(1))
                        .build(),
                Order.builder()
                        .id("O20240004")
                        .productId("P004")
                        .productName("无线鼠标 M2")
                        .customerName("李四")
                        .quantity(3)
                        .totalAmount(new BigDecimal("387.00"))
                        .status("已完成")
                        .trackingNo("SF1122334455")
                        .shippedAt(LocalDateTime.now().minusDays(10))
                        .build(),

                // 王五的订单
                Order.builder()
                        .id("O20240005")
                        .productId("P001")
                        .productName("智能蓝牙耳机 Pro")
                        .customerName("王五")
                        .quantity(2)
                        .totalAmount(new BigDecimal("598.00"))
                        .status("待付款")
                        .build(),
                Order.builder()
                        .id("O20240006")
                        .productId("P005")
                        .productName("USB-C 扩展坞")
                        .customerName("王五")
                        .quantity(1)
                        .totalAmount(new BigDecimal("199.00"))
                        .status("已取消")
                        .build(),

                // 赵六的订单
                Order.builder()
                        .id("O20240007")
                        .productId("P008")
                        .productName("高速固态硬盘 1TB")
                        .customerName("赵六")
                        .quantity(1)
                        .totalAmount(new BigDecimal("599.00"))
                        .status("已完成")
                        .trackingNo("YT5566778899")
                        .shippedAt(LocalDateTime.now().minusDays(7))
                        .build(),
                Order.builder()
                        .id("O20240008")
                        .productId("P009")
                        .productName("游戏手柄 X1")
                        .customerName("赵六")
                        .quantity(2)
                        .totalAmount(new BigDecimal("538.00"))
                        .status("待发货")
                        .build(),

                // 孙七的订单
                Order.builder()
                        .id("O20240009")
                        .productId("P002")
                        .productName("机械键盘 K8")
                        .customerName("孙七")
                        .quantity(1)
                        .totalAmount(new BigDecimal("459.00"))
                        .status("已完成")
                        .trackingNo("SF9988776655")
                        .shippedAt(LocalDateTime.now().minusDays(3))
                        .build(),
                Order.builder()
                        .id("O20240010")
                        .productId("P007")
                        .productName("平板电脑支架")
                        .customerName("孙七")
                        .quantity(5)
                        .totalAmount(new BigDecimal("395.00"))
                        .status("已发货")
                        .trackingNo("JD1122334455")
                        .shippedAt(LocalDateTime.now().minusDays(2))
                        .build(),

                // 周八的订单
                Order.builder()
                        .id("O20240011")
                        .productId("P010")
                        .productName("复古胶片相机")
                        .customerName("周八")
                        .quantity(1)
                        .totalAmount(new BigDecimal("2599.00"))
                        .status("待发货")
                        .build(),
                Order.builder()
                        .id("O20240012")
                        .productId("P003")
                        .productName("4K显示器 27寸")
                        .customerName("周八")
                        .quantity(1)
                        .totalAmount(new BigDecimal("1899.00"))
                        .status("待付款")
                        .build(),

                // 吴九的订单
                Order.builder()
                        .id("O20240013")
                        .productId("P004")
                        .productName("无线鼠标 M2")
                        .customerName("吴九")
                        .quantity(2)
                        .totalAmount(new BigDecimal("258.00"))
                        .status("已完成")
                        .trackingNo("SF5566778899")
                        .shippedAt(LocalDateTime.now().minusDays(8))
                        .build(),
                Order.builder()
                        .id("O20240014")
                        .productId("P001")
                        .productName("智能蓝牙耳机 Pro")
                        .customerName("吴九")
                        .quantity(1)
                        .totalAmount(new BigDecimal("299.00"))
                        .status("已完成")
                        .trackingNo("YT1122334455")
                        .shippedAt(LocalDateTime.now().minusDays(12))
                        .build(),

                // 郑十的订单
                Order.builder()
                        .id("O20240015")
                        .productId("P005")
                        .productName("USB-C 扩展坞")
                        .customerName("郑十")
                        .quantity(3)
                        .totalAmount(new BigDecimal("597.00"))
                        .status("已发货")
                        .trackingNo("JD9988776655")
                        .shippedAt(LocalDateTime.now().minusDays(1))
                        .build()
        );

        orderRepository.saveAll(orders);
        log.info("已插入 {} 条订单数据", orders.size());
    }
}
