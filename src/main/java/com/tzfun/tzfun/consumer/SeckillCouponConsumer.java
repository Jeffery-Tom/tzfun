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
            return;//   回滚 Redis 预扣库存，直接 return，这条消息消费结束。
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setTemplateId(templateId);
        uc.setStatus(0);
        uc.setExpireTime(t.getUseEnd());
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException e) {
//          seckillService.rollbackStock(templateId);唯一索引已经说明前面成功一次了,再回滚会导致库存虚高,直接return
            return;
        }
        //  条件更新,数据库层面的兜底防超发;   防止 Redis 和 DB 数据不一致
        //update语句返回影响行数;   返回不是1，说明更新失败，比如已经领满了
        if (couponTemplateMapper.incrementClaimed(templateId) != 1) {
            userCouponMapper.deleteById(uc.getId()); // 删掉刚插入的用户券
            seckillService.rollbackStock(templateId); // 已领完，回滚预扣
        }
    }

    @PostConstruct
    public void init() throws Exception {
        super.start();
    }
}
