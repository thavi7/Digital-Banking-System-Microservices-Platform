package com.banking.transactionservice.service;

import com.banking.transactionservice.clint.AccountServiceClint;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final RedisTemplate<String,String> redisTemplate;
    private final KafkaTemplate<String,Object>kafkaTemplate;
    private final ModelMapper modelMapper;

    private static final Long otpexpirymin = 2L;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
            @Payload Map<String, Object> payload) {

        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            String amount = payload.get("amount").toString();


            log.info("Verification required - transaction: {} reason: {}",
                    transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new RuntimeException(
                            "Transaction not found " + transactionId
                    ));

            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction {} not PROCESSING - skipping", transactionId);
                return;
            }
            //generate 6 digit otp
            String otp = String.format("%06d",(int)(Math.random()*900000)+100000);

            //otp stores in redis and expiry in 1 min

            String otpkey = "verification:otp"+transactionId;
            redisTemplate.opsForValue().set(otpkey,otp,otpexpirymin, TimeUnit.MINUTES);
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} expires: in {} min"+transactionId,otpexpirymin);

            //notify user
            Map<String,Object>otpEvent= new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount",amount);

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);
        } catch (Exception e) {
        log.error("Error handeling verification requried: {}",e.getMessage());
        }
    }
    @KafkaListener(topics="fraud.check.clean")
    private void consumeFraudCheckCleanResult(
            @Payload Map<String,Object>payload
    ){
        try{
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);
        } catch (Exception e) {
            log.error("Error processing fraud check result"+e.getMessage());
        }
    }

}
