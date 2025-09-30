# 写在前面

在高并发，高响应的场景下Redis的使用必不可少，这里我们提供了一个完整的Spring Boot项目，大家可以根据测试demo来对Redis进行学习，同时我们也准备了思维导图意在为大家打好理论基础。学习方式如下：先学习思维导图中的理论→然后根据思维导图的学习模块来找到对应的代码demo进行学习。

# 第一次如何运行项目？

首先需要安装好idea和maven

接着clone代码，下载好项目

然后右键pom.xml文件，加载到Maven并刷新依赖，下载项目必要的依赖项。

接着就可以运行Spring Boot项目入口，项目正常启动后，可以对代码进行运行测试。

# 项目结构

- src -- 项目主目录

    - main/java/org.tiger.redislearning --项目主包

        - application.distributedlock --Redis 实现分布式锁

        - base

            - delayQueue --Redis 实现延迟队列

            - pojo --测试实体类

            - BaseDataType --基础数据类型学习Controller

        - config --Redis 配置这里配置了序列化方式

        - pojo --实体类

        - util

            - RedisLockUtils --分布式锁工具类

            - RedisUtil -- Redis使用模版类

            - ResultUtil --封装前端结果类

- pom.xml --Maven依赖

- readme.md --项目介绍



