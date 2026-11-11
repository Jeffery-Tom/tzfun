package com.tzfun.coupon.controller;

import com.tzfun.common.Result;
import com.tzfun.common.UserContext;
import com.tzfun.coupon.entity.CouponTemplate;
import com.tzfun.coupon.entity.UserCoupon;
import com.tzfun.coupon.mapper.CouponTemplateMapper;
import com.tzfun.coupon.mapper.UserCouponMapper;
import com.tzfun.coupon.service.CouponService;
import com.tzfun.coupon.service.SeckillService;
import com.tzfun.coupon.vo.UserCouponVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券接口：可领列表 / 领券 / 我的卡包。
 */
@RestController
@RequestMapping("/coupon")
public class CouponController {

    @Resource
    private CouponService couponService;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private SeckillService seckillService;

    /** 可领券列表。 */
    @GetMapping("/listTemplates")
    public Result<List<CouponTemplate>> listTemplates() {
        return Result.success(couponTemplateMapper.listAvailable());
    }

    /** 登录用户领券。 */
    @PostMapping("/claim/{templateId}")
    public Result<UserCoupon> claim(@PathVariable Long templateId) {
        return Result.success(couponService.claim(templateId, UserContext.getUserId()));
    }

    /** 秒杀领券（Redis Lua 预扣 + MQ 异步落库，与普通 claim 分开）。 */
    @PostMapping("/seckill/{templateId}")
    public Result<Void> seckill(@PathVariable Long templateId) {
        seckillService.seckill(templateId, UserContext.getUserId());
        return Result.success();
    }

    /** 我的卡包（status=0）。 */
    @GetMapping("/myCoupons")
    public Result<List<UserCouponVO>> myCoupons() {
        return Result.success(userCouponMapper.listMyCoupons(UserContext.getUserId()));
    }

    /** 管理员下架券模板：写 DB 后延迟双删缓存，下架立即生效。 */
    @PostMapping("/admin/offline/{templateId}")
    public Result<Void> offline(@PathVariable Long templateId) {
        UserContext.requireAdmin();
        couponService.offlineTemplate(templateId);
        return Result.success();
    }
}
