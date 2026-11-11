package com.tzfun.order.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 熔断规则：代码加载，car-service 异常比例熔断（无需控制台）。
 * 资源名为 Feign 整合后的标准格式：HTTP方法:http://服务名/路径。
 */
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initDegradeRules() {
        DegradeRule occupy = degradeRule("POST:http://car-service/inventory/occupy");
        DegradeRule release = degradeRule("POST:http://car-service/inventory/release");
        DegradeRuleManager.loadRules(List.of(occupy, release));
    }

    /** 异常比例熔断：5s 窗口内异常比例 > 50% 则熔断 10s。 */
    private DegradeRule degradeRule(String resource) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(0.5);
        rule.setMinRequestAmount(10);
        rule.setStatIntervalMs(5000);
        rule.setTimeWindow(10);
        return rule;
    }
}
