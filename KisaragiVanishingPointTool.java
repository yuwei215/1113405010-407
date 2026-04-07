import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class KisaragiVanishingPointTool extends JPanel {

    // 你只要改這裡
    private static final String IMAGE_PATH = "C:\\Users\\User\\Desktop\\111\\螢幕擷取畫面 2026-04-07 113530.png";

    private BufferedImage image;

    // 每兩點形成一條線，建議點 4 條線 = 8 個點
    private final List<Point> points = new ArrayList<>();

    // 算出的消失點
    private Point vanishingPoint = null;

    public KisaragiVanishingPointTool() {
        try {
            image = ImageIO.read(new File(IMAGE_PATH));
            if (image == null) {
                throw new IOException("無法解析圖片格式");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "圖片讀取失敗：\n" + e.getMessage() + "\n請確認路徑：\n" + IMAGE_PATH,
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                points.add(e.getPoint());

                // 8 點 = 4 條線
                if (points.size() == 8) {
                    computeVanishingPoint();
                }

                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(image.getWidth(), image.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, null);

        // 標題
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRoundRect(12, 12, 470, 95, 18, 18);
        g2.setColor(Color.WHITE);
        g2.drawString("Kisaragi Station - Vanishing Point Tool", 28, 35);
        g2.drawString("請依序點四條平行線，每條線點兩點，共 8 點", 28, 60);
        g2.drawString("建議點：左鐵軌、右鐵軌、左月台邊、右月台邊", 28, 85);

        // 畫已點的點
        g2.setColor(Color.RED);
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            g2.fillOval(p.x - 4, p.y - 4, 8, 8);
            g2.drawString(String.valueOf(i + 1), p.x + 6, p.y - 6);
        }

        // 每兩點畫一條線
        g2.setStroke(new BasicStroke(2));
        for (int i = 0; i + 1 < points.size(); i += 2) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            g2.setColor(Color.YELLOW);
            drawExtendedLine(g2, p1, p2, getWidth(), getHeight());
        }

        // 畫消失點
        if (vanishingPoint != null) {
            g2.setColor(Color.CYAN);
            g2.fillOval(vanishingPoint.x - 6, vanishingPoint.y - 6, 12, 12);
            g2.drawString("Vanishing Point", vanishingPoint.x + 10, vanishingPoint.y - 10);

            // 畫 horizon line
            g2.setColor(Color.ORANGE);
            g2.drawLine(0, vanishingPoint.y, getWidth(), vanishingPoint.y);
        }
    }

    private void computeVanishingPoint() {
        // 用前兩條線求交點、後兩條線求交點，再平均
        Point p1 = lineIntersection(points.get(0), points.get(1), points.get(2), points.get(3));
        Point p2 = lineIntersection(points.get(4), points.get(5), points.get(6), points.get(7));

        if (p1 != null && p2 != null) {
            int x = (p1.x + p2.x) / 2;
            int y = (p1.y + p2.y) / 2;
            vanishingPoint = new Point(x, y);
        } else if (p1 != null) {
            vanishingPoint = p1;
        } else if (p2 != null) {
            vanishingPoint = p2;
        } else {
            JOptionPane.showMessageDialog(this, "無法計算交點，請重新選點。");
        }
    }

    private Point lineIntersection(Point a1, Point a2, Point b1, Point b2) {
        double x1 = a1.x, y1 = a1.y;
        double x2 = a2.x, y2 = a2.y;
        double x3 = b1.x, y3 = b1.y;
        double x4 = b2.x, y4 = b2.y;

        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

        if (Math.abs(denom) < 1e-9) {
            return null;
        }

        double px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denom;
        double py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denom;

        return new Point((int) Math.round(px), (int) Math.round(py));
    }

    private void drawExtendedLine(Graphics2D g2, Point p1, Point p2, int width, int height) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;

        if (Math.abs(dx) < 1e-9) {
            g2.drawLine(p1.x, 0, p1.x, height);
            return;
        }

        double m = dy / dx;
        double b = p1.y - m * p1.x;

        int xStart = 0;
        int yStart = (int) Math.round(b);

        int xEnd = width;
        int yEnd = (int) Math.round(m * width + b);

        g2.drawLine(xStart, yStart, xEnd, yEnd);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kisaragi Vanishing Point Tool");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new KisaragiVanishingPointTool());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}