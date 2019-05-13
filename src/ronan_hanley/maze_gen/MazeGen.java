package ronan_hanley.maze_gen;

import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.*;

public class MazeGen extends JPanel {
	private static final long serialVersionUID = 1L;
	private int windowWidth;
	private int windowHeight;
	private static final int STARTING_SCALE = 27;
	private double topScale;
	private double zoomSpeed;
	private double zoomAccel;
	private double zoomSpeedLimit;

	private long nsPerUpdate;
	private Maze topMaze;
	private long nextUpdate;
	private boolean saveFrames;
	private long frameCounter;
	private static final String FRAMES_DIR = "./frames/";
	private BufferedImage nextFrame;
	private BufferStrategy bs;
	
	public MazeGen(int width, int height, long nsPerUpdate, boolean saveFrames) {
		this.nsPerUpdate = nsPerUpdate;
		this.saveFrames = saveFrames;

		if (saveFrames) {
			new File(FRAMES_DIR).mkdirs();
		}

		windowWidth = width * STARTING_SCALE;
		windowHeight = height * STARTING_SCALE;

		topScale = windowWidth;
		zoomSpeed = 1.02;
		zoomAccel = 0.000005;
		zoomSpeedLimit = 1.08;

		topMaze = new Maze(width, height, width / 2, height / 2, 2);
		frameCounter = 0;
	}
	
	private void go() {
		Dimension size = new Dimension(windowWidth, windowHeight);
		setMinimumSize(size);
		setMaximumSize(size);
		setPreferredSize(size);

		JFrame frame = new JFrame("Infinite Maze Zoom by Ronan-H");

		frame.getContentPane().setSize(size);
		frame.setLayout(new BorderLayout());
		frame.add(this, BorderLayout.CENTER);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		setIgnoreRepaint(true);
		frame.setResizable(true);

		frame.createBufferStrategy(2);
		bs = frame.getBufferStrategy();

		frame.setVisible(true);

		nextUpdate = System.nanoTime();

		try {
			genMazes();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void genMazes() throws InterruptedException {
		while (true) {
			for (int i = 0; i < 1; i++) {
				topMaze.generateStep();
				if (topMaze.isFinishedGenerating()) {
					break;
				}
			}

			zoomSpeed += zoomAccel;
			if (zoomSpeed > zoomSpeedLimit) {
				zoomSpeed = zoomSpeedLimit;
			}

			pruneMazes();
			repaintAndSleep();
		}
	}

	private long lastTimer = System.currentTimeMillis();
	private int fps = 0;
	private void repaintAndSleep() throws InterruptedException {
		nextFrame = drawFrame();
		render();

		long sleepNs = nextUpdate - System.nanoTime();

		if (sleepNs > 0) {
			Thread.sleep(sleepNs / 1000000, (int) (sleepNs % 1000000));
		}

		nextUpdate += nsPerUpdate;
		fps++;
		if (System.currentTimeMillis() > lastTimer + 1000) {
			System.out.printf("%-5dfps%n", fps);
			lastTimer += 1000;
			fps = 0;
		}

		frameCounter++;
	}

	private BufferedImage drawFrame() {
		BufferedImage frame = new BufferedImage(windowWidth, windowHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) frame.getGraphics();

		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setClip(new Rectangle(0, 0, windowWidth, windowHeight));

		Maze nextMaze = topMaze;
		double scale = topScale;
		for (int i = 0; i < 3 && nextMaze != null; i++) {
			double drawStart = Math.round((windowWidth / 2d) - (scale / 2d));

			int drawStartInt, scaleInt;

			if (i == 0) {
				drawStartInt = (int) drawStart;
				scaleInt = (int) scale;
			}
			else {
				drawStartInt = (int) Math.ceil(drawStart) - 1;
				scaleInt = (int) Math.ceil(scale) + 1;
			}

			g.drawImage(nextMaze.getGridImage(), drawStartInt, drawStartInt, scaleInt, scaleInt, null);

			nextMaze = nextMaze.getSubMaze();
			scale /= topMaze.getWidth();
		}

		topScale *= zoomSpeed;

		g.dispose();

		if (saveFrames) {
			String imageSavePath = FRAMES_DIR + frameCounter + ".png";
			try {
				ImageIO.write(frame, "PNG", new File(imageSavePath));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		return frame;
	}

	public void render() {
		Graphics2D g2 = null;

		do {
			try {
				g2 = (Graphics2D) bs.getDrawGraphics();
				g2.drawImage(nextFrame, 0, 0, null);
			}
			finally {
				g2.dispose();
			}
		} while (bs.contentsLost());

		bs.show();
	}

	private void pruneMazes() {
		while (topScale > windowWidth * topMaze.getWidth()) {
			topMaze = topMaze.getSubMaze();
			topScale /= topMaze.getWidth();

			Maze nextMaze = topMaze;

			while (nextMaze.getSubMaze() != null) {
				nextMaze = nextMaze.getSubMaze();
			}

			nextMaze.setSubMaze(new Maze(topMaze.getWidth(), topMaze.getHeight(), topMaze.getWidth() / 2, topMaze.getHeight() / 2, 0));
		}
	}

	public static void main(String[] args) {
		int mazeSize = 27;
		new MazeGen(mazeSize, mazeSize, 14 * 1000000, false).go();
	}
}
