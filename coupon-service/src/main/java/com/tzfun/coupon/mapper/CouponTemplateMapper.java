package com.tzfun.coupon.mapper;

import com.tzfun.coupon.entity.CouponTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 优惠券模板 Mapper。
 */
public interface CouponTemplateMapper {

    @Select("SELECT * FROM coupon_template WHERE id = #{id}")
    CouponTemplate selectById(Long id);

    /** 可领列表：上架且在领取时间窗内。 */
    @Select("SELECT * FROM coupon_template WHERE status = 1 " +
            "AND claim_start <= NOW() AND claim_end >= NOW() ORDER BY id DESC")
    List<CouponTemplate> listAvailable();

    /** 已领取数 +1，条件更新防超发；行数=0 表示已领完。 */
    @Update("UPDATE coupon_template SET claimed_count = claimed_count + 1 " +
            "WHERE id = #{id} AND claimed_count < total_count")
    int incrementClaimed(@Param("id") Long id);

    /** 即将开始的秒杀券（预热用）：上架且 5 分钟内即将开始。 */
    @Select("SELECT * FROM coupon_template WHERE seckill = 1 AND status = 1 " +
            "AND claim_start > NOW() AND claim_start <= DATE_ADD(NOW(), INTERVAL 5 MINUTE)")
    List<CouponTemplate> listSeckillStartingSoon();

    /** 已结束的秒杀券（对账用）：结束超过 5 分钟，MQ 应已消费完。 */
    @Select("SELECT * FROM coupon_template WHERE seckill = 1 " +
            "AND claim_end < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    List<CouponTemplate> listSeckillEnded();
}
