package com.aireview.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

/**
 * 自绘算术验证码：返回一张 PNG 图片的 base64，以及算式结果。
 *
 * <p>不引入第三方验证码库，仅用 JDK 自带的 AWT 绘制；Spring Boot 默认 {@code headless=true}，
 * 服务器环境可正常使用。
 */
public final class CaptchaImageUtil {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 48;
    private static final Font FONT = new Font("SansSerif", Font.BOLD, 28);

    private CaptchaImageUtil() {
    }

    public record Captcha(String answer, String base64Image) {
    }

    public static Captcha generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int left = random.nextInt(1, 10);
        int right = random.nextInt(1, 10);
        int operator = random.nextInt(3);
        String symbol = switch (operator) {
            case 0 -> "+";
            case 1 -> "-";
            default -> "×";
        };
        int answer = switch (operator) {
            case 0 -> left + right;
            case 1 -> left - right;
            default -> left * right;
        };

        String expression = left + " " + symbol + " " + right + " = ?";
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xF6, 0xF7, 0xF9));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            // 干扰线，提高一点肉眼辨识成本
            g.setColor(new Color(0xD8, 0xDE, 0xE4));
            for (int i = 0; i < 4; i++) {
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            g.setColor(new Color(0x1F, 0x23, 0x28));
            g.setFont(FONT);
            g.drawString(expression, 10, 32);
        } finally {
            g.dispose();
        }
        return new Captcha(String.valueOf(answer), "data:image/png;base64," + toBase64Png(image));
    }

    private static String toBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("验证码图片生成失败", exception);
        }
    }
}
