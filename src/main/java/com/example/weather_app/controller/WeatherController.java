package com.example.weather_app.controller;

import org.springframework.ui.Model;

import com.example.weather_app.model.WeatherResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class WeatherController {

    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/")
    public String getIndex() {
        return "index";
    }

    @GetMapping("/weather")
    public String getWeather(@RequestParam("city") String city, Model model) {
        try {
            // First, get coordinates for the city
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + city + "&count=1&language=en&format=json";
            logger.info("Fetching coordinates for city: {} from URL: {}", city, geoUrl);

            JsonNode geoResponse = restTemplate.getForObject(geoUrl, JsonNode.class);

            if (geoResponse == null || !geoResponse.has("results") || geoResponse.get("results").size() == 0) {
                logger.warn("City not found: {}", city);
                model.addAttribute("error", "City not found. Please try another location.");
                return "weather";
            }

            JsonNode result = geoResponse.get("results").get(0);
            double latitude = result.get("latitude").asDouble();
            double longitude = result.get("longitude").asDouble();
            String countryName = result.has("country") ? result.get("country").asText() : "Unknown";
            String cityName = result.get("name").asText();

            logger.info("Found city: {} at lat: {}, lon: {}", cityName, latitude, longitude);

            // Now get real-time weather data
            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude +
                              "&longitude=" + longitude +
                              "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto";
            logger.info("Fetching weather data from URL: {}", weatherUrl);

            JsonNode weatherResponse = restTemplate.getForObject(weatherUrl, JsonNode.class);

            if (weatherResponse != null && weatherResponse.has("current")) {
                JsonNode current = weatherResponse.get("current");
                double temp = current.get("temperature_2m").asDouble();
                int humidity = current.get("relative_humidity_2m").asInt();
                double windSpeed = current.get("wind_speed_10m").asDouble();
                int weatherCode = current.get("weather_code").asInt();
                String weatherDescription = getWeatherDescription(weatherCode);
                String weatherIcon = getWeatherIcon(weatherCode);

                logger.info("Weather data received for city: {} - Temp: {}°C", cityName, temp);
                model.addAttribute("city", cityName);
                model.addAttribute("country", countryName);
                model.addAttribute("latitude", String.format("%.2f", latitude));
                model.addAttribute("longitude", String.format("%.2f", longitude));
                model.addAttribute("weatherDescription", weatherDescription);
                model.addAttribute("temperature", String.format("%.1f", temp));
                model.addAttribute("humidity", humidity);
                model.addAttribute("windSpeed", String.format("%.1f", windSpeed));
                model.addAttribute("weatherIcon", weatherIcon);
            } else {
                logger.warn("No weather data received for city: {}", city);
                model.addAttribute("error", "Unable to fetch weather data.");
            }
        } catch (RestClientException e) {
            logger.error("API Error: Failed to fetch weather data for city: {}. Error: {}", city, e.getMessage());
            model.addAttribute("error", "Unable to fetch weather data. Please try again later.");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching weather for city: {}. Error: {}", city, e.getMessage(), e);
            model.addAttribute("error", "An unexpected error occurred. Please try again.");
        }
        return "weather";
    }

    private String getWeatherDescription(int code) {
        return switch(code) {
            case 0, 1 -> "Clear sky";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Light rain";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    private String getWeatherIcon(int code) {
        return switch(code) {
            case 0, 1 -> "wi wi-day-sunny";
            case 2 -> "wi wi-day-partly-cloudy";
            case 3 -> "wi wi-cloudy";
            case 45, 48 -> "wi wi-fog";
            case 51, 53, 55, 61, 63, 65, 80, 81, 82 -> "wi wi-rain";
            case 71, 73, 75, 77, 85, 86 -> "wi wi-snow";
            case 95, 96, 99 -> "wi wi-thunderstorm";
            default -> "wi wi-day-sunny";
        };
    }
}
