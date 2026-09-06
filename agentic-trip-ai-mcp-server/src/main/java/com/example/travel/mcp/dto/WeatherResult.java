package com.example.travel.mcp.dto;

public record WeatherResult(boolean success, String location, String summary, boolean rainLikely) {
}
