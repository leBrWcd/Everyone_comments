package com.rrdp.service.impl;

import com.rrdp.dto.Result;
import com.rrdp.entity.VoucherOrder;
import com.rrdp.mapper.VoucherOrderMapper;
import com.rrdp.service.ISeckillVoucherService;
import com.rrdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.utils.UserHolder;
import com.rrdp.utils.redis.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/31
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
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
}
