package org.tiger.redislearing.base;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.tiger.redislearing.base.delayQueue.DelayTaskConsumer;
import org.tiger.redislearing.base.pojo.Cart;
import org.tiger.redislearing.pojo.User;
import org.tiger.redislearing.util.RedisUtil;
import org.tiger.redislearing.util.ResultUtil;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @ClassName BaseDataType
 * @Description redis基础数据类型测试，由于我们封装了redisUtil工具类直接调用即可
 * @Author tiger
 * @Date 2025/9/20 15:30
 */

@RestController
@RequestMapping("/baseDataType")
public class BaseDataType {
    // 注入redisUtil工具类
    @Autowired
    private RedisUtil redisUtil ;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * String类型操作与获取
     * */
    @GetMapping("/String")
    public ResultUtil stringdemo(){
        // 存储一个名为time的key，当前时间为value的String类型
        redisUtil.set("time", LocalDateTime.now() + "");
        // 获取redis数据库中一个名为time的key的value
        String res =(String) redisUtil.get("time");
        return ResultUtil.success(res);
    }
    /**
     * 存储一个String类型的数据要求前段发送过来（路径）
     * */
    @PostMapping("String/{key}/{value}")
    public ResultUtil stringPost(@PathVariable String key, @PathVariable String value){
        if(key.isEmpty() || value.isEmpty()){
            return ResultUtil.fail("key或value不能为空");
        }
        redisTemplate.opsForValue().set(key,value);
        String res = (String) redisTemplate.opsForValue().get(key);
        if(res != null && res.equals(value)){
            return ResultUtil.success("存储成功");
        }else{
            return ResultUtil.fail("存储失败");
        }
    }

    /**
     * String类型存储并解析序列化对象，前提是要自定义template模版这里已经定义在config包下
     */
    @GetMapping("object")
    public ResultUtil objectdemo(){
        User user = new User();
        user.setName("张三");
        user.setId(1);
        user.setDes("这是一个描述");
        // 模版会自动自动序列化，这里需要调整一下工具类的参数
        redisUtil.set(user.getName(), user);
        // 反序列化
        User res = (User)redisUtil.get(user.getName());
        return ResultUtil.success(res);
    }
    /**
     * list类型存储与获取，还是要依靠序列化
     */
    @GetMapping("list")
    public ResultUtil listdemo(){
        List<User> userList = new ArrayList<>();
        userList.add(new User("张三",1,"描述"));
        userList.add(new User("李四",1,"描述"));
        userList.add(new User("王二麻",1,"描述"));
        String listKey = "users:list";
        for(User user: userList){
            redisTemplate.opsForList().rightPush(listKey,user);
        }
        // 获取
        List<Object> storedList = redisTemplate.opsForList().range(listKey,0,-1);

        // 反序列化
        List<User> res = new ArrayList<>();
        for(Object obj : storedList){
            User user = (User) obj;
            res.add(user);
        }
        return ResultUtil.success(res);
    }

    /**
     * set类型存储与基本方法调用，以用户收藏场景为例
     *
     * */
    // 添加用户收藏接口
    private static final String FAVORITE_KEY_PREFIX = "user:%s:favorite";
    @GetMapping("Set/{userId}/{productId}")
    public ResultUtil setDemo(@PathVariable String userId, @PathVariable String productId){
        // 参数判空
        if(userId.isEmpty() || productId.isEmpty()){
            return ResultUtil.fail("参数不能为空");
        }

        String key = String.format(FAVORITE_KEY_PREFIX,userId);
        // 判断收藏中是否已经存在了这个商品
        Boolean isExist = redisTemplate.opsForSet().isMember(key,productId);
        if(isExist){return ResultUtil.fail("商品已经收藏了");}

        // 存储到Set,获取了add方法返回值，返回值表示本次添加操作成功写入集合中的数量
        Long cont = redisTemplate.opsForSet().add(key,productId);
        if(cont != null && cont > 0){
            return ResultUtil.success("存储成功");
        }else{
            return ResultUtil.fail("存储失败");
        }
    }

    // 快速判断两个用户共同收藏接口（交集计算）
    @GetMapping("CommonFavorite")
    public ResultUtil getCommonFavorite(@RequestParam List<Long> userIds){
        if(userIds.size() < 2){
            return ResultUtil.fail("获取失败，用户id至少大于2");
        }
        // 构造key List
        List<String> keys = new ArrayList<>(userIds.size());
        for(Long userId : userIds){
            keys.add(String.format(FAVORITE_KEY_PREFIX, userId));
        }

        // redis中查询并返回set集合结果
        Set<String> res = redisTemplate.opsForSet().intersect(keys);
        return ResultUtil.success(res);
    }
    /**
     * 并集、差集的实现几乎与交集一致，只是调用方法不同
     * redisTemplate.opsForSet().union(keys)  // 并集
     * redisTemplate.opsForSet().difference(keys) // 差集
     * */

    /**
     * Hash散列实现购物车
     * */
    // 添加商品到购物车
    private static final String CART_KEY_PREFIX = "user:%s:cart";
    @PostMapping("Hash/add")
    public ResultUtil hashDemo(@RequestParam Long userId,@RequestParam String productId,@RequestParam String productName,@RequestParam String productPrice){
        if (userId == null || productId == null || productName == null || productPrice == null){
            return ResultUtil.fail("参数不能为空");
        }
        String key = String.format(CART_KEY_PREFIX,userId);
        redisTemplate.opsForHash().put(key,"productId:",productId);
        redisTemplate.opsForHash().put(key,"productName:",productName);
        redisTemplate.opsForHash().put(key,"productPrice:",productPrice);
        return ResultUtil.success("添加成功");
    }
    // 修改购物车中某个商品,只需要调用put方法传入之前的key 和feild即可

    /**
     * Zset有序集合相关应用场景
     * 1. 用户排行榜
     * */
    private static final String USER_KEY_PREFIX = "user:rank:score";
    // 存储(新用户) 更新（旧用户）分数
    @PostMapping("Zset/add")
    public ResultUtil zsetDemo(@RequestParam Long userId, @RequestParam String score){
        if(userId == null || score == null){
            return ResultUtil.fail("参数不能为空");
        }
        redisTemplate.opsForZSet().add(USER_KEY_PREFIX, userId, Double.parseDouble(score));
        return ResultUtil.success();
    }

    // 获取排行榜
    @GetMapping("Zset/getRank")
    public ResultUtil getRank(@RequestParam Long num){
        // 获取前mun个排行榜用户
        Set<Object> res = redisTemplate.opsForZSet().reverseRange(USER_KEY_PREFIX,0,num -1);
        return ResultUtil.success(res);
    }

    /**
     * 9.25 领跑
     *
     * Zset实现延迟队列，核心是使用score作为时间戳，通过轮训的方式删除过期任务
     * */
    @Autowired
    private DelayTaskConsumer delayTaskConsumer;
    // 添加一个延迟队列
    @PostMapping("Zset/delayQueue/add")
    public ResultUtil adDelayQueue(@RequestParam String orderId){
        delayTaskConsumer.addDelayTask("order_cancel",orderId.toString(), 30 * 60);
        return ResultUtil.success("添加到订单延迟队列成功");
    }







}

