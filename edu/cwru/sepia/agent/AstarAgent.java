package edu.cwru.sepia.agent;
//This is a comment
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class AstarAgent extends Agent {

    List<MapLocation> enemyLocations = new LinkedList<>();
    int delay = 0;
    //for map location class, I defined the cameFrom variable and cost
    //cost here means the g cost of the map location
    class MapLocation
    {
        public int x, y;
        public double cost;
        public MapLocation cameFrom;
        public MapLocation(int x, int y, MapLocation cameFrom, double cost)
        {
            this.x = x;
            this.y = y;
            this.cameFrom = cameFrom;
            this.cost = cost;
        }

        //we overrider the equals method so that the comparison in the lists will be carried out better,
        //it will only check is the x and y position are equal instead of the memory location
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MapLocation location = (MapLocation) o;
            return this.x == location.x &&
                    this.y == location.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    Stack<MapLocation> path;
    int footmanID, townhallID, enemyFootmanID;
    MapLocation nextLoc;

    private long totalPlanTime = 0; // nsecs
    private long totalExecutionTime = 0; //nsecs

    public AstarAgent(int playernum)
    {
        super(playernum);

        System.out.println("Constructed edu.cwru.sepia.agent.AstarAgent");
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        // get the footman location
        List<Integer> unitIDs = newstate.getUnitIds(playernum);

        if(unitIDs.size() == 0)
        {
            System.err.println("No units found!");
            return null;
        }

        footmanID = unitIDs.get(0);

        // double check that this is a footman
        if(!newstate.getUnit(footmanID).getTemplateView().getName().equals("Footman"))
        {
            System.err.println("Footman unit not found");
            return null;
        }

        // find the enemy playernum
        Integer[] playerNums = newstate.getPlayerNumbers();
        int enemyPlayerNum = -1;
        for(Integer playerNum : playerNums)
        {
            if(playerNum != playernum) {
                enemyPlayerNum = playerNum;
                break;
            }
        }

        if(enemyPlayerNum == -1)
        {
            System.err.println("Failed to get enemy playernumber");
            return null;
        }

        // find the townhall ID
        List<Integer> enemyUnitIDs = newstate.getUnitIds(enemyPlayerNum);

        if(enemyUnitIDs.size() == 0)
        {
            System.err.println("Failed to find enemy units");
            return null;
        }

        townhallID = -1;
        enemyFootmanID = -1;
        for(Integer unitID : enemyUnitIDs)
        {
            Unit.UnitView tempUnit = newstate.getUnit(unitID);
            String unitType = tempUnit.getTemplateView().getName().toLowerCase();
            if(unitType.equals("townhall"))
            {
                townhallID = unitID;
            }
            else if(unitType.equals("footman"))
            {
                enemyFootmanID = unitID;
            }
            else
            {
                System.err.println("Unknown unit type");
            }
        }

        if(townhallID == -1) {
            System.err.println("Error: Couldn't find townhall");
            return null;
        }

        long startTime = System.nanoTime();
        path = findPath(newstate);
        totalPlanTime += System.nanoTime() - startTime;

        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        long startTime = System.nanoTime();
        long planTime = 0;

        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        if(shouldReplanPath(newstate, statehistory, path)) {
            long planStartTime = System.nanoTime();
            path = findPath(newstate);
            planTime = System.nanoTime() - planStartTime;
            totalPlanTime += planTime;
        }

        Unit.UnitView footmanUnit = newstate.getUnit(footmanID);

        int footmanX = footmanUnit.getXPosition();
        int footmanY = footmanUnit.getYPosition();

        if(!path.empty() && (nextLoc == null || (footmanX == nextLoc.x && footmanY == nextLoc.y))) {

            // stat moving to the next step in the path
            nextLoc = path.pop();

            System.out.println("Moving to (" + nextLoc.x + ", " + nextLoc.y + ")");
        }

        if(nextLoc != null && (footmanX != nextLoc.x || footmanY != nextLoc.y))
        {
            int xDiff = nextLoc.x - footmanX;
            int yDiff = nextLoc.y - footmanY;

            // figure out the direction the footman needs to move in
            Direction nextDirection = getNextDirection(xDiff, yDiff);

            actions.put(footmanID, Action.createPrimitiveMove(footmanID, nextDirection));
        } else {
            Unit.UnitView townhallUnit = newstate.getUnit(townhallID);

            // if townhall was destroyed on the last turn
            if(townhallUnit == null) {
                terminalStep(newstate, statehistory);
                return actions;
            }

            if(Math.abs(footmanX - townhallUnit.getXPosition()) > 1 ||
                    Math.abs(footmanY - townhallUnit.getYPosition()) > 1)
            {
                System.err.println("Invalid plan. Cannot attack townhall");
                totalExecutionTime += System.nanoTime() - startTime - planTime;
                return actions;
            }
            else {
                System.out.println("Attacking TownHall");
                // if no more movements in the planned path then attack
                actions.put(footmanID, Action.createPrimitiveAttack(footmanID, townhallID));
            }
        }

        totalExecutionTime += System.nanoTime() - startTime - planTime;
        return actions;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {
        System.out.println("Total turns: " + newstate.getTurnNumber());
        System.out.println("Total planning time: " + totalPlanTime/1e9);
        System.out.println("Total execution time: " + totalExecutionTime/1e9);
        System.out.println("Total time: " + (totalExecutionTime + totalPlanTime)/1e9);
    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this method.
     *
     * This method should return true when the path needs to be replanned
     * and false otherwise. This will be necessary on the dynamic map where the
     * footman will move to block your unit.
     * 
     * You can check the position of the enemy footman with the following code:
     * state.getUnit(enemyFootmanID).getXPosition() or .getYPosition().
     * 
     * There are more examples of getting the positions of objects in SEPIA in the findPath method.
     *
     * @param state
     * @param history
     * @param currentPath
     * @return
     */

    //this strategy simply check if the footman is in the way of our path, if it is, return true, if not, return false
    private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath)
    {
        boolean hasEnemyMoved = false;

        int enemyXPosition = Integer.MIN_VALUE;
        int enemyYPosition = Integer.MIN_VALUE;
        try {
            enemyXPosition = state.getUnit(enemyFootmanID).getXPosition();
            enemyYPosition = state.getUnit(enemyFootmanID).getYPosition();

            //enemylocations are added and find the extinct values out of it, if there are more than 2 value
            //we know enemy is moving, so we
            enemyLocations.add(new MapLocation(enemyXPosition,enemyYPosition,null, 0));
            if (enemyLocations.stream().distinct().collect(Collectors.toList()).size() > 1) {
                hasEnemyMoved = true;
                enemyLocations.clear();
                delay = 2;
            }
            if (delay > 0){
                delay --;
                return true;
            }
        }
        catch (NullPointerException e)
        {
            System.out.println("no need to replan since no enemy footman");
        }

        try {
            System.out.println(currentPath.contains(new MapLocation(enemyXPosition, enemyYPosition, null, 0)) || hasEnemyMoved);
            return currentPath.contains(new MapLocation(enemyXPosition, enemyYPosition, null, 0)) || hasEnemyMoved;
        }
        catch (NullPointerException e){
            return false;
        }
    }

    /**
     * This method is implemented for you. You should look at it to see examples of
     * how to find units and resources in Sepia.
     *
     * @param state
     * @return
     */
    private Stack<MapLocation> findPath(State.StateView state)
    {
        Unit.UnitView townhallUnit = state.getUnit(townhallID);
        Unit.UnitView footmanUnit = state.getUnit(footmanID);

        MapLocation startLoc = new MapLocation(footmanUnit.getXPosition(), footmanUnit.getYPosition(), null, 0);

        MapLocation goalLoc = new MapLocation(townhallUnit.getXPosition(), townhallUnit.getYPosition(), null, 0);

        MapLocation footmanLoc = null;
        if(enemyFootmanID != -1) {
            Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
            footmanLoc = new MapLocation(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition(), null, 0);
        }

        // get resource locations
        List<Integer> resourceIDs = state.getAllResourceIds();
        Set<MapLocation> resourceLocations = new HashSet<MapLocation>();
        for(Integer resourceID : resourceIDs)
        {
            ResourceNode.ResourceView resource = state.getResourceNode(resourceID);

            resourceLocations.add(new MapLocation(resource.getXPosition(), resource.getYPosition(), null, 0));
        }

        return AstarSearch(startLoc, goalLoc, state.getXExtent(), state.getYExtent(), footmanLoc, resourceLocations);
    }
    /**
     * This is the method you will implement for the assignment. Your implementation
     * will use the A* algorithm to compute the optimum path from the start position to
     * a position adjacent to the goal position.
     *
     * Therefore your you need to find some possible adjacent steps which are in range 
     * and are not trees or the enemy footman.
     * Hint: Set<MapLocation> resourceLocations contains the locations of trees
     *
     * You will return a Stack of positions with the top of the stack being the first space to move to
     * and the bottom of the stack being the last space to move to. If there is no path to the townhall
     * then return null from the method and the agent will print a message and do nothing.
     * The code to execute the plan is provided for you in the middleStep method.
     *
     * As an example consider the following simple map
     *
     * F - - - -
     * x x x - x
     * H - - - -
     *
     * F is the footman
     * H is the townhall
     * x's are occupied spaces
     *
     * xExtent would be 5 for this map with valid X coordinates in the range of [0, 4]
     * x=0 is the left most column and x=4 is the right most column
     *
     * yExtent would be 3 for this map with valid Y coordinates in the range of [0, 2]
     * y=0 is the top most row and y=2 is the bottom most row
     *
     * resourceLocations would be {(0,1), (1,1), (2,1), (4,1)}
     *
     * The path would be
     *
     * (1,0)
     * (2,0)
     * (3,1)
     * (2,2)
     * (1,2)
     *
     * Notice how the initial footman position and the townhall position are not included in the path stack
     *
     * @param start Starting position of the footman
     * @param goal MapLocation of the townhall
     * @param xExtent Width of the map
     * @param yExtent Height of the map
     * @param resourceLocations Set of positions occupied by resources
     * @return Stack of positions with top of stack being first move in plan
     */
    private Stack<MapLocation> AstarSearch(MapLocation start, MapLocation goal, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations)
    {
        //these are map size values that doesn't change
        final int MIN_Y = 0;
        final int MIN_X = 0;
        final int MAX_Y = yExtent - 1;
        final int MAX_X = xExtent - 1;

        //this is the approx squareroot of 2, it is related to the heuristic I adapted for this problem

        final double LEAST_DISTANCE_UNIT = 1.5;
        //result is the place to hold the final path
        //yetToVisit works as the openlist, it shows the possible list that currentnode can go
        //visited works as the closList, it holds all the past currentlists
        //yetToVisit holds the starting location at the beginning
        Stack<MapLocation> result = new Stack<>();
        List<MapLocation> yetToVisit = new LinkedList<>();
        Stack<MapLocation> visited = new Stack<>();
        yetToVisit.add(start);

        //we will keep iterating until ther's no more location footman can go to
        while (!yetToVisit.isEmpty()){

            //takes the lowest f(n) cost map locations in yetToVisit list
            //and add current location to the visited list
            //children location is the possible location for current location to go
            MapLocation currentLocation = lowestCostSolution(yetToVisit, goal);
            visited.push(currentLocation);
            List<MapLocation> childrenLocations = availableLocation(enemyFootmanLoc,currentLocation,MIN_Y,MIN_X,MAX_Y,MAX_X,resourceLocations);

            //we check every children location
            //if theh location is the goal, we terminate the loop
            //if the children location isn't in the yetToVisit, we add it into yetToVisit
            //if the children location is already in yetToVisit,
            //and that path to this children location has higher g(n) cost,
            //we reconnect this location's cameFrom to current location
            for (MapLocation location: childrenLocations){
                if (location.equals(goal)) {
                    yetToVisit.clear();
                    break;
                }
                if (visited.contains(location))
                    continue;
                if (!yetToVisit.contains(location))
                    yetToVisit.add(location);
                else
                    if (location.cost < yetToVisit.stream()
                            .filter(loc -> loc.equals(location))
                            .collect(Collectors.toList())
                            .get(0).cost)
                        yetToVisit.stream()
                                .filter(loc -> loc.equals(location))
                                .collect(Collectors.toList())
                                .get(0).cameFrom = currentLocation;
            }
            //after every children is checked, remove the currentlocation from yetToVisit
            yetToVisit.remove(currentLocation);
        }

        MapLocation resultPush = visited.peek();
        if (hCost(resultPush.x, goal.x, resultPush.y, goal.y) > LEAST_DISTANCE_UNIT) {
            System.out.println("there's no way to get to the town hall");
            return null;
        }
        while (!resultPush.equals(start)){
            result.push(resultPush);
            resultPush = resultPush.cameFrom;
        }

        return result;

    }

    //this method simply recieve some MapLocation and return the one with the lowest f(n) = g(n) + h(n) cost
    private MapLocation lowestCostSolution(Collection<MapLocation> list, MapLocation goal) {
        MapLocation result = new MapLocation(Integer.MAX_VALUE, Integer.MIN_VALUE, null, Double.MAX_VALUE);
        for (MapLocation location: list) {
            if (hCost(location.x, goal.x, location.y, goal.y) + location.cost < hCost(result.x, goal.x, result.y, goal.y) + result.cost)
                result = location;
        }

        return result;
    }


    //methods for debugging purpose
    private void debugPosition(MapLocation tested){
        /*for (MapLocation location: tested) {
            System.out.println("x: " + location.x + "," + "y: " +location.y);
        }*/
        System.out.println("x: " + tested.x + "," + "y: " +tested.y);
    }

    private void debugStack (Stack<MapLocation> stack){
        while (!stack.isEmpty()){
            MapLocation print = stack.pop();
            System.out.println("x: " + print.x + "," + "y: " +print.y);
        }
    }
    private void debugList(Collection<MapLocation> tested){
        for (MapLocation location: tested) {
            System.out.print("start..." + "x: " + location.x + "," + "y: " +location.y + "came from: ");
            if(location.cameFrom != null) {
                debugPosition(location.cameFrom);
            }
        }
    }

    //this method return a list of Maplocations that a given state can go
    private List<MapLocation> availableLocation (MapLocation enemyFootmanLoc, MapLocation currentLocation, int MIN_Y, int MIN_X, int MAX_Y, int MAX_X, Set<MapLocation> resourceLocations) {
        List<MapLocation> result = new ArrayList<>();
        int enemyX = Integer.MIN_VALUE;
        int enemyY = Integer.MIN_VALUE;

        //try and catch to make sure it works on the map without a blocker agent
        try {
            enemyX = enemyFootmanLoc.x;
            enemyY = enemyFootmanLoc.y;
        }

        catch (NullPointerException e){
            System.out.println("this map doesn't have a enemy footman");
        }

        //what this loop does is it testify the 9 locations (3x3), centered on the current location, and it checks:
        //1. if this location is not the current locaiton
        //2. if this location is within the bound of the map
        //3. if this location is not a resource location
        //4. if this location is not enemy footman location
        //this is kinda ugly coding, so Ill try to improve it later on
        for (int x = -1; x < 2; x++) {
            for (int y = -1; y < 2; y++) {
                final int temp_x = x;
                final int temp_y = y;
                if (!(x == 0 && y == 0)
                        && (currentLocation.y+y) >= MIN_Y
                        && (currentLocation.y+y) <= MAX_Y
                        && (currentLocation.x+x) >= MIN_X
                        && (currentLocation.x+x) <= MAX_X
                        && !(resourceLocations.stream().anyMatch(location -> (currentLocation.x+temp_x == location.x && currentLocation.y+temp_y == location.y)))
                        && !(currentLocation.x+x == enemyX && currentLocation.y+y == enemyY)) {

                    //after the conditions are all satisfied, create the Maplocation that has currentLocation as its cameFrom,
                    //and cost of currentlocation.cost + 1
                    result.add(new MapLocation(currentLocation.x+x, currentLocation.y+y, currentLocation, currentLocation.cost + 1));
                }
            }
        }
        return result;
    }

    private double hCost(int startX, int endX, int startY, int endY){
        //this is the heuristic we use for the aster search which is the distance of the straight line between two point.
        return Math.hypot(Math.abs(startX - endX), Math.abs(startY - endY));
    }


    @Override
    public int hashCode() {
        return Objects.hash(path, footmanID, townhallID, enemyFootmanID, nextLoc, totalPlanTime, totalExecutionTime);
    }

    /**
     * Primitive actions take a direction (e.g. Direction.NORTH, Direction.NORTHEAST, etc)
     * This converts the difference between the current position and the
     * desired position to a direction.
     *
     * @param xDiff Integer equal to 1, 0 or -1
     * @param yDiff Integer equal to 1, 0 or -1
     * @return A Direction instance (e.g. SOUTHWEST) or null in the case of error
     */
    private Direction getNextDirection(int xDiff, int yDiff) {

        // figure out the direction the footman needs to move in
        if(xDiff == 1 && yDiff == 1)
        {
            return Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            return Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            return Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            return Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            return Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            return Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            return Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            return Direction.NORTHWEST;
        }

        System.err.println("Invalid path. Could not determine direction");
        return null;
    }
}
