# 人人点评
## 技术栈：SpringBoot、Mybatis、Redis、MySQL

Nginx负责作为静态资源服务器

## 项目启动流程

1. 服务端修改相应的配置文件
application.yaml中数据库，redis的host地址，密码等
2. Nginx作为静态资源服务器，直接启动nginx即可

`本项目侧重点是Redis的使用场景`

### 1.session共享问题
1. 传统登录session方案适用于单体应用，如果多个服务的话，session复制太耗费内存，同时也有一定的延迟问题
2. 采用Redis解决
```java
public Result login(LoginFormDTO loginForm, HttpSession session) {
    // 1.校验手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 1.1 不一致返回错误信息
        String phoneCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_PRE + phone);
        if (phoneCode == null || !phoneCode.equals(code)) {
            return Result.fail("验证码失效");
        }
        // 2.根据手机号查询用户是否存在 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        // 2.1 不存在，新建用户到数据库，接着保存用户到session
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 3. 保存用户信息到redis中
        // 3.1 生成随机token，作为user存入redis的key
        String token = UUID.randomUUID().toString(true);
        // 3.2 将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        // 确保每个字段都是字符串
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 3.3 保存到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN_PRE + token,userMap);
        // 3.4 设置有效期
        stringRedisTemplate.expire(LOGIN_TOKEN_PRE + token,LOGIN_TOKEN_TTL,TimeUnit.MINUTES);
        // 4. 返回token给客户端
        return Result.ok(token);
}
```
3、把token存入前端storage,每次发送请求都将token放入auth请求头进行发送，后端利用拦截器从请求头获取token，再从redis Get进行判断
```java
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.从请求头中获取token'
        String token = request.getHeader("authorization");
        if (StringUtils.isEmpty(token)) {
            // token不存在，拦截 401 未授权
            response.setStatus(401);
            return false;
        }
        // 2.基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_TOKEN_PRE + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            // 不存在，拦截 401 未授权
            response.setStatus(401);
            return false;
        }
        // 4.将查询都的map转为dto
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        // 5.刷新token有效期
        stringRedisTemplate.expire(LOGIN_TOKEN_PRE + token,LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        // 6.保存用户到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7.放行
        return true;
    }
```

### 2.对象缓存
1. 通过设置空值的方式解决缓存穿透
```java
public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 2.1 命中，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 不为null就是 ""
            return null;
        }
        // 2.2 未命中，根据id从数据库查询
        R r = dbFallback.apply(id);
        // 3. 判断商铺是否存在
        if (r == null) {
            // 将空值写入有效期，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4. 写入redis
       this.set(key,r,time,unit);
        return  r;
    } // end of queryWithPassThrough

```
2. 缓存雪崩只需要对redis中的key设置一个随机的过期时间，防止某个时间段大量key同时过期
3. 采用了互斥锁（setnx）、逻辑过期的方式解决缓存击穿问题
```java
public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                   Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 2.1 命中，返回数据
            return JSONUtil.toBean(json,type);
        }
        // 判断命中的是否是空值 : ""
        if (json != null) {
            // 不为null就是 ""
            return null;
        }
        // 3.未命中，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            if (!tryLock(lockKey)) {
                // 3.1 获取锁失败，休眠
                Thread.sleep(50);
                // 3.2 重试
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            // 3.3 获取锁,根据id从数据库查询
            r = dbFallback.apply(id);
            if (r == null) {
                // 数据库也不存在，防止缓存穿透，将空值写入redis
                this.set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            // 3.4 写入缓存
            this.set(key,JSONUtil.toJsonStr(r),time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 4.释放互斥锁
            unlock(lockKey);
        }
        // 5.返回
        return r;
    } // end of queryWithMutex

/**
     * 方法：根据指定的key查询缓存，并反序列化为指定类型，利用逻辑过期的方式解决缓存击穿问题
     * @param keyPrefix key 前缀
     * @param id id
     * @param type 反序列化对象类型
     * @param dbFallback 函数式调用
     * @param time 时间
     * @param unit 单位
     * @param <R> 函数式调用返回值
     * @param <ID> 函数式调用参数
     * @return R
     */
    public <R,ID> R queryWithLogic(String keyPrefix, ID id, Class<R> type,
                                   Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isBlank(json)) {
            // 2.1 未命中，直接返回null
            return null;
        }
        // 3.命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1 未过期，返回商铺数据
            return r;
        }
        // 3.2 已过期，缓存重建
        // 4.缓存重建
        // 4.1 获取互斥锁，判断获取互斥锁是否成功
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 4.2 成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                try {
                    R rFromDB = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,rFromDB,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 4.3 获取锁失败，返回商铺信息
        // 5.获取锁成功与否都要返回
        return r;
    }

```
5. 封装尝试获得锁、释放锁、缓存穿透、缓存击穿方法，充分利用了函数式编程以及泛型机制
```java
   /**
     * 尝试获得锁
     * @param key key
     * @return true：获得 false ：没有获得
     */
    private  boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
```



### 3.秒杀-一人一单
```xml
1、全局唯一ID:采用Redis的自增策略 + 时间戳
2、乐观锁（CAS）解决超卖问题，悲观锁（syn）解决一人一单问题
```
### 4.分布式锁
上面一人一单在单体环境下可以控制线程安全，但是在分布式环境下，一个Tomcat一个JVM，不能保证线程安全，所以需要采用分布式锁

基于Redis的分布式锁实现思路：
```java
// * 一、利用set nx ex获取锁，并设置过期时间，保存线程标示  
Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId , timeoutSec, TimeUnit.SECONDS); 
// * 二、释放锁时先判断线程标示是否与自己一致，一致则删除锁
String threadId = ID_PREFIX + Thread.currentThread().getId();
String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
// 判断当前线程标识是否与锁中的标识一致
if (threadId.equals(lockId)) {
   // 一致，删除
    stringRedisTemplate.delete(KEY_PREFIX + name);
}
// * 三、第二步中拿锁比锁不具备原子性，需采用lua脚本确保redis多指令的原子性
```
```lua
-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
  -- 一致，则删除锁
  return redis.call('DEL', KEYS[1])
end
-- 不一致，则直接返回
return 0
```

```xml-dtd
Redis分布式锁总结：
1、采用Redis实现分布式锁（setnx）ex 设置过期时间防止死锁
2、Redis分布式锁误删问题（当前线程标识与Redis分布式锁中value比较）
3、lua脚本解决分布式锁误删问题解决方案中出现的原子性问题
```
