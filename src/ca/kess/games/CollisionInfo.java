package ca.kess.games;

public class CollisionInfo {
    
    //The distance to the collision
    public double Distance;
    //The type of wall for the collision
    public int WallType;
    //The position along the wall of the collision (for texturing purposes).
    public double CollisionPosition;
    //The angle from the player to this collision
    public double Angle;
}
