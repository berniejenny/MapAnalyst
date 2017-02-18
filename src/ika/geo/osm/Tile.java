package ika.geo.osm;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;

public class Tile {

	protected int xtile;
	protected int ytile;
	protected int zoom;
	protected BufferedImage image;
	protected String key;
	protected boolean loaded = false;
	public static final int WIDTH = 256;
	public static final int HEIGHT = 256;
	public static final int WIDTH_HALF = 128;
	public static final int HEIGHT_HALF = 128;

	public Tile(int xtile, int ytile, int zoom) {
		super();
		this.xtile = xtile;
		this.ytile = ytile;
		this.zoom = zoom;
		this.image = null;
		this.key = getTileKey(xtile, ytile, zoom);
	}

	public Tile(int xtile, int ytile, int zoom, BufferedImage image) {
		this(xtile, ytile, zoom);
		this.image = image;
	}

	public int getXtile() {
		return xtile;
	}

	public int getYtile() {
		return ytile;
	}

	public int getZoom() {
		return zoom;
	}

	public BufferedImage getImage() {
		return image;
	}

	public String getKey() {
		return key;
	}

	public boolean isLoaded() {
		return loaded;
	}

	public Tile getParentTile(TileCache tileCache) {
		if (zoom < 1)
			return null;
		return tileCache.getTile(xtile / 2, ytile / 2, zoom - 1);
	}

	public synchronized void loadTileImage() throws IOException {
		if (loaded)
			return;
		URL url;
		URLConnection urlConn;
		DataInputStream input;
		url = new URL("http://tile.openstreetmap.org/" + zoom + "/" + xtile
				+ "/" + ytile + ".png");
		// System.out.println(url);
		urlConn = url.openConnection();
		// urlConn.setUseCaches(false);
		input = new DataInputStream(urlConn.getInputStream());
		image = ImageIO.read(input);
		input.close();
		loaded = true;
	}

	/**
	 * Paints the tile-image on the {@link Graphics} <code>g</code> at the
	 * position <code>x</code>/<code>y</code>.
	 * 
	 * @param g
	 * @param x
	 *            x-coordinate in <code>g</code>
	 * @param y
	 *            y-coordinate in <code>g</code>
	 */
	public void paint(Graphics g, int x, int y) {
		if (image == null)
			return;
		/*
		 * if (image.getHeight() < OSMMap.TILE_HEIGHT || image.getWidth() <
		 * OSMMap.TILE_WIDTH) { x += (OSMMap.TILE_WIDTH - image.getWidth()) / 2;
		 * y += (OSMMap.TILE_HEIGHT - image.getHeight()) / 2; }
		 */
		g.drawImage(image, x, y, null);
	}

	public void paint(Graphics g, int x, int y, int stretch) {
		if (image == null)
			return;
		int tx = x * stretch;
		int ty = y = stretch;
		g.drawImage(image, x, y, tx, ty, 0, 0, image.getWidth(), image
				.getHeight(), null);
	}

	/**
	 * Paints one-fourth of the tile image {@link Graphics} <code>g</code> at
	 * the position <code>x</code>/<code>y</code>.
	 * 
	 * @param g
	 * @param x
	 * @param y
	 * @param partx
	 *            (0 or 1) selects if the left or right part of tile should be
	 *            painted
	 * @param party
	 *            (0 or 1) selects if the upper or lower part of tile should be
	 *            painted
	 */
	public void parentPaint(Graphics g, int x, int y, int partx, int party) {
		int sx = 0;
		int sy = 0;
		if (partx == 1)
			sx = WIDTH_HALF;
		if (party == 1)
			sy = HEIGHT_HALF;
		g.drawImage(image, x, y, x + WIDTH, y + HEIGHT, sx, sy,
				sx + WIDTH_HALF, sy + HEIGHT_HALF, null);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Tile))
			return false;
		Tile tile = (Tile) obj;
		return (xtile == tile.xtile) && (ytile == tile.ytile)
				&& (zoom == tile.zoom);
	}

	public static String getTileKey(int xtile, int ytile, int zoom) {
		return zoom + "/" + xtile + "/" + ytile;
	}

}