package org.tiger.redislearing.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tiger.redislearing.pojo.User;
import org.tiger.redislearing.util.RedisUtil;
import org.tiger.redislearing.util.ResultUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
     *
     *
     * */



}

