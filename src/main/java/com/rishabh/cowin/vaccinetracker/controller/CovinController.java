package com.rishabh.cowin.vaccinetracker.controller;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.awt.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
public class CovinController {

    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final RestTemplate restTemplate;

    @Value("${pincode}")
    private Integer pincode;

    @Value("${lookForward:5}")
    private Integer lookForward;

    @Value("${covin.api.url}")
    private String baseUrl;

    public CovinController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/fetch/{pinCode}/{date}")
    public ResponseEntity<String> fetch(@PathVariable Integer pinCode, @PathVariable String date) {
        log.info("Checking for {} on {}", pinCode, date);
        JSONArray availableSLots = checkAvailability(pinCode, date);
        return ResponseEntity.ok(availableSLots.toString());
    }

    @Scheduled(fixedRateString = "${fixedRate:60000}")
    public void keepChecking() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < lookForward; i++) {
            LocalDate dateToCheck = today.plusDays(i);
            JSONArray jsonArray = checkAvailability(pincode, dateToCheck.format(format));
            if (!jsonArray.isEmpty()) {
                log.info("Slots available !!!! : {}  -> {}", dateToCheck, jsonArray.length());
                openBrowser();
            } else {
                log.info("No slot available for : {}", dateToCheck);
            }
        }

    }

    private JSONArray checkAvailability(Integer pinCode, String date) {
        JSONArray availableSLots = new JSONArray();
        HttpEntity<String> entity = new HttpEntity<>(getHeaders());
        String url = String.format(baseUrl, pinCode, date);
        log.info(url);
        ResponseEntity<String> forEntity = restTemplate.getForEntity(url, String.class, entity);
        System.out.println("forEntity.getStatusCode() = " + forEntity.getStatusCode());
        System.out.println("forEntity.getBody() = " + forEntity.getBody());
        JSONObject jsonObject = new JSONObject(forEntity.getBody());
        JSONArray centers = jsonObject.getJSONArray("centers");
        center:
        for (int i = 0; i < centers.length(); i++) {
            JSONObject center = centers.getJSONObject(i);
            JSONArray sessions = center.getJSONArray("sessions");
            for (int j = 0; j < sessions.length(); j++) {
                JSONObject session = sessions.getJSONObject(j);
                if (session.getInt("min_age_limit") == 18 &&
                        session.optInt("available_capacity", 0) > 0) {
                    System.err.println("Got the slot");
                    availableSLots.put(center);
                    break center;
                }
            }
        }
        return availableSLots;
    }

    private void openBrowser() {
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.cowin.gov.in/home"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private MultiValueMap<String, String> getHeaders() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("access-control-allow-credentials", "true");
        headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
