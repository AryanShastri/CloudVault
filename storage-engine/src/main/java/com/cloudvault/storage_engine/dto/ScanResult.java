package com.cloudvault.storage_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScanResult {


    private boolean clean;
    private String details;
    private String virusName;

    public static ScanResult clean() {
        return new ScanResult(true, "No threats detected", null);
    }

    public static ScanResult infected(String details) {

        String virusName = "Unknown";
        if (details != null && details.contains(":")) {
            String middle = details.substring(
                    details.indexOf(":") + 1).trim();
            if (middle.contains("FOUND")) {
                virusName = middle.replace("FOUND", "").trim();
            }
        }
        return new ScanResult(false, details, virusName);
    }

    public static ScanResult scannerUnavailable() {
        return new ScanResult(true,
                "Scanner unavailable — file allowed", null);
    }
}