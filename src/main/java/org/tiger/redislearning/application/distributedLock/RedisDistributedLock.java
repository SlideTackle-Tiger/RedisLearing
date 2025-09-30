package org.tiger.redislearning.application.distributedLock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tiger.redislearning.util.RedisLockUtils;
import org.tiger.redislearning.util.ResultUtil;

import java.util.Random;

/**
 * @ClassName RedisDistributedLock
 * @Description 分布式锁业务示例控制器，用于演示如何使用Redis分布式锁解决高并发场景下的库存超卖问题
 * 前提条件：需要配置自定义的Redis序列化器和分布式锁工具类(RedisLockUtils)
 * @Author tiger
 * @Date 2025/9/20 16:34
 */
@Slf4j  // 日志注解，自动生成log对象用于日志输出
// 标识为REST风格控制器，并指定bean名称为"distributedLockDemo"以区分其他控制器
@RestController("distributedLockDemo")
@RequestMapping("distributedLockDemo")
public class RedisDistributedLock {

    /**
     * 注入RedisTemplate，用于操作Redis数据库中的库存数据
     */
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 注入分布式锁工具类，用于获取和释放分布式锁
     */
    @Autowired
    private RedisLockUtils redisLockUtils;

    /**
     * 库存扣减接口，演示如何使用分布式锁解决超卖问题
     * 处理高并发下的库存扣减请求，通过分布式锁保证库存操作的原子性
     *
     * @return ResultUtil<String> 统一响应结果，包含操作状态和库存信息
     */
    @RequestMapping("deduct-stock")
    public ResultUtil<String> deductStock(){
        // 商品ID，标识需要扣减库存的商品
        String productId = "product001";
        log.info("------开始扣减库存------");

        // 分布式锁的键名，使用商品ID作为唯一标识，确保同一商品的库存操作互斥
        String key = productId;

        // 锁的值，由商品ID+当前线程ID组成，确保唯一性
        // 用于标识锁的持有者，防止不同线程或进程误释放对方的锁
        String requestId = productId + Thread.currentThread().getId();

        try{
            // 尝试获取分布式锁，过期时间10秒
            // 如果获取锁失败，返回错误信息
            boolean locked = redisLockUtils.lock(key, requestId, 10);
            if(!locked){
                return ResultUtil.fail("抢占锁失败，请稍后重试");
            }

            // 以下为核心业务逻辑，在分布式锁保护下执行

            // 1. 从Redis中获取当前库存数量
            // 注意：实际生产环境应增加非空判断和类型转换异常处理
            int stock = Integer.parseInt(redisTemplate.opsForValue().get("product001-stock").toString());

            // 2. 扣减库存（库存减1）
            int currentStock = stock - 1;

            // 3. 将扣减后的库存数量更新回Redis
            redisTemplate.opsForValue().set("product001-stock", currentStock);

            // 模拟业务处理耗时（随机0-2秒）
            // 用于模拟实际业务中可能的复杂处理过程
            try{
                Random random = new Random();
                Thread.sleep(random.nextInt(3) * 1000);
            } catch (InterruptedException e){
                e.printStackTrace();
            }

            log.info("------扣减库存成功------:currentStock = {}", currentStock);
            return ResultUtil.success("扣减库存成功，当前库存: " + currentStock);
        } finally {
            // 无论业务处理成功与否，最终都要释放锁
            // 确保锁资源不会因异常等情况导致长期占用
            redisLockUtils.unlock(key, requestId);
        }
    }
}