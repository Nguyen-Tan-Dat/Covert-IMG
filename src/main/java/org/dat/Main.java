package org.dat;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {

    // Lấy thư mục chứa JAR (hoặc build/classes khi chạy IDE)
    static Path jarDir() {
        try {
            URI uri = URI.create("./");
            Path loc = Paths.get(uri);
            return Files.isDirectory(loc) ? loc : loc.getParent();
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        }
    }

    // Đọc tất cả dòng từ file (UTF-8)
    static List<String> readAllLines(Path p) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            List<String> out = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) out.add(line);
            return out;
        }
    }

    public static void main(String[] args) {
        Path baseJar = jarDir(); // nơi chứa JAR
        System.out.println("[INFO] JAR dir : " + baseJar);

        // Files đặt cạnh JAR khi deploy
        Path inputList   = baseJar.resolve("output_tif.txt");
        Path licenseFile = baseJar.resolve("license.txt");

        // ===== 1) Kiểm tra license =====
        if (!Files.exists(licenseFile)) {
            System.err.println("[ERROR] Missing license file: " + licenseFile);
            return;
        }
        try {
            String licenseKey = Files.readString(licenseFile, StandardCharsets.UTF_8).trim();
            if (licenseKey.isEmpty()) {
                System.err.println("[ERROR] License file rỗng");
                return;
            }
            String computed = License.getLicense();
            if (!licenseKey.equals(computed)) {
                System.err.println("[ERROR] License verification failed! expected=" + licenseKey + " got=" + computed);
                return;
            }
            System.out.println("[OK] License verified.");
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }

        // ===== 2) Đọc danh sách TIF (cạnh JAR) =====
        if (!Files.exists(inputList)) {
            System.err.println("[ERROR] Không tìm thấy " + inputList);
            return;
        }

        List<String> tifPaths = new ArrayList<>();
        try {
            for (String raw : readAllLines(inputList)) {
                String s = raw == null ? "" : raw.trim();
                if (s.isEmpty()) continue;
                Path p = Paths.get(s);
                if (!p.isAbsolute()) p = baseJar.resolve(s).normalize(); // cho phép path tương đối
                if (Files.exists(p) && !Files.isDirectory(p)) {
                    tifPaths.add(p.toString());
                } else {
                    System.err.println("[WARNING] Bỏ qua (không tồn tại): " + p);
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Không thể đọc " + inputList + " : " + e);
            return;
        }

        if (tifPaths.isEmpty()) {
            System.err.println("[ERROR] Không có file TIF hợp lệ.");
            return;
        }

        // ===== 3) Convert TIF → PSD =====
        List<String> psdPaths = new ArrayList<>();
        for (String tif : tifPaths) {
            try {
                Path tifPath = Paths.get(tif);
                String baseName = tifPath.getFileName().toString().replaceAll("(?i)\\.tif$", "");

                // Luôn ghi PSD cạnh JAR
                Path psdPath = baseJar.resolve(baseName + ".psd");

                boolean ok = TiffToPsdConverter.convert(tifPath.toString(), psdPath.toString());
                if (ok) {
                    psdPaths.add(psdPath.toString());
                    System.out.println("[SUCCESS] " + tifPath + " -> " + psdPath);
                } else {
                    System.err.println("[FAIL] Convert: " + tifPath);
                }
            } catch (Throwable t) {
                System.err.println("[EXC] Convert error: " + tif + " : " + t);
            }
        }

        // ===== 4) Ghi output.txt cạnh JAR =====
        Path outputTxt = baseJar.resolve("output.txt");
        try (BufferedWriter bw = Files.newBufferedWriter(outputTxt, StandardCharsets.UTF_8)) {
            for (String psd : psdPaths) {
                bw.write(psd);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Không ghi được output.txt : " + e);
        }

        // ===== 5) Xóa input_tif.txt và file .tif gốc =====
        try {
            for (String tif : tifPaths) {
                Files.deleteIfExists(Paths.get(tif));
            }
            Files.deleteIfExists(inputList);
        } catch (IOException e) {
            System.err.println("[WARNING] Không xóa được file: " + e.getMessage());
        }

        System.out.println("[DONE] PSD count: " + psdPaths.size());
        System.out.println("[INFO] output.txt: " + outputTxt);
    }
}
