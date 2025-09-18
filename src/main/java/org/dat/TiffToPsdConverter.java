package org.dat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TiffToPsdConverter {
    public static boolean convert(String tifPath, String psdPath) {
        try {
            BufferedImage img = ImageIO.read(new File(tifPath));
            if (img == null) {
                System.err.println("[ERROR] Không đọc được file: " + tifPath);
                return false;
            }
            ImageIO.write(img, "psd", new File(psdPath));
            System.out.println("[SUCCESS] Đã lưu: " + psdPath);
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Chuyển đổi thất bại: " + tifPath);
            e.printStackTrace();
            return false;
        }
    }
}
