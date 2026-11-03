package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AccountResponse {
    private String id;
    private String accountNumber;
    private String accountHolderName;
    private String email;
    private String phone;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private BigDecimal dailyTransactionLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
