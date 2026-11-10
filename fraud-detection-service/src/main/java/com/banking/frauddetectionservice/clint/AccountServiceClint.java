package com.banking.frauddetectionservice.clint;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClint {

    @GetMapping("accounts/{accountNumber}/balance")
    public BigDecimal getBalance(
            @PathVariable String accountNumber);

}
