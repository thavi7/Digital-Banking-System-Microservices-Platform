package com.banking.accountservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false,unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolderName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private AccountStatus accountStatus;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal balance;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal dailyTransactionLimit;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @CreationTimestamp
    private LocalDateTime updatedAt;
}
