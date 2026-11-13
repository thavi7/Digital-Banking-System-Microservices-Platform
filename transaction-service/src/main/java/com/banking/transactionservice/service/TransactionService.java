package com.banking.transactionservice.service;

import com.banking.transactionservice.clint.AccountServiceClint;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * SAGA STEP - 1: Initiate transfer
 * Deducts from sender via feign
 * Saves transaction as PROCESSING.
 * Publish event to Kafka for fraud check
 * Returns.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClint accountServiceClint;
    private final KafkaTemplate<String , Object>kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;
    private final ModelMapper modelMapper;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    public TransactionResponse transfer(TransferRequest request){
        log.info("Saga Start - Transfer: {} -> {} amount: {}",
            request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount()
        );


        //SAGA Step 1 -->
        //detected money from sender if fraud detect then we will refund by saga
        accountServiceClint.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savetransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as processing: {}",savetransaction.getId());

        //SAGA Step 2 publish for fraud check-->
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savetransaction.getId(),
                savetransaction.getSenderAccountNumber(),
                savetransaction.getReceiverAccountNumber(),
                savetransaction.getAmount(),
                savetransaction.getDescription()
        );
        //publishing that event to kafka for fraud check
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savetransaction.getId(),event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {} ", savetransaction.getId());

        return modelMapper.map(savetransaction, TransactionResponse.class);

    }

    public TransactionResponse getTransaction(String transactionId){
        Transaction transaction = transactionRepository.
                findById(transactionId).orElseThrow(()->new RuntimeException
                        ("Transaction Not Found with transaction id "+transactionId));
            return modelMapper.map(transaction, TransactionResponse.class);
    }

    public List<TransactionResponse>getTransactionHistory(String accountNumber){
        List<Transaction> transactions = transactionRepository.findBysenderAccountNumberOrderByCreatedAtDesc(accountNumber);
       return transactions.stream().map((element) -> modelMapper.map(element, TransactionResponse.class)).toList();
    }

    //verify otp

    public TransactionResponse verifyOTP(String transactionId,String otp){
        log.info("otp verification for transaction: {}",transactionId);

        Transaction transaction = transactionRepository.findById(transactionId).
                orElseThrow(() -> new RuntimeException("transaction not found with id"+transactionId));

        String otpkey = "verification:otp"+transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpkey);

        if(storedOtp==null){
            log.warn("otp expired for transaction: {}",transactionId);
            compensateTransaction(transaction,"OTP expired - transaction cancelled and amount refunded");
            return modelMapper.map(transaction,TransactionResponse.class);
        }
        if(!storedOtp.equals(otp)){
            log.warn("Blocking account and refunding: {}",transactionId);
            redisTemplate.delete(otpkey);
            blockAccountAndCompensate(transaction,
                    "Wrong Otp entered - transaction cancelled,"+
                            "Account blocked for security");
            return modelMapper.map(transaction,TransactionResponse.class);
        }
        //otp verified
        log.info("Otp verified - completing transaction: {}",transactionId);
        redisTemplate.delete(otpkey);
        completeTransaction(transaction);
        return modelMapper.map(transaction,TransactionResponse.class);
    }


    private void compensateTransaction(Transaction transaction,String reason){
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());
        //credit in sender account
        accountServiceClint.creditBalance(transaction.getSenderAccountNumber(),
                transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);

        transaction.setFailureReason(reason+"SAGA COMPENSATION executed, amount refunded at"+ LocalDateTime.now());
        Transaction save = transactionRepository.save(transaction);

        // Publish refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());

    }

    private void blockAccountAndCompensate(Transaction transaction,String reason){
        log.warn("SAGA BLOCKING - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());
        //blocking sender account and publish fraud detected
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank",
                transaction.getSenderAccountNumber());

        // SAGA COMPENSATION - refund Sender
        compensateTransaction(transaction, reason);

    }

    private void completeTransaction(Transaction transaction){
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE - Transaction {} completed",
                transaction.getId());
    }

    public void processCleanResult(String transactionId){
        Transaction transaction = transactionRepository.findById(transactionId).
                orElseThrow(() -> new RuntimeException("transaction not found with id"+transactionId));
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }
        completeTransaction(transaction);
    }

}
