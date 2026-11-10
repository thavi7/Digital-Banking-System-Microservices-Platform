package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    //create an account
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }

    //get account by account number
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    //get balance of an account
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    //block an account
    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account Blocked Successfully");
    }

    //deduct money from an account by acc number
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {

        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance deducted Successfully");
    }

    //add money to account by account number
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {

        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("BALANCE CREDITED SUCCESSFULLY");
    }

}
