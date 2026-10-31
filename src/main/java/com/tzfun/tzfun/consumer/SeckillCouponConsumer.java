package com.tzfun.tzfun.consumer;

import com.tzfun.tzfun.common.mq.MqConsumerSupport;
import com.tzfun.tzfun.entity.CouponTemplate;
import com.tzfun.tzfun.entity.UserCoupon;
import com.tzfun.tzfun.mapper.CouponTemplateMapper;
import com.tzfun.tzfun.mapper.UserCouponMapper;
import com.tzfun.tzfun.service.SeckillService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 秒杀落库：异步插入 user_coupon（唯一索引兜底）+ claimed_count+1 条件更新。
 * 业务失败（重复/领完）回滚 Redis 预扣后吞掉，不重试；系统异常才抛走重试。
 */
@Component
public class SeckillCouponConsumer extends MqConsumerSupport {

    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private SeckillService seckillService;

    @Value("${rocketmq.coupon.seckill-topic}")
    private String seckillTopic;

    @Override
    protected String group() {
        return "tzfun-seckill-consumer";
    }

    @Override
    protected String topic() {
        return seckillTopic;
    }

    @Override
    protected void onMessage(String body) {
        String[] parts = body.split(":");
        Long userId = Long.valueOf(parts[0]);
        Long templateId = Long.valueOf(parts[1]);

        CouponTemplate t = couponTemplateMapper.selectById(templateId);
        if (t == null) {
            seckillService.rollbackStock(templateId); // 模板已删，回滚预扣
            return;
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setTemplateId(templateId);
        uc.setStatus(0);
        uc.setExpireTime(t.getUseEnd());
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException e) {
            seckillService.rollbackStock(templateId); // 重复秒杀，唯一索引兜底，回滚预扣
            return;
        }
        if (couponTemplateMapper.incrementClaimed(templateId) != 1) {
            userCouponMapper.deleteById(uc.getId());
            seckillService.rollbackStock(templateId); // 已领完，回滚预扣
        }
    }

    @PostConstruct
    public void init() throws Exception {
        super.start();
    }
}
