package org.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication//默认扫描和启动类同目录下的所有包和类，否则使用@ComponentScan指定要扫描的包
public class ZkLockApp {
    public static void main(String[] args) {
        SpringApplication.run(ZkLockApp.class, args);
    }
}