# 人人点评
### 技术栈：SpringBoot、Mybatis、Redis、MySQL

Nginx负责作为静态资源服务器

### 项目启动流程

1、服务端修改相应的配置文件
application.yaml中数据库，redis的host地址，密码等
2、Nginx作为静态资源服务器，直接启动nginx即可

本项目侧重点是Redis的使用场景

### 1.对象缓存
1、主要解决了缓存穿透、缓存击穿
2、采用了互斥锁（setnx）、逻辑过期的方式解决缓存击穿问题
