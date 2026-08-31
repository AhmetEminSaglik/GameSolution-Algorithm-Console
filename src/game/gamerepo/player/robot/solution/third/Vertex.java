package game.gamerepo.player.robot.solution.third;

import game.location.Location;
import printarray.StringFormat;
import weights.WeightOfAvailableWay;

import java.util.HashMap;

public class Vertex {
    private Location location= new Location();
    private int availableWayNumber;
    private HashMap<Vertex, Edge> listHashMap = new HashMap<>();


    public Vertex(int x, int y) {
        location.setX(x);
        location.setY(y);
//        this.availableWayNumber = availableWayNumber;
    }

    public void setAvailableWayNumber(int availableWayNumber) {
        this.availableWayNumber = availableWayNumber;
    }

    public void addConnection(Vertex newConnectionVertex) {
        WeightOfAvailableWay weightOfAvailableWay = new WeightOfAvailableWay();
        double weight = weightOfAvailableWay.getWeightOfDirection()[availableWayNumber];
        listHashMap.put(newConnectionVertex, new Edge(weight));

    }

    public Location getLocation() {
        return location;
    }

    public int getAvailableWayNumber() {
        return availableWayNumber;
    }

    public void increaseAvailableWayNumber() {
        availableWayNumber++;
    }

    public void decreaseAvailableWayNumber() {
        availableWayNumber--;
    }

    public HashMap<Vertex, Edge> getListHashMap() {
        return listHashMap;
    }

    public void setListHashMap(HashMap<Vertex, Edge> listHashMap) {
        this.listHashMap = listHashMap;
    }

    @Override
    public String toString() {
        return "Vertex{" +
                "location=" + getName() +
                ", availableWayNumber=" + availableWayNumber +
                ", listHashMap=" + getHashMapInString() +
                '}';
    }

    public String getName() {
        return "[ " + location.getX() + "," + location.getY() + " ]";
    }

    String getHashMapInString() {
        String text = "\nVertext will be shown ---> " + getName() + "\n";
        for (Vertex vertexTmp : listHashMap.keySet()) {
            text += "\nConnectted  Vertex : " + vertexTmp.getName() + " , weight : ";
            listHashMap.get(vertexTmp).getWeight();
        }
        return text;
    }
}
