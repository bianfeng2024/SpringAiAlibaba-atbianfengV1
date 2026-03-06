package com.atguigu.study.controller;

import com.atguigu.study.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);
    
    @Autowired
    private WeatherService weatherService;
    
    /**
     * 查询天气接口
     * @param city 城市名称
     * @return 天气信息
     */
    @GetMapping("/query")
    public Map<String, Object> query(
            @RequestParam(value = "city", defaultValue = "北京") String city) {
        log.info("查询天气: {}", city);
        
        try {
            Map<String, Object> weather = weatherService.getWeather(city);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("city", city);
            result.put("data", weather);
            
            return result;
            
        } catch (Exception e) {
            log.error("查询天气失败: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询天气失败");
            result.put("error", e.getMessage());
            return result;
        }
    }
    
    /**
     * 获取支持的热门城市列表
     */
    @GetMapping("/cities")
    public Map<String, Object> getCities() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("cities", java.util.Arrays.asList(
            "北京", "上海", "广州", "深圳", "杭州",
            "成都", "武汉", "西安", "南京", "重庆"
        ));
        return result;
    }
}
