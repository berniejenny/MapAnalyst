package convexhull;

/**
 * Type point.
 */
public class CPoint
{
    /** X coordinate */
    public float x;
    
    /** Y coordinate */
    public float y;

    /** sets default coordinates to 0.0 */
    public CPoint()
    {
	x = 0.0f;
	y = 0.0f;
    }

    /** sets coordinates as requested
     *
     * @param x new X coordinate
     * @param y new Y coordinate
     */
    public CPoint(float x, float y)
    {
	this.x = x;
	this.y = y;
    }

    /** compares to another CPoint instance
     *
     * @param p2
     * - the other instance of CPoint
     * @return
     * - returns true if the two instances are the same points,
     *   otherwise returns false
     */
    public boolean isEqual(CPoint p2)
    {
	return (x == p2.x && y == p2.y);
    }

    /** converts the point coordinates to a string of format [x,y]
     *
     * @return String - string representation of the point
     */
    public String toString()
    {
	return "[" + x + "," + y + "]";
    }
}
