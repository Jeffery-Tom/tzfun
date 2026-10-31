package com.tzfun.tzfun.controller;

import com.tzfun.tzfun.common.Result;
import com.tzfun.tzfun.dto.PayNotifyDTO;
import com.tzfun.tzfun.service.PaymentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（mock 网关）。
 */
@RestController
@RequestMapping("/pay")
public class PaymentController {

    @Resource
    private PaymentService paymentService;

    /** 创建支付单。 */
    @PostMapping("/create")
    public Result<String> create(@RequestParam Long orderId) {
        return Result.success(paymentService.createPay(orderId));
    }

    /** 网关异步回调（无 token，返回纯文本 ack）。 */
    @PostMapping("/notify")
    public String notify(@Valid @RequestBody PayNotifyDTO dto) {
        return paymentService.handleNotify(dto);
    }
}
