package com.banking.transactionservice.entity;
/*
transaction life cycle-->
pending -> processing -> completed -> credit to reciver (CLEAN)
                        -> pending verification & suspicious dedected
                            if user verify -> credit to reciver (CLEAN)
                            else flag transaction and block acc, (SAGA Refund)
                        -> failed
                        ->flagged


 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
