package com.commentengine.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 본체 — 정형 수치를 받아 코멘트를 만든다. 분야는 모른다 */
@SpringBootApplication
public class CommentEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentEngineApplication.class, args);
    }
}
