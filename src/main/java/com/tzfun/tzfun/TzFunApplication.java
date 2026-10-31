package com.tzfun.tzfun;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.tzfun.tzfun.mapper")
public class TzFunApplication {

	public static void main(String[] args) {
		SpringApplication.run(TzFunApplication.class, args);
	}

}
