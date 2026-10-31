package com.tzfun.tzfun.mapper;

import com.tzfun.tzfun.entity.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单表 Mapper。
 */
public interface OrderMapper {

    /** 仍占用库存的订单（下单/已确认/使用中），供 rebuildFromDb 聚合。 */
    @Select("SELECT * FROM orders WHERE store_id = #{storeId} AND car_type_id = #{carTypeId} " +
            "AND status IN ('PENDING', 'CONFIRMED', 'IN_USE')")
    List<Order> selectActive(@Param("storeId") Long storeId, @Param("carTypeId") Long carTypeId);

    @Select("SELECT * FROM orders WHERE id = #{id}")
    Order selectById(Long id);

    /** 超时未支付订单：PENDING 且创建时间早于 N 分钟前，供定时任务兜底扫描。 */
    @Select("SELECT * FROM orders WHERE status = 'PENDING' " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)")
    List<Order> selectTimeoutPending(@Param("minutes") int minutes);

    @Insert("INSERT INTO orders(order_no, user_id, store_id, car_type_id, start_time, end_time, status, " +
            "total_amount, discount_amount, pay_amount, create_time, update_time) " +
            "VALUES(#{orderNo}, #{userId}, #{storeId}, #{carTypeId}, #{startTime}, #{endTime}, #{status}, " +
            "#{totalAmount}, #{discountAmount}, #{payAmount}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    /** 状态流转：条件更新，防止并发覆盖。 */
    @Update("UPDATE orders SET status = #{newStatus}, update_time = NOW() " +
            "WHERE id = #{id} AND status = #{oldStatus}")
    int updateStatus(@Param("id") Long id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);

    /** 取车回写：分配车辆 + 取车信息。 */
    @Update("UPDATE orders SET car_id = #{carId}, pickup_info = #{pickupInfo}, update_time = NOW() WHERE id = #{id}")
    int fillPickup(@Param("id") Long id, @Param("carId") Long carId, @Param("pickupInfo") String pickupInfo);

    /** 还车回写：还车信息 + 追加额外费到实付金额。 */
    @Update("UPDATE orders SET return_info = #{returnInfo}, pay_amount = pay_amount + #{extraFee}, update_time = NOW() " +
            "WHERE id = #{id}")
    int fillReturn(@Param("id") Long id, @Param("returnInfo") String returnInfo, @Param("extraFee") BigDecimal extraFee);

    /** 支付回写：用券后更新折扣金额、实付金额、关联券 id。 */
    @Update("UPDATE orders SET discount_amount = #{discountAmount}, pay_amount = #{payAmount}, " +
            "coupon_id = #{couponId}, update_time = NOW() WHERE id = #{id}")
    int updateDiscount(@Param("id") Long id, @Param("discountAmount") BigDecimal discountAmount,
                       @Param("payAmount") BigDecimal payAmount, @Param("couponId") Long couponId);
}
