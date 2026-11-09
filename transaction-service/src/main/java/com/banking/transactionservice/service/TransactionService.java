package com.banking.transactionservice.service;

import com.banking.transactionservice.clint.AccountServiceClint;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final ModelMapper modelMapper;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    public TransactionResponse transfer(TransferRequest request){
        log.info("Saga Start - Transfer: {} -> {} amount: {}",
            request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount()
        );
        //SAGA Step 1 -->
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
                savetransaction.getSenderAccountNumber(),
                savetransaction.getAmount(),
                savetransaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savetransaction.getId(),event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {} ", savetransaction.getId());

        return modelMapper.map(transaction, TransactionResponse.class);

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


}
