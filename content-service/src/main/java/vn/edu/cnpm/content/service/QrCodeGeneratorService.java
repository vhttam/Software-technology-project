package vn.edu.cnpm.content.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeGeneratorService {

    public byte[] generatePng(String text, int size) {
        try {
            int dimension = Math.max(100, Math.min(size, 2000));
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, dimension, dimension, hints);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR Code PNG: " + e.getMessage(), e);
        }
    }

    public String generateSvg(String text, int size) {
        try {
            int dimension = Math.max(100, Math.min(size, 2000));
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, dimension, dimension, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();

            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
              .append(width).append(" ").append(height)
              .append("\" width=\"").append(dimension).append("\" height=\"").append(dimension).append("\">\n")
              .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
              .append("<path d=\"");

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        sb.append("M").append(x).append(",").append(y).append("h1v1h-1z ");
                    }
                }
            }
            sb.append("\" fill=\"#000000\"/>\n</svg>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR Code SVG: " + e.getMessage(), e);
        }
    }
}
