package com.tzfun.order.feign;

import com.tzfun.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * car-service 降级：下游异常/熔断时走兜底。
 * occupy 是强依赖，快速失败返回「服务不可用」；release 是最终一致，仅记录日志（后续对账兜底）。
 */
@Component
public class CarServiceClientFallbackFactory implements FallbackFactory<CarServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(CarServiceClientFallbackFactory.class);

    @Override
    public CarServiceClient create(Throwable cause) {
        return new CarServiceClient() {
            @Override
            public Result<Boolean> occupy(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
                log.error("car-service 占库存降级：storeId={} carTypeId={} start={}", storeId, carTypeId, start, cause);
                return Result.error("500", "库存服务不可用，请稍后再试");
            }

            @Override
            public Result<Void> release(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
                log.error("car-service 释放库存降级(需对账兜底)：storeId={} carTypeId={} start={}", storeId, carTypeId, start, cause);
                return Result.error("500", "库存服务不可用");
            }
        };
    }
}
