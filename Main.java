package com.assignment;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class Main {
    static class BusRecord {
        int rowIndex;
        String routeNumber;
        String operator;
        String isAC;
        String isSeater;
        String isSleeper;
        String deptTime;
        String doj;
        double price;

        public BusRecord(int rowIndex, String[] row) {
            this.rowIndex = rowIndex;
            this.routeNumber = row[0];
            this.operator = row[4];
            this.isAC = row[6];
            this.isSeater = row[7];
            this.isSleeper = row[8];
            this.deptTime = row[9];
            this.doj = row[12];
            try {
                this.price = Double.parseDouble(row[15]);
            } catch (Exception e) {
                this.price = -1; // Invalid or NA price
            }
        }
        
        public String getGroupKey() {
            return routeNumber + "|" + doj + "|" + isAC + "|" + isSeater + "|" + isSleeper;
        }
    }

    public static void main(String[] args) {
        String csvFile = "../Take Home Assignment Dataset.csv";
        System.out.println("Processing CSV Data...");
        
        Map<String, List<BusRecord>> groupedBuses = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] header = reader.readNext(); // skip header
            String[] line;
            int rowIndex = 2; // Excel row index starts at 2 after header
            
            while ((line = reader.readNext()) != null) {
                if (line.length < 16) continue;
                BusRecord record = new BusRecord(rowIndex++, line);
                if (record.price > 0) {
                    groupedBuses.computeIfAbsent(record.getGroupKey(), k -> new ArrayList<>()).add(record);
                }
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Total groups found: " + groupedBuses.size());
        
        try (Workbook workbook = new XSSFWorkbook()) {
            createFlaggingSheet(workbook, groupedBuses);
            createLogicSheet(workbook);
            createAutomationSheet(workbook);
            
            try (FileOutputStream out = new FileOutputStream("../Take_Home_Assignment_Output.xlsx")) {
                workbook.write(out);
                System.out.println("Excel generated successfully at ../Take_Home_Assignment_Output.xlsx");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createFlaggingSheet(Workbook workbook, Map<String, List<BusRecord>> groupedBuses) {
        Sheet sheet = workbook.createSheet("Flagging Output");
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "Flixbus Row Index", "Route Number", "DOJ", "Departure Time",
            "Our Price", "Competitor Avg Price", "Comparable Buses Info", 
            "Price Difference", "% Variance", "Flag"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }

        int rowNum = 1;
        
        for (Map.Entry<String, List<BusRecord>> entry : groupedBuses.entrySet()) {
            List<BusRecord> group = entry.getValue();
            
            List<BusRecord> ourBuses = new ArrayList<>();
            List<BusRecord> competitors = new ArrayList<>();
            
            for (BusRecord b : group) {
                if ("Flixbus".equalsIgnoreCase(b.operator)) {
                    ourBuses.add(b);
                } else {
                    competitors.add(b);
                }
            }
            
            if (ourBuses.isEmpty()) continue; // Skip groups where we don't operate
            
            double competitorAvg = 0;
            if (!competitors.isEmpty()) {
                competitorAvg = competitors.stream().mapToDouble(b -> b.price).average().orElse(0.0);
            }
            
            String compInfo = competitors.size() + " comparable buses";
            
            for (BusRecord ours : ourBuses) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ours.rowIndex);
                row.createCell(1).setCellValue(ours.routeNumber);
                row.createCell(2).setCellValue(ours.doj);
                row.createCell(3).setCellValue(ours.deptTime);
                row.createCell(4).setCellValue(ours.price);
                
                if (competitors.isEmpty()) {
                    row.createCell(5).setCellValue("N/A");
                    row.createCell(6).setCellValue("No Competitors");
                    row.createCell(7).setCellValue("N/A");
                    row.createCell(8).setCellValue("N/A");
                    row.createCell(9).setCellValue("No Flag");
                    continue;
                }
                
                row.createCell(5).setCellValue(Math.round(competitorAvg * 100.0) / 100.0);
                row.createCell(6).setCellValue(compInfo);
                
                double diff = Math.round((ours.price - competitorAvg) * 100.0) / 100.0;
                double variance = Math.round((diff / competitorAvg) * 10000.0) / 100.0; // as %
                
                row.createCell(7).setCellValue(diff);
                row.createCell(8).setCellValue(variance + "%");
                
                String flag = "Normal";
                if (variance > 10) {
                    flag = "High (>" + 10 + "%)";
                } else if (variance < -10) {
                    flag = "Low (<" + -10 + "%)";
                }
                row.createCell(9).setCellValue(flag);
            }
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createLogicSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Logic Explanation");
        Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Logic for Identifying Similar Buses:");
        Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("Buses are considered similar if they share the exact same Route Number, Date of Journey (DOJ), and identical comfort amenities (Is AC, Is Seater, Is Sleeper).");
        
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("Logic for Raising Price Flags:");
        Row row4 = sheet.createRow(4);
        row4.createCell(0).setCellValue("For every Flixbus offering, we calculate the average price of all competing buses in the same similarity group. We then compare our 'Weighted Average Price' with this average.");
        Row row5 = sheet.createRow(5);
        row5.createCell(0).setCellValue("We flag a price as 'High' if it is > 10% more expensive than the average competitor, and 'Low' if it is > 10% cheaper.");
    }
    
    private static void createAutomationSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Automation Plan");
        Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("MVP Automation Plan:");
        Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("1. Store the incoming bus pricing feed in an AWS S3 Bucket or Google Cloud Storage.");
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("2. Schedule an Apache Airflow or AWS Lambda function to trigger daily when new pricing data lands.");
        Row row4 = sheet.createRow(3);
        row4.createCell(0).setCellValue("3. A Python/Pandas script executes the same grouping logic to compute the average competitor price per similarity cohort.");
        Row row5 = sheet.createRow(4);
        row5.createCell(0).setCellValue("4. The script sends an automated Slack alert or creates a Zendesk/Jira ticket for the Pricing Team for any tickets flagged as 'High' or 'Low'.");
        Row row6 = sheet.createRow(5);
        row6.createCell(0).setCellValue("5. Flagged outputs are simultaneously inserted into a Snowflake/Redshift table for ingestion by a dashboarding tool like Tableau or PowerBI for continuous monitoring.");
    }
}
