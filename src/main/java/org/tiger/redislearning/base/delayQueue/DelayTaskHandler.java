package org.tiger.redislearning.base.delayQueue;

/**
 * @ClassName DelayTaskHandler
 * @Description 延迟任务处理器接口
 * 设计说明：
 * 1. 定义了延迟任务处理的标准接口，所有具体任务类型的处理器需实现此接口
 * 2. 采用策略模式，通过任务类型与处理器的绑定，实现不同类型任务的差异化处理
 * 3. 配合DelayTaskConsumer使用，由消费者自动分发任务到对应的处理器
 * @Author tiger
 * @Date 2025/9/22 12:28
 */
public interface DelayTaskHandler {

    /**
     * 处理延迟任务的核心方法
     * 当任务到达执行时间时，由延迟队列消费者调用此方法
     * @param taskID 任务唯一标识，通常为业务ID（如订单ID、消息ID等）
     */
    void handle(String taskID);

    /**
     * 获取当前处理器支持的任务类型
     * 用于与延迟队列中存储的任务类型进行匹配，实现任务的正确分发
     * @return 任务类型字符串（如"order_cancel"表示订单取消任务）
     */
    String getTaskType();
}