package com.example.jsonparser;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SpringBootApplication
public class JsonParserApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JsonParserApplication.class, args);
    }

    @Autowired
    private Reader reader;

    @Autowired
    private Map<String, PropExtractor> propExtractors;

    @Override
    public void run(String... args) throws Exception {
        List<File> jsonFiles = reader.readFiles();
        Map<String, Map<String, String>> itemMap = new HashMap<>();
        for (var jsonFile : jsonFiles) {
            log.info("Start processing json {}", jsonFile.getName());

            String jsonString = FileUtils.readFileToString(jsonFile);
            JSONObject json = new JSONObject(jsonString);
            String itemName = PropExtractor.extractStringValue(json.getJSONObject("name"));

            JSONArray infoBlocks = json.getJSONArray("infoBlocks");
            Map<String, String> itemParameters = new HashMap<>();
            itemMap.put(itemName, itemParameters);
            for (int infoBlockIndex = 0; infoBlockIndex < infoBlocks.length(); infoBlockIndex++) {

                JSONObject infoBlock = infoBlocks.getJSONObject(infoBlockIndex);
                JSONArray elements = infoBlock.optJSONArray("elements");
                if (elements == null) {
                    continue;
                }
                log.info("Elements extracted from json {} iter {}", jsonFile.getName(), infoBlockIndex);

                for (int elementIndex = 0; elementIndex < elements.length(); elementIndex++) {
                    JSONObject element = elements.getJSONObject(elementIndex);
                    String type = element.getString("type");
                    propExtractors.get(type)
                            .extract(element)
                            .placeTo(itemParameters);
                }
            }
        }
        JSONObject object = new JSONObject(itemMap);
        System.out.println(object);
    }


}

interface PropExtractor {

    Prop extract(JSONObject element);

    static String extractStringValue(JSONObject object) {
        if (object.getString("type").equals("translation")) {
            return object.getJSONObject("lines").getString("ru");
        } else {
            return object.getString("text");
        }
    }
}

@Component("key-value")
@RequiredArgsConstructor
class KeyValuePropExtractor implements PropExtractor {

    @Override
    public Prop extract(JSONObject element) {
        JSONObject key = element.getJSONObject("key");
        JSONObject value = element.getJSONObject("value");
        String itemPropKey = PropExtractor.extractStringValue(key);
        String itemPropValue = PropExtractor.extractStringValue(value);
        return new Prop(itemPropKey, itemPropValue);
    }
}

@Component("numeric")
@RequiredArgsConstructor
class NumericPropExtractor implements PropExtractor {

    @Override
    public Prop extract(JSONObject element) {
        JSONObject key = element.getJSONObject("name");
        String itemPropKey = PropExtractor.extractStringValue(key);
        String itemPropValue = element.get("value").toString();
        return new Prop(itemPropKey, itemPropValue);
    }
}

@Component("text")
@RequiredArgsConstructor
class TextPropExtractor implements PropExtractor {

    @Override
    public Prop extract(JSONObject element) {
        JSONObject text = element.getJSONObject("text");
        String itemPropKey = "Свойство";
        String itemPropValue = PropExtractor.extractStringValue(text);
        return new Prop(itemPropKey, itemPropValue);
    }
}

@Component("item")
@RequiredArgsConstructor
class ItemPropExtractor implements PropExtractor {

    @Override
    public Prop extract(JSONObject element) {
        JSONObject text = element.getJSONObject("name");
        String itemPropKey = "Подходит для";
        String itemPropValue = PropExtractor.extractStringValue(text);
        return new Prop(itemPropKey, itemPropValue);
    }
}

@Component("range")
@RequiredArgsConstructor
class RangePropExtractor implements PropExtractor {

    @Override
    public Prop extract(JSONObject element) {
        JSONObject key = element.getJSONObject("name");
        String itemPropKey = PropExtractor.extractStringValue(key);
        String itemPropMax = element.get("max").toString();
        String itemPropMin = element.get("min").toString();
        return new Prop(itemPropKey, String.format("%s to %s", itemPropMin, itemPropMax));
    }
}

interface Placeable {
    void placeTo(Map<String, String> map);
}

@Value
class Prop implements Placeable {
    String key;
    String value;

    @Override
    public void placeTo(Map<String, String> map) {
        if (map.containsKey(key)) {
            map.merge(key, value, (s, s2) -> String.format("%s, %s", s, s2));
        } else {
            map.put(key, value);
        }
    }
}

@Component
@RequiredArgsConstructor
class Reader {

    @SneakyThrows
    public List<File> readFiles() {
        File[] files = ResourceUtils.getFile("classpath:stalcraft").listFiles();
        if (files == null) {
            return List.of();
        }
        List<File> jsons = new ArrayList<>();
        extractFiles(files, jsons);
        return jsons.stream()
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());
    }

    private void extractFiles(File[] files, List<File> jsons) {
        for (var file : files) {
            if (file.isDirectory()) {
                File[] nestedFiles = file.listFiles();
                if (nestedFiles == null) {
                    continue;
                }
                extractFiles(nestedFiles, jsons);
            } else {
                jsons.add(file);
            }
        }
    }
}
