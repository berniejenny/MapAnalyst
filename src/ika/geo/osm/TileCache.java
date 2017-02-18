package ika.geo.osm;

public interface TileCache {

	public Tile getTile(int x, int y, int z);
	public void addTile(Tile tile);
}
