package com.rrdp.service.impl;

import com.rrdp.dto.Result;
import com.rrdp.entity.SeckillVoucher;
import com.rrdp.entity.VoucherOrder;
import com.rrdp.mapper.VoucherOrderMapper;
import com.rrdp.service.ISeckillVoucherService;
import com.rrdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.utils.UserHolder;
import com.rrdp.utils.redis.RedisConstants;
import com.rrdp.utils.redis.RedisIdWorker;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/31
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 优惠券秒杀
     * @param voucherId 优惠券ID
     * @return 无
     */
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
}
