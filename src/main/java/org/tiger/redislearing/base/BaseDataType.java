package org.tiger.redislearing.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tiger.redislearing.util.RedisUtil;
import org.tiger.redislearing.util.ResultUtil;

import java.time.LocalDateTime;

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

    /**
     * String类型操作与获取
     * */
    @GetMapping("/String")
    public ResultUtil stringdemo(){
        // 存储一个名为time的key，当前时间为value的String类型
        redisUtil.set("time", LocalDateTime.now() + "");
        String res =(String) redisUtil.get("time");
        return ResultUtil.success(res);
    }


}

