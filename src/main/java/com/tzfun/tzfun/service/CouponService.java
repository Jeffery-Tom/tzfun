package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.entity.CouponTemplate;
import com.tzfun.tzfun.entity.CouponType;
import com.tzfun.tzfun.entity.UserCoupon;
import com.tzfun.tzfun.mapper.CouponTemplateMapper;
import com.tzfun.tzfun.mapper.UserCouponMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券：领券（防超发/防重复）+ 抵扣计算 + 用券/退券。
 */
@Service
public class CouponService {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private UserCouponMapper userCouponMapper;

    /**
     * 领券：先插 user_coupon（唯一索引拦重复领），再条件更新 claimed_count。
     * 事务保证两步原子；并发下「唯一索引 + 条件更新」双重防超发。
     * 注意：唯一索引仅当 per_user_limit=1 时天然限领；若 >1 需额外 COUNT 校验（MVP 按 1 处理）。
     */
    @Transactional
    public UserCoupon claim(Long templateId, Long userId) {
        CouponTemplate t = couponTemplateMapper.selectById(templateId);
        if (t == null || t.getStatus() == null || t.getStatus() != 1) {
            throw new CustomException("404", "券模板不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(t.getClaimStart()) || now.isAfter(t.getClaimEnd())) {
            throw new CustomException("400", "不在领取时间内");
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setTemplateId(templateId);
        uc.setStatus(0);
        uc.setExpireTime(t.getUseEnd());
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException e) {
            throw new CustomException("400", "已领取过该券");
        }
        if (couponTemplateMapper.incrementClaimed(templateId) != 1) {
            userCouponMapper.deleteById(uc.getId());
            throw new CustomException("400", "该券已被领完");
        }
        return uc;
    }

    /** 抵扣计算（供模块6 下单/支付用）：满减/立减=value；折扣=订单额×(1-折扣率) 封顶 max_discount；结果不超订单额。 */
    public BigDecimal calcDiscount(CouponTemplate t, BigDecimal orderAmount) {
        if (t == null || orderAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount;
        CouponType type = CouponType.valueOf(t.getType());
        if (type == CouponType.DISCOUNT) {
            discount = orderAmount.multiply(BigDecimal.ONE.subtract(t.getValue()));
            if (t.getMaxDiscount() != null && discount.compareTo(t.getMaxDiscount()) > 0) {
                discount = t.getMaxDiscount();
            }
        } else {
            discount = t.getValue(); // 满减 / 立减
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return discount;
    }

    /** 用券：支付成功时调，条件更新防复用。 */
    public void useCoupon(Long userCouponId, Long orderId) {
        if (userCouponMapper.useCoupon(userCouponId, orderId) != 1) {
            throw new CustomException("400", "券不可用");
        }
    }

    /**
     * 支付成功用券（封装）：先算折扣，再条件更新标记已用；返回实际抵扣金额供回写订单。
     * 券不存在/不可用（status!=0 已用等）抛 CustomException，由调用方 catch 做幂等忽略。
     */
    public BigDecimal applyCoupon(Long userCouponId, Long orderId, BigDecimal orderAmount) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new CustomException("400", "券不存在");
        }
        CouponTemplate t = couponTemplateMapper.selectById(uc.getTemplateId());
        BigDecimal discount = calcDiscount(t, orderAmount);
        useCoupon(userCouponId, orderId); // 内部 WHERE status=0 条件更新，行数≠1 抛"券不可用"
        return discount;
    }

    /** 退券：取消/超时退回（幂等，非已用状态静默忽略）。 */
    public void returnCoupon(Long userCouponId) {
        userCouponMapper.returnCoupon(userCouponId);
    }
}
