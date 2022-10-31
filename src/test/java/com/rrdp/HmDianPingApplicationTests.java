package com.rrdp;

import com.rrdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;


    @Test
    void testSave() {
        shopService.saveShop2Redis(1L,10L);
    }


}
