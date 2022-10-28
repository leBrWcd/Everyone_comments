package com.rrdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.rrdp.mapper")
@SpringBootApplication
public class RRDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RRDianPingApplication.class, args);
    }

}
