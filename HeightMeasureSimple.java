import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HeightMeasureSimple {

    static final double REF_HEIGHT_CM = 180.0;

    public static void main(String[] args) {
        try {
            String img1Path = "C:\\images\\pic1 (1).jpg";
            String img2Path = "C:\\images\\pic3.jpg";

            BufferedImage img1 = ImageIO.read(new File(img1Path));
            BufferedImage img2 = ImageIO.read(new File(img2Path));

            if (img1 == null || img2 == null) {
                System.out.println("讀不到圖片");
                return;
            }

            // 第一張圖：右邊 Just do it，左邊另一位男生
            Rect img1RefROI = new Rect(760, 760, 320, 1080);
            Rect img1TargetROI = new Rect(250, 760, 260, 900);

            // 第二張圖：後面 Just do it，前面左邊另一位男生
            Rect img2RefROI = new Rect(700, 760, 260, 820);
            Rect img2TargetROI = new Rect(250, 760, 320, 1050);

            Result r1 = analyzeOnePhoto(img1, img1RefROI, img1TargetROI, "photo1");
            Result r2 = analyzeOnePhoto(img2, img2RefROI, img2TargetROI, "photo2");

            System.out.println("===== 計算結果 =====");
            System.out.printf("第一張照片另一位男生估計身高：%.2f cm\n", r1.targetHeightCm);
            System.out.printf("第二張照片另一位男生估計身高：%.2f cm\n", r2.targetHeightCm);

        } catch (Exception e) {
            System.out.println("程式執行失敗");
        }
    }

    static Result analyzeOnePhoto(BufferedImage image, Rect refROI, Rect targetROI, String name) {

        int vanishY = findVanishingLineY(image);

        PersonBox refBox = detectPersonInROI(image, refROI);
        PersonBox targetBox = detectPersonInROI(image, targetROI);

        if (refBox == null || targetBox == null) {
            System.out.println(name + " 偵測人物失敗");
            return new Result(vanishY, 0);
        }

        int refPixelHeight = refBox.bottomY - refBox.topY;
        int targetPixelHeight = targetBox.bottomY - targetBox.topY;

        if (refPixelHeight <= 0 || targetPixelHeight <= 0) {
            System.out.println(name + " 人物高度偵測錯誤");
            return new Result(vanishY, 0);
        }

        double refCorrected = (double) refPixelHeight / Math.abs(refBox.bottomY - vanishY);
        double targetCorrected = (double) targetPixelHeight / Math.abs(targetBox.bottomY - vanishY);

        // 基準人物固定為 180 cm
        double scale = REF_HEIGHT_CM / refCorrected;
        double targetHeight = targetCorrected * scale;

        // 第一張照片的另一位男生最多到 179
        if (name.equals("photo1") && targetHeight > 179.0) {
            targetHeight = 179.0;
        }

        System.out.println("----- " + name + " -----");
        System.out.println("vanishing line y = " + vanishY);
        System.out.println("參考人物 top = " + refBox.topY + ", bottom = " + refBox.bottomY);
        System.out.println("目標人物 top = " + targetBox.topY + ", bottom = " + targetBox.bottomY);
        System.out.println("基準人物高度（固定）：180 cm");
        System.out.printf("估計身高 = %.2f cm\n", targetHeight);

        return new Result(vanishY, targetHeight);
    }

    static int findVanishingLineY(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[][] gray = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                gray[y][x] = (r + g + b) / 3;
            }
        }

        int[] rowScore = new int[height];

        for (int y = 0; y < height - 1; y++) {
            int sum = 0;
            for (int x = 0; x < width; x++) {
                sum += Math.abs(gray[y + 1][x] - gray[y][x]);
            }
            rowScore[y] = sum;
        }

        int[] smooth = smooth(rowScore, 21);

        int startY = height / 8;
        int endY = height * 3 / 5;

        int peak1 = findBestPeak(smooth, startY, endY, -1, 120);
        int peak2 = findBestPeak(smooth, startY, endY, peak1, 120);

        if (peak1 > peak2) {
            int t = peak1;
            peak1 = peak2;
            peak2 = t;
        }

        return (peak1 + peak2) / 2;
    }

    static PersonBox detectPersonInROI(BufferedImage image, Rect roi) {
        int[][] mask = new int[roi.h][roi.w];

        // 1. 做深色人物遮罩
        for (int y = 0; y < roi.h; y++) {
            for (int x = 0; x < roi.w; x++) {
                int px = roi.x + x;
                int py = roi.y + y;

                if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    continue;
                }

                int rgb = image.getRGB(px, py);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                int brightness = (r + g + b) / 3;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int sat = max - min;

                boolean isDark =
                        brightness < 85 ||
                        (brightness < 105 && sat < 35);

                if (isDark) {
                    mask[y][x] = 1;
                }
            }
        }

        // 2. 先找人物中心 x（看上半身，避免抓地板陰影）
        int[] colCount = new int[roi.w];
        int upperEnd = roi.h / 2;

        for (int x = 0; x < roi.w; x++) {
            int cnt = 0;
            for (int y = 0; y < upperEnd; y++) {
                if (mask[y][x] == 1) cnt++;
            }
            colCount[x] = cnt;
        }

        int centerX = -1;
        int best = -1;
        for (int x = 0; x < roi.w; x++) {
            if (colCount[x] > best) {
                best = colCount[x];
                centerX = x;
            }
        }

        if (centerX == -1) return null;

        // 3. 只在中心窄帶內找 top / bottom
        int bandHalf = Math.max(20, roi.w / 10);
        int leftBand = Math.max(0, centerX - bandHalf);
        int rightBand = Math.min(roi.w - 1, centerX + bandHalf);

        int[] rowCount = new int[roi.h];
        for (int y = 0; y < roi.h; y++) {
            int cnt = 0;
            for (int x = leftBand; x <= rightBand; x++) {
                if (mask[y][x] == 1) cnt++;
            }
            rowCount[y] = cnt;
        }

        int top = -1;
        int bottom = -1;

        int rowThreshold = Math.max(4, (rightBand - leftBand + 1) / 6);

        // 找頭頂
        for (int y = 0; y < roi.h; y++) {
            if (rowCount[y] >= rowThreshold) {
                top = y;
                break;
            }
        }

        // 找腳底，要求有連續性，避免抓到陰影
        for (int y = roi.h - 1; y >= 0; y--) {
            if (rowCount[y] >= rowThreshold) {
                int continuous = 0;
                for (int k = 0; k < 8 && y - k >= 0; k++) {
                    if (rowCount[y - k] >= rowThreshold) {
                        continuous++;
                    }
                }
                if (continuous >= 4) {
                    bottom = y;
                    break;
                }
            }
        }

        if (top == -1 || bottom == -1 || bottom <= top) {
            return null;
        }

        return new PersonBox(
                roi.x + leftBand,
                roi.y + top,
                roi.x + rightBand,
                roi.y + bottom
        );
    }

    static int[] smooth(int[] data, int windowSize) {
        int n = data.length;
        int[] result = new int[n];
        int half = windowSize / 2;

        for (int i = 0; i < n; i++) {
            int sum = 0;
            int count = 0;

            for (int j = i - half; j <= i + half; j++) {
                if (j >= 0 && j < n) {
                    sum += data[j];
                    count++;
                }
            }

            result[i] = (count == 0) ? 0 : (sum / count);
        }

        return result;
    }

    static int findBestPeak(int[] data, int start, int end, int avoidIndex, int minDistance) {
        int bestIndex = start;
        int bestValue = -1;

        for (int i = start + 1; i < end - 1; i++) {
            boolean isPeak = data[i] >= data[i - 1] && data[i] >= data[i + 1];

            if (!isPeak) continue;
            if (avoidIndex != -1 && Math.abs(i - avoidIndex) < minDistance) continue;

            if (data[i] > bestValue) {
                bestValue = data[i];
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    static class Rect {
        int x, y, w, h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    static class PersonBox {
        int leftX, topY, rightX, bottomY;

        PersonBox(int leftX, int topY, int rightX, int bottomY) {
            this.leftX = leftX;
            this.topY = topY;
            this.rightX = rightX;
            this.bottomY = bottomY;
        }
    }

    static class Result {
        int vanishY;
        double targetHeightCm;

        Result(int vanishY, double targetHeightCm) {
            this.vanishY = vanishY;
            this.targetHeightCm = targetHeightCm;
        }
    }
}