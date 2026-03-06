package com.atguigu.study.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("message", "测试成功");
        result.put("time", java.time.LocalDateTime.now().toString());
        return result;
    }

    @GetMapping("/test/chat")
    public Map<String, Object> testChat(@RequestParam(value = "message", defaultValue = "test") String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("received", message);
        result.put("response", "后端正常工作，message参数接收成功");
        return result;
    }
}
