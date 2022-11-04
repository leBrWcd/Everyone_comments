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
![image](https://user-images.githubusercontent.com/83166781/199402621-bccb8ded-6829-485b-b096-8fef88028440.png)


2. 采用Redis解决
![image](https://user-images.githubusercontent.com/83166781/199403151-767a2acc-fe65-4a43-b82d-b0fd61f37d1d.png)
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

3. 把token存入前端storage,每次发送请求都将token放入auth请求头进行发送，后端利用拦截器从请求头获取token，再从redis Get进行判断
![image](https://user-images.githubusercontent.com/83166781/199403225-0bdaa6b6-8e62-484e-b22b-828142d9b747.png)

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
1. 解决缓存与数据库双写一致问题
![image](https://user-images.githubusercontent.com/83166781/199403405-ac370e20-b68c-4164-8827-4d276364549e.png)
```java
    /**
     * 先更新数据库，再删除缓存，采用事务控制
     * @param shop 商铺
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 3.返回
        return Result.ok();
    }
```

2. 通过设置空值的方式解决缓存穿透

![image](https://user-images.githubusercontent.com/83166781/199403529-df9899a2-5ef3-4828-9072-6ea59a9a0058.png)
![image](https://user-images.githubusercontent.com/83166781/199403584-45deaea9-6f74-4204-809b-0eb1d0bc6c2f.png)

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
3. 缓存雪崩只需要对redis中的key设置一个随机的过期时间，防止某个时间段大量key同时过期
4. 采用了互斥锁（setnx）、逻辑过期的方式解决缓存击穿问题
4.1 互斥锁方案
![image](https://user-images.githubusercontent.com/83166781/199403670-99cced0a-f339-4163-a355-328ce00afa74.png)
![image](https://user-images.githubusercontent.com/83166781/199403756-0038f788-8b3f-48ca-a2df-16e6ae87e503.png)
4.2 逻辑过期方案
![image](https://user-images.githubusercontent.com/83166781/199403699-3b4677aa-d38e-4639-acd6-49927dc4a684.png)
![image](https://user-images.githubusercontent.com/83166781/199403783-bd2afbc6-0b0b-4ffe-86cf-2581741f117f.png)

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
1. 全局唯一ID:采用Redis的自增策略 + 时间戳
![image](https://user-images.githubusercontent.com/83166781/199403925-472258b7-bec7-4ca7-99d6-64a3050306b6.png)
```java
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳 2022.1.1
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }
}
```

![image](https://user-images.githubusercontent.com/83166781/199404053-ce86bc72-45ef-495f-8618-e453e7eb6ce0.png)

2. 乐观锁（CAS）解决超卖问题
![image](https://user-images.githubusercontent.com/83166781/199404366-6da18573-0fb9-415d-97da-c5c5bafc49a6.png)
```java
boolean success = seckillVoucherService.update()
            .setSql("stock= stock -1")
            .eq("voucher_id", voucherId).update().gt("stock",0); //where id = ? and stock > 0
```

4. 悲观锁（syn）解决一人一单问题
![image](https://user-images.githubusercontent.com/83166781/199404503-5b7ca00c-288c-4f1a-b0ef-ceec16577e95.png)
```java
@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (LocalDateTime.now().isBefore(beginTime)) {
            // 2.1 未开始或者已经结束，返回错误信息
            return Result.fail("秒杀未开始!");
        }
        if (LocalDateTime.now().isAfter(endTime)) {
            return Result.fail("秒杀已结束!");
        }
        // 2.2 秒杀开始
        // 3.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            // 3.1 不充足，返回错误信息
            return Result.fail("库存不足!");
        }
        // 3.2 充足
        Long userId = UserHolder.getUser().getId();
        // 一个用户一把锁 intern() 返回字符串对象的规范表示
        // 悲观锁
        synchronized (userId.toString().intern()) {
            // 获取事务的当前代理对象 需要引进 Aspectj 依赖
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * 库存充足 -> 一人一单 -> 减少库存 -> 创建订单
     * @param voucherId 订单id
     * @return Result
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单业务
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("您已购买过该商品！请勿再次购买");
        }
        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                // 乐观锁 cas
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock = ?
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }
        // 3.3 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisIdWorker.nextId("order"));
        save(voucherOrder);
        // 4.返回
        return Result.ok(orderId);
    } // 事务提交
```

### 4.分布式锁
上面一人一单在单体环境下可以控制线程安全，但是在分布式环境下，一个Tomcat一个JVM，不能保证线程安全，所以需要采用分布式锁

![image](https://user-images.githubusercontent.com/83166781/199405874-7c13e29f-0ebd-47ca-b0dc-c641e04bc60e.png)

![image](https://user-images.githubusercontent.com/83166781/199405914-a6b5fa11-4218-476b-a381-f5e00159bacd.png)

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
unlock.lua
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
Redis实现分布式锁代码
```java
public class SimpleRedisLock implements ILock{
    // 锁名称
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 锁前缀
     */
    private static final String KEY_PREFIX = "lock:";
    /**
     * UUID 区分不同 JVM
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    /**
     * 静态代码块加载好lua.脚本
     */
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate,String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 当前线程标识 不同UUID + 不同的线程ID 保证线程标识唯一
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁  互斥  同时设置超时时间
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId , timeoutSec, TimeUnit.SECONDS);
        // 防止npe
        return Boolean.TRUE.equals(success);
    }

    /**
     * 调用lua脚本 解决误删锁问题中原子性问题
     */
    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
```
```xml-dtd
Redis分布式锁总结：
1、采用Redis实现分布式锁（setnx）ex 设置过期时间防止死锁
2、Redis分布式锁误删问题（当前线程标识与Redis分布式锁中value比较）
3、lua脚本解决分布式锁误删问题解决方案中出现的原子性问题
```
尽管Redis分布式可以通过lua脚本实现比锁删锁原子性问题，但是如果我们可以在锁到期后给他延时，是不是就不会出现后面的问题了
这时就出现了Redisson了

### 5.分布式锁-Redisson
基于setnx实现的分布式锁存在下面的问题：
![image](https://user-images.githubusercontent.com/83166781/199487566-cd8bf2e0-b611-448b-93f6-95ea28bd19a9.png)

使用Redisson可以解决以上四个问题
使用Redisson的步骤：
```xml
<dependency>
	<groupId>org.redisson</groupId>
	<artifactId>redisson</artifactId>
	<version>3.13.6</version>
</dependency>
```
```java
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.xxx.xxx:6379")
            .setPassword("xxxx");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}

// 秒杀业务代码修改片段
 //创建锁对象 这个代码不用了，因为我们现在要使用Redisson实现分布式锁
 //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
  RLock lock = redissonClient.getLock("lock:order:" + userId);
  //获取锁对象
  boolean isLock = lock.tryLock();
```
1. 使用Redisson可以实现可重入式的重要原因是底层采用了Hash结构，记录了线程标识和重入的次数
![image](https://user-images.githubusercontent.com/83166781/199488483-b239949c-ca14-4808-9492-b6f246727140.png)
![image](https://user-images.githubusercontent.com/83166781/199488523-98190268-7c7f-45d6-af39-d715991ed5c1.png)

2. 使用Redisson实现可重试锁的原理是底层采用了信号量和发布订阅模型
![image](https://user-images.githubusercontent.com/83166781/199488750-4320c771-b3d5-4a7f-8184-7049b28b3e60.png)

3. 使用Redisson实现可超时续约的原理是底层采用了看门狗机制，触发了时会刷新expire
![image](https://user-images.githubusercontent.com/83166781/199489044-963138dd-d573-47db-b1d6-4e074ff77d28.png)

4. 使用Redisson实现主从一致性主要采用了Redisson的联锁机制，将多个Redis节点联锁，只有在该集合中的锁都重入成功，才算获取锁成功
![image](https://user-images.githubusercontent.com/83166781/199489529-6e829ebe-3a68-42dd-9f3e-a9af5027b6db.png)

![image](https://user-images.githubusercontent.com/83166781/199489818-52fda3cf-69dd-43f0-b0eb-4cb4f434c230.png)

### 6.秒杀优化
我们来回顾一下下单流程
当用户发起请求，此时会请求nginx，nginx会访问到tomcat，而tomcat中的程序，会进行串行操作，分成如下几个步骤
![image](https://user-images.githubusercontent.com/83166781/199976904-d097bbc5-6d7e-4d6b-be18-6dea30496ce2.png)

在这六步操作中，有很多操作是要去操作数据库的，而且还是一个线程串行执行， 这样就会导致我们的程序执行的很慢，所以我们需要异步程序执行，那么如何加速呢？
优化方案：
```xml
我们将耗时比较短的逻辑判断放入到redis中，比如是否库存足够，比如是否一人一单，这样的操作，只要这种逻辑可以完成，就意味着我们是一定可以下单完成的，我们只需要进行快速的逻辑判断，而其他需要操作数据库的可以放到异步线程去执行，再加以事务控制，就能完成优化
考虑到redis中判断库存(stock>0)和一人一单(Set不可重复)需要两者都成立才可以进行下单，所以需要采用lua脚本做到原子性
```
![image](https://user-images.githubusercontent.com/83166781/199977140-b11ceaa1-f4e2-4df1-97a8-f09bfdb19094.png)
![image](https://user-images.githubusercontent.com/83166781/199977196-b2ea4dbf-610b-4ef0-b2eb-79a5cfde0ef0.png)

优化总体思路：
![image](https://user-images.githubusercontent.com/83166781/199977771-93015391-4d3d-4347-8e05-25617145b04b.png)

关键代码：
```java
/**
 * 阻塞队列： 当一个线程尝试从队列中获取元素时，如果队列中没有元素，线程就会被阻塞，直到队列中有元素
 *
 *   而我们订单队列就是 有人去下单队列中才会去完成对应的操作
 */
private BlockingQueue<VoucherOrder> orderBlockingQueue = new ArrayBlockingQueue<>(1024 * 1024);
/**
 * 异步线程完成订单在数据库中的操作
 */
private static final ExecutorService SECKILL_ORDER_HANDLER = Executors.newSingleThreadExecutor();
/**
 * 事务代理对象
 */
private IVoucherOrderService proxy;

/**
 * 执行lua脚本的初始化工作
 */
private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
static {
    SECKILL_SCRIPT = new DefaultRedisScript<>();
    SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    SECKILL_SCRIPT.setResultType(Long.class);
}
/**
 * PostConstruct Spring注解：类完成初始化后执行的方法
 */
@PostConstruct
public void init() {
    SECKILL_ORDER_HANDLER.submit(new VoucherOrderHandler());
}
/**
 * 线程任务
 * 内部类，从阻塞队列中取出订单信息，完成订单的创建
 */
private class VoucherOrderHandler implements Runnable{
    @Override
    public void run() {
        while (true) {
            try {
                // 1.从队列中获取订单信息
                VoucherOrder voucherOrder = orderBlockingQueue.take();
                // 2.创建订单
                handleVoucherOrder(voucherOrder);
            } catch (Exception e) {
                log.error("异步线程异常：",e);
            }
        }
    }
}
/**
 * 创建订单（异步线程去调用）
 * @param voucherOrder  阻塞队列中获取的订单
 * @return
 */
private void handleVoucherOrder(VoucherOrder voucherOrder) {
    // 不能从ThreadLocal中取得，因为线程是子线程，而不是原来的线程
    // Long userId = UserHolder.getUser().getId();
    Long userId = voucherOrder.getUserId();
    // 创建锁对象
    RLock redissonClientLock = redissonClient.getLock("lock:order:" + userId);
    // 获取锁
    boolean isLock = redissonClientLock.tryLock();
    // 是否获取锁成功
    if (!isLock) {
        // 失败,返回错误信息
        log.error("不允许重复下单");
    }
    try {
        // 同样 private static final ThreadLocal<Object> currentProxy 也是不能从ThreadLocal中取
        // 获取事务的当前代理对象
        // IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        proxy.createVoucherOrder(voucherOrder);
    } finally {
        // 释放锁
        redissonClientLock.unlock();
    }
}

/**
 * 优惠券秒杀业务
 * 我们去下单时，是通过lua表达式去原子执行判断逻辑，如果判断我出来不为0 ，则要么是库存不足，要么是重复下单，返回错误信息
 * 如果是0，则把下单的逻辑保存到队列中去，然后异步执行
 * @param voucherId 优惠券ID
 * @return 无
 */
@Override
public Result seckillVoucher(Long voucherId) {

    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");
    // 1. 执行lua脚本
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            // key类型参数为0
            Collections.emptyList(),
            voucherId.toString(), userId.toString(), String.valueOf(orderId));
    // 2. 得到lua脚本执行结构，为0有购买资格，为1没有购买资格
    int resultValue = result.intValue();
    if (resultValue != 0) {
        // 没有资格
        // 2.1.不为0 ，代表没有购买资格
        return Result.fail(resultValue == 1 ? "库存不足" : "不能重复下单");
    }
    // 2.2 有资格，把下单信息保存到阻塞队列
    // 3.创建订单信息
    VoucherOrder voucherOrder = new VoucherOrder();
    voucherOrder.setVoucherId(voucherId);
    voucherOrder.setUserId(userId);
    voucherOrder.setId(orderId);
    // 3.1保存到阻塞队列
    orderBlockingQueue.add(voucherOrder);
    // 异步创建线程完成订单的创建
    // 4.创建代理对象
    proxy = (IVoucherOrderService) AopContext.currentProxy();
    // 4.返回
    return Result.ok(orderId);
}

@Transactional(rollbackFor = Exception.class)
@Override
public void createVoucherOrder(VoucherOrder voucherOrder) {
    // 一人一单业务
    Long userId = voucherOrder.getUserId();
    int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
    if (count > 0) {
        log.error("您已购买过该商品！请勿再次购买");
    }
    // 扣减库存
    boolean success = seckillVoucherService.update()
            .setSql("stock = stock - 1") // set stock = stock - 1
            // 乐观锁 cas
            .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock = ?
            .update();
    if (!success) {
        log.error("库存不足!");
    }
    // 3.3 创建订单
    save(voucherOrder);
}
```
