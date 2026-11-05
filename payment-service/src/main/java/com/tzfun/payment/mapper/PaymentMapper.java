package com.tzfun.payment.mapper;

import com.tzfun.payment.entity.Payment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付单 Mapper。
 */
public interface PaymentMapper {

    @Select("SELECT * FROM payment WHERE out_trade_no = #{outTradeNo}")
    Payment selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    @Insert("INSERT INTO payment(order_id, out_trade_no, amount, channel, status, create_time) " +
            "VALUES(#{orderId}, #{outTradeNo}, #{amount}, #{channel}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Payment payment);

    /** 置为已支付 + 记录支付时间。 */
    @Update("UPDATE payment SET status = '已支付', pay_time = NOW() WHERE out_trade_no = #{outTradeNo}")
    int markPaid(@Param("outTradeNo") String outTradeNo);
}
