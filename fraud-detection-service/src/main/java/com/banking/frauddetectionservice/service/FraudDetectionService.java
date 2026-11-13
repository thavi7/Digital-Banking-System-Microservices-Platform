package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.clint.AccountServiceClint;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    private final AccountServiceClint accountServiceClint;
    private final RedisTemplate<String,String>redisTemplate;
    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";
    private final KafkaTemplate <String,Object>kafkaTemplate;

    @Value("${fraud.max-Transaction-Per-Minute}")
    private int maxTransactionPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private int suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxbalancepercentage;

    public void checkTransaction(Map<String,Object> payload){
        String transactionId= (String)payload.get("transactionId");
        String accountNumber = (String)payload.get("senderAccountNumber");
        BigDecimal ammount= new BigDecimal(payload.get("amount").toString());

        //fetch real balance from acc service
        BigDecimal balance = accountServiceClint.getBalance(accountNumber);

        log.info("Checking Transaction: {} account: {} ammount: {} balance: {}",
                transactionId,accountNumber,ammount,balance);

        FraudCheckResult result = performFraudCheck(accountNumber,ammount,balance);

        if(result.isFraude()){
            log.info("Suspicious activity detected in - account: {} "+"reason: {} - requesting OTP verification",
                    accountNumber,result.getReason());
            Map<String, Object>verificationEvent=new HashMap<>();
            verificationEvent.put("transactionId",transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount", ammount);
            verificationEvent.put("balance",balance);
            verificationEvent.put("reason",result.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);
        }else{
            //Transaction is clean
            log.info("Transaction is clean");
            Map<String,Object>transactionCleanEvent=new HashMap<>();
            transactionCleanEvent.put("transactionId",transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason",null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,transactionCleanEvent);
        }

    }



    private FraudCheckResult performFraudCheck(
            String accountNumber,
            BigDecimal amount,
            BigDecimal senderBalance
    ){
        // Pattern 1: Velocity Check
        if (isVelocityExceeded(accountNumber)) {
            return new FraudCheckResult(
                    true,
                    "Too many transactions in 60 seconds - Velocity limit exceeded");
        }

        // Pattern 2: Amount check
        if (isAmountSuspicious(accountNumber, amount)) {
            return new FraudCheckResult(
                    true,
                    "Unusual transaction amount - exceeds 3x your average");
        }

        // Pattern 3: Balance Check
        if (senderBalance.compareTo(BigDecimal.ZERO) > 0
                && isBalanceCheckFailed(senderBalance, amount)) {
            return new FraudCheckResult(
                    true,
                    "Transaction exceed 90% of account balance");
        }

        return new FraudCheckResult(false,null);

    }

    private boolean isVelocityExceeded(String accountNumber){
        String key = "fraud:velocity"+accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);
        if(count!=null && count==1){
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }
        log.info("Velocity Check - amount: {} count: {}/{}",accountNumber,count,maxTransactionPerMinute);
        return count != null && count > maxTransactionPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber,BigDecimal amount){
        String avgkey = "fraud:avg-amount"+accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgkey);

        if(avgStr==null){
            redisTemplate.opsForValue().set(avgkey,amount.toString());
            return false;
        }
        BigDecimal avgAmount = new BigDecimal(avgStr);
        BigDecimal threshhold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        BigDecimal newavg= avgAmount.add(amount).divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);
        redisTemplate.opsForValue().set(avgkey,newavg.toString());
        log.info("Amount Check - amount: {} threshold: {} suspicious: {}",
                accountNumber,threshhold,amount.compareTo(threshhold)>0);

        return amount.compareTo(threshhold)>0;
    }

    private boolean isBalanceCheckFailed(BigDecimal balance,BigDecimal amount){
        BigDecimal maxAllowed = balance.multiply(BigDecimal.valueOf(maxbalancepercentage));
        log.info("Balance Check - amount: {} maxAllowed: {} suspicious: {}",
                amount,maxAllowed,amount.compareTo(maxAllowed)>0);

        return amount.compareTo(maxAllowed)>0;
    }




}
