package org.tiger.redislearing.util;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName RedisLockUtils
 * @Description Redis实现的分布式锁工具类，用于在分布式系统中实现资源的互斥访问
 * 提供了加锁、解锁以及自动续期功能，确保分布式环境下的并发安全性
 * @Author tiger
 * @Date 2025/9/21 10:25
 */
@Slf4j  // 日志注解，自动生成log对象
@Component  // 标记为Spring组件，使其可以被自动扫描和注入
@Data  // Lombok注解，自动生成getter、setter等方法
public class RedisLockUtils {

    // 注入RedisTemplate，用于操作Redis
    @Autowired
    private RedisTemplate redisTemplate;

    // 存储当前持有的锁信息，使用ConcurrentHashMap保证线程安全
    // key: 锁的键名 value: 锁的详细信息对象
    private static Map<String, LockInfo> lockInfoMap = new ConcurrentHashMap<>();

    // 操作成功的返回值标识
    private static final Long SUCCESS = 1L;

    /**
     * @ClassName LockInfo
     * @Description 锁信息内部类，用于存储锁的详细信息
     */
    @Data
    public static class LockInfo{
        private String key;  // 锁的键名
        private String value;  // 锁的值，通常使用UUID等唯一标识
        private int expireTime;  // 锁的过期时间（秒）
        private long renewTime;  // 上次续期时间（毫秒时间戳）
        private long renewalInterval;  // 续期间隔（毫秒）

        /**
         * 创建锁信息对象的工厂方法
         * @param key 锁的键名
         * @param value 锁的值
         * @param expireTime 锁的过期时间（秒）
         * @return 构建好的LockInfo对象
         */
        public static LockInfo getLockInfo(String key, String value,int expireTime){
            LockInfo lockInfo = new LockInfo();
            lockInfo.setKey(key);
            lockInfo.setValue(value);
            lockInfo.setExpireTime(expireTime);
            lockInfo.setRenewTime(System.currentTimeMillis());  // 初始续期时间为当前时间
            // 续期间隔设置为过期时间的2/3，确保在锁过期前完成续期
            lockInfo.setRenewalInterval(expireTime * 1000L * 2 / 3);
            return lockInfo;
        }
    }

    /**
     * 使用Lua脚本更新Redis锁的过期时间（续期操作）
     * 确保检查锁的持有者和续期操作的原子性，防止误续期其他线程的锁
     * @param lockKey 锁的键名
     * @param value 锁的值（用于验证持有者身份）
     * @param exprieTime 续期后的过期时间（秒）
     * @return 续期是否成功 true:成功 false:失败
     */
    public boolean renewal(String lockKey, String value,int exprieTime){
        // Lua脚本：先检查锁是否存在且值匹配，匹配则更新过期时间，否则返回0
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else return 0 end";

        // 创建Redis脚本对象
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Boolean.class);  // 指定脚本返回值类型
        redisScript.setScriptText(luaScript);      // 设置脚本内容

        // 构建键列表
        List<String> keys = new ArrayList<>();
        keys.add(lockKey);

        // 执行Lua脚本，参数依次为：脚本对象、键列表、脚本参数
        Object result = redisTemplate.execute(redisScript, keys, value, exprieTime);
        log.info("更新redis锁过期时间结果：{}", result);

        return (boolean) result;
    }

    /**
     * 获取分布式锁
     * 使用Redis的setIfAbsent命令（原子操作）实现，确保只有一个线程能获取到锁
     * @param lockKey 锁的键名，标识需要锁定的资源
     * @param value 锁的值，用于标识锁的持有者，建议使用UUID等唯一值
     * @param expireTime 锁的过期时间（秒），防止死锁
     * @return 是否获取到锁 true:成功获取 false:获取失败
     */
    public boolean lock(String lockKey, String value, long expireTime){
        // setIfAbsent等价于Redis的SET NX命令，只有当键不存在时才设置值
        // 同时指定过期时间，避免死锁
        return redisTemplate.opsForValue().setIfAbsent(lockKey, value, expireTime, TimeUnit.SECONDS);
    }

    /**
     * 释放分布式锁
     * 先检查锁的持有者是否为当前线程，避免误释放其他线程的锁
     * @param key 锁的键名
     * @param value 锁的持有者标识（获取锁时使用的value）
     * @return 是否释放成功 true:成功 false:失败
     */
    public boolean unlock(String key, String value){
        // 获取当前锁的值
        Object currentValue = redisTemplate.opsForValue().get(key);
        boolean result = false;

        // 检查当前锁是否存在且值与当前线程持有的值一致
        if(StringUtils.isNotEmpty(String.valueOf(currentValue)) && currentValue.equals(value)){
            // 一致则删除锁（释放锁）
            result = redisTemplate.opsForValue().getOperations().delete(key);
            // 从本地缓存中移除锁信息
            lockInfoMap.remove(key);
        }
        return result;
    }

    /**
     * 定时检查并续期Redis锁
     * 采用定时任务+异步执行的方式，自动为即将过期但仍在使用的锁续期
     * @Scheduled(fixedRate = 5000L) 每5秒执行一次检查
     * @Async("redisExecutor") 使用指定的线程池异步执行
     */
    @Scheduled(fixedRate = 5000L)
    @Async("redisExecutor")
    public void renewal(){
        long now = System.currentTimeMillis();  // 当前时间戳

        // 遍历所有持有的锁信息
        for(Map.Entry<String, LockInfo> lockInfoEntry : lockInfoMap.entrySet()){
            LockInfo lockInfo = lockInfoEntry.getValue();

            // 检查是否需要续期：上次续期时间 + 续期间隔 < 当前时间
            if(lockInfo.getRenewTime() + lockInfo.getRenewalInterval() < now){
                // 执行续期操作
                renewal(lockInfo.getKey(), lockInfo.getValue(), lockInfo.getExpireTime());
                // 更新续期时间为当前时间
                lockInfo.setRenewTime(now);
                log.info("锁续期成功:{}", JSON.toJSONString(lockInfo));
            }
        }
    }

    /**
     * 创建用于Redis锁续期的专用线程池
     * 隔离续期任务与其他业务任务，避免互相影响
     * @return 配置好的线程池执行器
     */
    @Bean("redisExecutor")  // 定义为Spring Bean，名称为redisExecutor
    public Executor redisExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);  // 核心线程数：1
        executor.setMaxPoolSize(1);   // 最大线程数：1
        executor.setQueueCapacity(1); // 任务队列容量：1
        executor.setKeepAliveSeconds(60);  // 线程空闲时间：60秒
        executor.setThreadNamePrefix("redis-renewal-");  // 线程名前缀
        // 拒绝策略：丢弃最旧的任务，尝试提交新任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }
}