package com.rishabh.cowin.vaccinetracker.controller;

import com.rishabh.cowin.vaccinetracker.utils.SoundUtils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
public class CovinController {

    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final RestTemplate restTemplate;
    private static final String myOS = System.getProperty("os.name").toLowerCase();
    private static final String url = "https://www.cowin.gov.in/home";

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
    public void keepChecking() throws InterruptedException {
        LocalDate today = LocalDate.now().plusDays(1);
        for (int i = 0; i < lookForward; i++) {
            LocalDate dateToCheck = today.plusDays(i);
            JSONArray jsonArray = checkAvailability(pincode, dateToCheck.format(format));
            if (!jsonArray.isEmpty()) {
                log.info("Slots available !!!! : {}  -> {}", dateToCheck, jsonArray.length());
                openBrowser();
                SoundUtils.tone(400, 500);
                SoundUtils.tone(400, 500);
                SoundUtils.tone(400, 500);
            } else {
                log.info("No slot available for : {}", dateToCheck);
                Thread.sleep(1000);
            }
        }
        System.out.println("-----------------------------------------" + LocalDateTime.now() + "-------------------------------------------------------");
    }

    private JSONArray checkAvailability(Integer pinCode, String date) {
        JSONArray availableSLots = new JSONArray();
        HttpEntity<String> entity = new HttpEntity<>("parameters", getHeaders());
        String url = String.format(baseUrl, pinCode, date);
        log.info(url);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        log.info("Http Status = {}", responseEntity.getStatusCode());
        log.info("Response Body = " + responseEntity.getBody());
        JSONObject jsonObject = new JSONObject(responseEntity.getBody());
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
        try {
            SoundUtils.tone(400, 500);
            SoundUtils.tone(400, 500);
            if (Desktop.isDesktopSupported()) {
                // Probably Windows
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(new URI(url));
            } else {
                // Definitely Non-windows
                Runtime runtime = Runtime.getRuntime();
                if (myOS.contains("mac")) {
                    // Apples
                    runtime.exec("open " + url);
                } else if (myOS.contains("nix") || myOS.contains("nux")) {
                    // Linux flavours
                    runtime.exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MultiValueMap<String, String> getHeaders() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("accept", "*/*");
        headers.add("accept-language", "en-US,en;q=0.9,hi;q=0.8");
        headers.add("dnt", "1");
        headers.add("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
        return headers;
    }
}
