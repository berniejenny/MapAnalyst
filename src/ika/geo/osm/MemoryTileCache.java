package ika.geo.osm;

import java.util.Comparator;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.logging.Logger;

public class MemoryTileCache implements TileCache {

	private static final Logger log = Logger.getLogger(MemoryTileCache.class
			.getName());

	protected int cacheSizeMax = 200;

	/**
	 * Number of tiles after a cache cleanup via {@link #removeOldTiles()};
	 */
	protected int cacheSizeDefault = 100;

	protected Hashtable<String, CacheEntry> hashtable;

	/**
	 * A logical clock that "ticks" every time a tile is retrieved from the
	 * cache
	 */
	protected int currentAccessTime = 0;

	public MemoryTileCache() {
		hashtable = new Hashtable<String, CacheEntry>(200);
	}

	public synchronized void addTile(Tile tile) {
		hashtable.put(tile.getKey(), new CacheEntry(tile, currentAccessTime));
		if (hashtable.size() > cacheSizeMax)
			removeOldTiles();
	}

	public Tile getTile(int x, int y, int z) {
		CacheEntry entry = hashtable.get(Tile.getTileKey(x, y, z));
		if (entry == null)
			return null;
		currentAccessTime++;
		entry.lastAccess = currentAccessTime;
		// We are right before an integer overflow!!
		if (currentAccessTime == Integer.MAX_VALUE)
			removeOldTiles();
		return entry.tile;
	}

	/**
	 * Removes the least recently used tiles and rewrites the
	 * {@link CacheEntry#lastAccess} of all remaining entries (-n to 0).
	 * 
	 * WARNING: While this method is running modifying the {@link #hashtable} is
	 * forbidden! Therefore this method and {@link #addTile(Tile)} are declared
	 * as synchronized.
	 */
	protected synchronized void removeOldTiles() {
		//System.out.println("removeOldTiles()");
		try {
			Set<Map.Entry<String, CacheEntry>> entries = hashtable.entrySet();
			TreeSet<Map.Entry<String, CacheEntry>> sortedEntries;
			// Sort the entries according to their access time
			sortedEntries = new TreeSet<Map.Entry<String, CacheEntry>>(
					new MEComparator());
			sortedEntries.addAll(entries);
			// System.out.println("Tiles in Cache: " + hashtable.size() +
			// " lru=" + currentAccessTime);
			int tilecount = 0;
			for (Map.Entry<String, CacheEntry> entry : sortedEntries) {
				tilecount++;
				if (tilecount < cacheSizeDefault) {
					entry.getValue().lastAccess = -tilecount;
				} else {
					// System.out.println("removing entry :"
					// + entry.getValue().lastAccess);
					entries.remove(entry);
				}
			}
			// We can now safely reset the the logical clock
			currentAccessTime = 1;
			// System.out.println("Tiles in Cache: " + hashtable.size() +
			// " lru=" + currentAccessTime);
		} catch (Exception e) {
			log.severe(e.toString());
		}
	}

	public int getCacheSizeMax() {
		return cacheSizeMax;
	}

	public void setCacheSizeMax(int cacheSizeMax) {
		this.cacheSizeMax = cacheSizeMax;
		this.cacheSizeDefault = cacheSizeMax / 2;
	}

	protected static class CacheEntry implements Comparable<CacheEntry> {
		int lastAccess;
		Tile tile;

		protected CacheEntry(Tile tile, int currentAccessTime) {
			this.tile = tile;
			lastAccess = currentAccessTime;
		}

		public int compareTo(CacheEntry o) {
			if (lastAccess > o.lastAccess)
				return -1;
			else
				return 1;
		}
	}

	protected static class MEComparator implements
			Comparator<Map.Entry<String, CacheEntry>> {

		public int compare(Entry<String, CacheEntry> o1,
				Entry<String, CacheEntry> o2) {
			return o1.getValue().compareTo(o2.getValue());
		}
	}
}
