package org.tiger.redislearning.base.delayQueue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName DelayTaskConsumer
 * @Description 基于Redis ZSet实现的延迟队列消费者组件
 * 功能说明：
 * 1. 利用Redis的ZSet数据结构存储延迟任务，以任务执行时间作为score
 * 2. 定期扫描并处理到期的延迟任务
 * 3. 通过任务类型与处理器的映射关系，实现不同类型任务的差异化处理
 * 4. 支持优雅启动和关闭，确保任务处理的完整性
 * @Author tiger
 * @Date 2025/9/22 12:12
 */
@Component
public class DelayTaskConsumer {

    /**
     * Redis操作模板，用于操作Redis的ZSet等数据结构
     */
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 所有延迟任务处理器的集合，通过Spring自动注入
     * 每个处理器对应一种任务类型的处理逻辑
     */
    @Autowired
    private List<DelayTaskHandler> taskHandlers;

    /**
     * 任务类型与处理器的映射表
     * 键：任务类型字符串
     * 值：对应的任务处理器实例
     */
    private Map<String, DelayTaskHandler> handlerMap = new HashMap<>();

    /**
     * 任务执行线程池，用于并行处理到期的延迟任务
     * 固定线程数为5，可根据实际需求调整
     */
    private ExecutorService taskExecutor = Executors.newFixedThreadPool(5);

    /**
     * 消费者主线程，负责定期扫描Redis中的到期任务
     */
    private Thread consumerThread;

    /**
     * 服务运行状态标志，用于控制消费者线程的启动与停止
     * volatile修饰确保多线程间的可见性
     */
    private volatile boolean isRunning = false;

    /**
     * Redis中存储延迟任务的ZSet键前缀
     * 实际存储键格式：ZSET_KEY_PREFIX + 任务类型
     */
    private static final String ZSET_KEY_PREFIX = "task:delay";

    /**
     * 初始化方法，在Bean创建后自动执行
     * 主要完成：
     * 1. 构建任务类型与处理器的映射关系
     * 2. 启动消费者线程开始扫描任务
     */
    @PostConstruct
    public void init(){
        // 遍历所有任务处理器，构建类型-处理器映射表
        for (DelayTaskHandler handler: taskHandlers){
            handlerMap.put(handler.getTaskType(), handler);
        }

        // 启动消费者线程
        isRunning = true;
        // 创建消费者线程，指定线程名为"delay:task:consumer"便于日志追踪
        consumerThread = new Thread(this::consumeExpiredTasks,"delay:task:consumer");
        // 设置为守护线程，当主线程退出时自动销毁
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    /**
     * 消费到期延迟任务的核心方法
     * 循环逻辑：
     * 1. 遍历所有任务类型
     * 2. 从Redis ZSet中查询当前时间点已到期的任务
     * 3. 将到期任务提交到线程池异步处理
     * 4. 处理完成后从ZSet中移除任务
     * 5. 循环间隔1秒，降低Redis访问压力
     */
    private void consumeExpiredTasks(){
        // 循环运行，直到服务停止标志为false
        while(isRunning){
            try{
                // 遍历所有已注册的任务类型
                for(String taskType : handlerMap.keySet()){
                    // 构建当前任务类型对应的ZSet键名
                    String zsetKey = ZSET_KEY_PREFIX + taskType;
                    // 获取当前时间戳（毫秒），作为判断任务是否到期的依据
                    long currentTime = System.currentTimeMillis();

                    // 查询分数（执行时间）在0到当前时间之间的任务，即已到期任务
                    // 最后两个参数：0表示从第0条开始，100表示最多取100条，避免一次处理过多任务
                    Set<Object> expiredTasks = redisTemplate.opsForZSet().rangeByScore(zsetKey,0,currentTime,0,100);

                    // 处理到期的延迟任务
                    if(expiredTasks != null && !expiredTasks.isEmpty()){
                        // 遍历每个到期任务，提交到线程池处理
                        for(Object task : expiredTasks){
                            String taskId = task.toString();
                            // 获取当前任务类型对应的处理器
                            DelayTaskHandler handler = handlerMap.get(taskType);
                            // 提交到线程池异步执行
                            taskExecutor.submit(()->{
                                try {
                                    // 调用处理器的处理方法
                                    handler.handle(taskId);
                                    // 处理成功后，从ZSet中移除该任务，避免重复处理
                                    redisTemplate.opsForZSet().remove(zsetKey,task);
                                } catch (Exception e){
                                    // 处理异常时打印错误日志，不中断整个处理流程
                                    System.err.println("处理延迟任务失败："+ taskId + "，原因：" + e.getMessage());
                                }
                            });
                        }
                    }
                }
                // 每次扫描完成后休眠1秒，减少Redis访问频率
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e){
                // 捕获中断异常，恢复中断状态并退出循环
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e){
                // 捕获其他异常，打印错误日志后休眠5秒再重试
                System.err.println("处理延迟任务失败："+ e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(5);
                }catch (InterruptedException e1){
                    break;
                }
            }
        }
    }

    /**
     * 添加延迟任务到Redis ZSet中
     * @param taskType 任务类型，需与已注册的处理器类型匹配
     * @param taskId 任务唯一标识
     * @param delayTime 延迟时间，单位：秒
     * @throws IllegalArgumentException 当任务类型不存在时抛出
     */
    public void addDelayTask(String taskType,String taskId,long delayTime){
        // 校验任务类型是否存在对应的处理器
        if(!handlerMap.containsKey(taskType)){
            throw new IllegalArgumentException("未知任务类型：" + taskType);
        }
        // 构建ZSet键名
        String zsetKey = ZSET_KEY_PREFIX + taskType;
        // 计算任务的执行时间戳（当前时间+延迟时间）
        long executeTime = System.currentTimeMillis() + delayTime * 1000;
        // 将任务添加到ZSet，score为执行时间戳
        redisTemplate.opsForZSet().add(zsetKey, taskId,executeTime);
        // 设置ZSet的过期时间，为延迟时间+1小时，避免Redis中残留永久键
        redisTemplate.expire(zsetKey, delayTime + 3600, TimeUnit.SECONDS);
    }

    /**
     * 销毁方法，在Bean销毁前自动执行
     * 主要完成：
     * 1. 停止消费者线程
     * 2. 优雅关闭线程池，确保正在处理的任务完成
     */
    @PreDestroy
    public void destroy(){
        // 设置运行状态为false，终止消费者线程循环
        isRunning = false;
        // 中断消费者线程（如果存在）
        if(consumerThread != null){
            consumerThread.interrupt();
        }
        // 关闭任务执行线程池
        taskExecutor.shutdown();
        try{
            // 等待线程池中的任务在5秒内完成
            if(!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)){
                // 如果超时，强制关闭线程池
                taskExecutor.shutdownNow();
            }
        }catch (InterruptedException e){
            // 捕获中断异常，强制关闭线程池
            taskExecutor.shutdownNow();
        }
    }
}