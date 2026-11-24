package com.finesse.composite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * tetris 칩의 조합 서비스 (교재 3장 product-composite 자리, 설계서 4장).
 * 화면 요청을 받아 tetris-analytics를 부르고 결과를 넘긴다. 엔진 호출(light·heavy)은 조각 8·9에서 더한다.
 */
@SpringBootApplication
public class FinesseApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinesseApiApplication.class, args);
    }
}
