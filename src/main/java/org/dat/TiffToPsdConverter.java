package org.dat;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TiffToPsdConverter {
    public static void main(String[] args) {
        String inputFilePath = "depth_output.tif";
        String outputFilePath = "depth_output.psd";

        try {
            // Đọc file TIFF
            // TwelveMonkeys plugin sẽ tự động được sử dụng
            BufferedImage image = ImageIO.read(new File(inputFilePath));

            if (image != null) {
                // Ghi file dưới định dạng PSD
                // TwelveMonkeys plugin sẽ được sử dụng để ghi
                ImageIO.write(image, "psd", new File(outputFilePath));
                System.out.println("Chuyển đổi thành công!");
            } else {
                System.out.println("Không thể đọc file TIFF.");
            }
        } catch (IOException e) {
            System.out.println("Lỗi trong quá trình chuyển đổi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}