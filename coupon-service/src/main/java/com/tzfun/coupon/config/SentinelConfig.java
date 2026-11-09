package com.tzfun.coupon.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 流控规则：代码加载，秒杀入口 QPS 限流（无需控制台）。
 */
@Configuration
public class SentinelConfig {

    @Value("${sentinel.seckill-qps:300}")
    private double seckillQps;

    @PostConstruct
    public void initFlowRules() {
        FlowRule rule = new FlowRule("seckill");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(seckillQps);
        FlowRuleManager.loadRules(List.of(rule));
    }
}
