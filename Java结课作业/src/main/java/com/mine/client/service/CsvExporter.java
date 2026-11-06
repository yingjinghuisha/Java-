package com.mine.client.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 告警记录 CSV 导出（UTF-8 BOM，Excel 打开不乱码）。 */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static int exportAlarms(Path path, List<AlarmEvent> events) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("序号,时间,级别,设备ID,传感器类型,数值,单位,阈值,告警信息");
            writer.newLine();
            int index = 1;
            for (AlarmEvent event : events) {
                writer.write(event.getCsvLine(index++));
                writer.newLine();
            }
            writer.flush();
            return events.size();
        }
    }

    public static Path ensureCsvSuffix(Path path) {
        String name = path.getFileName().toString();
        if (name.toLowerCase().endsWith(".csv")) {
            return path;
        }
        return path.resolveSibling(name + ".csv");
    }
}
