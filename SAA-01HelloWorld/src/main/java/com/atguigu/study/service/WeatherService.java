package com.atguigu.study.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    
    // 高德天气API Key
    private static final String AMAP_KEY = "21212e4200f7aa3e384c88a1c1117db2";
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public Map<String, Object> getWeather(String city) {
        log.info("查询天气: {}", city);
        
        try {
            String url = String.format(
                "https://restapi.amap.com/v3/weather/weatherInfo?city=%s&key=%s&extensions=base",
                city, AMAP_KEY
            );
            
            log.info("请求高德API: {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            log.info("高德API响应: {}", response.getBody());
            
            if (response.getBody() != null) {
                // 检查状态码 (高德返回 "10000" 表示成功)
                String infoCode = (String) response.getBody().get("infocode");
                if ("10000".equals(infoCode)) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> lives = 
                        (java.util.List<Map<String, Object>>) response.getBody().get("lives");
                    if (lives != null && !lives.isEmpty()) {
                        return parseWeatherData(lives.get(0));
                    }
                }
            }
            
            log.warn("高德API返回数据格式异常，使用模拟数据");
            return getMockWeatherData(city);
            
        } catch (Exception e) {
            log.error("查询天气失败: {}", e.getMessage(), e);
            return getMockWeatherData(city);
        }
    }
    
    private Map<String, Object> parseWeatherData(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("city", data.get("city"));
        result.put("weather", data.get("weather"));
        result.put("temperature", data.get("temperature"));
        result.put("winddirection", data.get("winddirection"));
        result.put("windpower", data.get("windpower"));
        result.put("humidity", data.get("humidity"));
        result.put("reporttime", data.get("reporttime"));
        result.put("note", null); // 真实数据
        return result;
    }
    
    private Map<String, Object> getMockWeatherData(String city) {
        Map<String, Object> result = new HashMap<>();
        result.put("city", city);
        result.put("weather", "晴");
        result.put("temperature", "25");
        result.put("winddirection", "西南");
        result.put("windpower", "3级");
        result.put("humidity", "50%");
        result.put("reporttime", java.time.LocalDateTime.now().toString());
        result.put("note", "使用模拟数据 - API调用失败");
        return result;
    }
}
