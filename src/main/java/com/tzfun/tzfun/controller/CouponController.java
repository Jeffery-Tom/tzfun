package com.tzfun.tzfun.controller;

import com.tzfun.tzfun.common.Result;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.entity.CouponTemplate;
import com.tzfun.tzfun.entity.UserCoupon;
import com.tzfun.tzfun.mapper.CouponTemplateMapper;
import com.tzfun.tzfun.mapper.UserCouponMapper;
import com.tzfun.tzfun.service.CouponService;
import com.tzfun.tzfun.vo.UserCouponVO;
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

    /** 我的卡包（status=0）。 */
    @GetMapping("/myCoupons")
    public Result<List<UserCouponVO>> myCoupons() {
        return Result.success(userCouponMapper.listMyCoupons(UserContext.getUserId()));
    }
}
