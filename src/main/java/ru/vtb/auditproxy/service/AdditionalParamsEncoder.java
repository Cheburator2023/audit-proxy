package ru.vtb.auditproxy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Кодирует additionalFields (Map&lt;String,Object&gt;) в Map&lt;String,String&gt;
 * для Avro-схемы vtb.avsc (additionalParams объявлен как map&lt;string,string&gt;).
 *
 * null-значения и null-ключи пропускаются.
 * Объекты преобразуются в String через toString().
 */
@Slf4j
@Component
public class AdditionalParamsEncoder {

    public Map<String, String> encode(Map<String, Object> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        source.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            result.put(key, String.valueOf(value));
        });
        return result;
    }
}