package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.AstarAgent;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class stores all of the information the agent
 * needs to know about the state of the game. For example this
 * might include things like footmen HP and positions.
 *
 * Add any information or methods you would like to this class,
 * but do not delete or change the signatures of the provided methods.
 */
public class GameState{


    //this is a helper class that simply store the position of a unit
    public class UnitPosition{
        private int x,y;
        private UnitPosition(int x, int y){
            this.x = x;
            this.y = y;
        }

        public int getY() {
            return y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }

        @Override
        public String toString() {
            return "x=" + x + ", y= " + y;
        }
    }

    /**
     * You will implement this constructor. It will
     * extract all of the needed state information from the built in
     * SEPIA state view.
     *
     * You may find the following state methods useful:
     *
     * state.getXExtent() and state.getYExtent(): get the map dimensions
     * state.getAllResourceIDs(): returns the IDs of all of the obstacles in the map
     * state.getResourceNode(int resourceID): Return a ResourceView for the given ID
     *
     * For a given ResourceView you can query the position using
     * resource.getXPosition() and resource.getYPosition()
     * 
     * You can get a list of all the units belonging to a player with the following command:
     * state.getUnitIds(int playerNum): gives a list of all unit IDs beloning to the player.
     * You control player 0, the enemy controls player 1.
     * 
     * In order to see information about a specific unit, you must first get the UnitView
     * corresponding to that unit.
     * state.getUnit(int id): gives the UnitView for a specific unit
     * 
     * With a UnitView you can find information about a given unit
     * unitView.getXPosition() and unitView.getYPosition(): get the current location of this unit
     * unitView.getHP(): get the current health of this unit
     * 
     * SEPIA stores information about unit types inside TemplateView objects.
     * For a given unit type you will need to find statistics from its Template View.
     * unitView.getTemplateView().getRange(): This gives you the attack range
     * unitView.getTemplateView().getBasicAttack(): The amount of damage this unit type deals
     * unitView.getTemplateView().getBaseHealth(): The initial amount of health of this unit type
     *
     * @param state Current state of the episode
     */
    private  int mapX;
    private  int mapY;
    private  List<UnitPosition> obstacles = new LinkedList<>();
    private  List<Integer> archerID = new LinkedList<>();
    private  List<Integer> footmenID = new LinkedList<>();
    private Map<Integer, UnitPosition> unitPositionMap = new HashMap<>();
    private Map<Integer, Integer>unitHPMap =  new HashMap<>();
    private  int archerDamage, footmanDamage;
    private final int ARCHER_RANGE;
    private final int FOOTMAN_RANGE;
    private boolean[][] pathFindingMap;

    //this is the turn variable, 0 represents max node, 1 represents min node
    private int turn;

    //this constructor is used when a game state is first created, it will get the initial value of local fields from the state view
    public GameState(State.StateView state) {
        //get the map size
        mapX = state.getXExtent();
        mapY = state.getYExtent();
        ARCHER_RANGE = state.getUnit(state.getUnitIds(1).get(0)).getTemplateView().getRange();
        FOOTMAN_RANGE = state.getUnit(state.getUnitIds(0).get(0)).getTemplateView().getRange();
        for (Integer obstacleID: state.getAllResourceIds()){
            obstacles.add(new UnitPosition(state.getResourceNode(obstacleID).getXPosition(),state.getResourceNode(obstacleID).getYPosition()));
        }
        footmenID.addAll(state.getUnitIds(0));
        archerID.addAll(state.getUnitIds(1));
        archerDamage = state.getUnit(state.getUnitIds(1).get(0)).getTemplateView().getBasicAttack();
        footmanDamage = state.getUnit(state.getUnitIds(0).get(0)).getTemplateView().getBasicAttack();
        for (Integer id: state.getAllUnitIds()){
            unitPositionMap.put(id, new UnitPosition(state.getUnit(id).getXPosition(),state.getUnit(id).getYPosition()));
            unitHPMap.put(id, state.getUnit(id).getHP());
        }

        this.pathFindingMap = new boolean[mapY][mapX];
        for (int row = 0; row < pathFindingMap.length; row++) {
            for (int col = 0; col < pathFindingMap[row].length; col++) {
                int tempCol = col;
                int tempRow = row;
               if (obstacles.stream().anyMatch(position -> position.getX() == tempCol && position.getY() == tempRow))
                    pathFindingMap[row][col] = true;
                else
                    pathFindingMap[row][col] = false;
            }
        }
        //since when a game state is firstly access, the turn is always player turn, so the turn always starts 0
        turn = 0;
    }

    //this constructor is a copy constructor that is used during alpha-beta search, it takes another gamestate and copy everything of this gamestate
    public GameState(GameState gameState){
        //this is where it adjusts the turn number from 1 to 0
        if (gameState.getTurn() == 1)
            this.setTurn(0);
        else
            this.setTurn(1);
        this.ARCHER_RANGE = gameState.ARCHER_RANGE;
        this.FOOTMAN_RANGE = gameState.FOOTMAN_RANGE;
        this.setMapX(gameState.mapX);
        this.setMapY(gameState.mapY);
        this.setObstacles(gameState.obstacles);
        this.setArcherID(gameState.archerID);
        this.setFootmenID(gameState.footmenID);
        this.setUnitPositionMap(gameState.unitPositionMap);
        this.setUnitHPMap(gameState.unitHPMap);
        this.setArcherDamage(gameState.archerDamage);
        this.setFootmanDamage(gameState.footmanDamage);
        this.setPathFindingMap(gameState.getPathFindingMap());
    }

    // getters and setters for the private fields
    public List<Integer> getFootmenID() {
        return footmenID;
    }

    public List<Integer> getArcherID() {
        return archerID;
    }

    public Map<Integer, Integer> getUnitHPMap() {
        return unitHPMap;
    }

    public Map<Integer, UnitPosition> getUnitPositionMap() {
        return unitPositionMap;
    }

    public int getTurn() {
        return turn;
    }

    public boolean[][] getPathFindingMap() {
        return pathFindingMap;
    }

    public int getMapX() {
        return mapX;
    }

    public int getMapY() {
        return mapY;
    }

    public void setArcherDamage(int archerDamage) {
        this.archerDamage = archerDamage;
    }

    public void setFootmanDamage(int footmanDamage) {
        this.footmanDamage = footmanDamage;
    }

    public void setPathFindingMap(boolean[][] pathFindingMap) {
        this.pathFindingMap = pathFindingMap;
    }

    public void setArcherID(List<Integer> archerID) {
        this.archerID.clear();
        this.archerID.addAll(archerID);
    }

    public void setFootmenID(List<Integer> footmenID) {
        this.footmenID.clear();
        this.footmenID.addAll(footmenID);
    }

    public void setMapX(int mapX) {
        this.mapX = mapX;
    }

    public void setMapY(int mapY) {
        this.mapY = mapY;
    }

    public void setObstacles(List<UnitPosition> obstacles) {
        this.obstacles.clear();
        this.obstacles.addAll(obstacles);
    }

    public void setUnitHPMap(Map<Integer, Integer> unitHPMap) {
        this.unitHPMap.clear();
        this.unitHPMap.putAll(unitHPMap);
    }

    public void setUnitPositionMap(Map<Integer, UnitPosition> unitPositionMap) {
        this.unitPositionMap.clear();
        this.unitPositionMap.putAll(unitPositionMap);
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }



    //these are the method that modifies some of the private fields so the modified state can represent the new gamestate
    public void replaceUnitHPMap(Integer unitID, int newHP){
        unitHPMap.replace(unitID, newHP);
    }

    public void replaceUnitPositionMap(Integer unitID, UnitPosition newPosition){
        unitPositionMap.replace(unitID, newPosition);
    }
    /**
     * You will implement this function.
     *
     * You should use weighted linear combination of features.
     * The features may be primitives from the state (such as hp of a unit)
     * or they may be higher level summaries of information from the state such
     * as distance to a specific location. Come up with whatever features you think
     * are useful and weight them appropriately.
     *
     * It is recommended that you start simple until you have your algorithm working. Then watch
     * your agent play and try to add features that correct mistakes it makes. However, remember that
     * your features should be as fast as possible to compute. If the features are slow then you will be
     * able to do less plys in a turn.
     *
     * Add a good comment about what is in your utility and why you chose those features.
     *
     * @return The weighted linear combination of the features
     */

    //our strat is simple but easy to understand, it only takes the distance to the first archer among all the archer,
    //so footman will try to take out the one archer first by getting close to them.
    //distance here is the distance calculated by the BFS shortest path algorithm
    public double getUtility() {
        if (archerID.isEmpty())
            return 100;
        double archerHealth = 0;
        double distance = -100;
        double footmenHealthLoss = 0;
        for (int archer: archerID){
            archerHealth += getUnitHPMap().get(archer);
        }
        for (int footmen: footmenID){
            footmenHealthLoss += (-getUnitHPMap().get(footmen));
            int startX = getUnitPositionMap().get(footmen).getX();
            int startY = getUnitPositionMap().get(footmen).getY();
            int endX = getUnitPositionMap().get(archerID.get(0)).getX();
            int endY = getUnitPositionMap().get(archerID.get(0)).getY();

            distance += shortestPath(getPathFindingMap(), startX, startY, endX, endY);


        }

        return (-1 * (distance));
    }
    private double straightLineDistance(int start_x, int start_y, int end_x, int end_y){
        return Math.hypot(Math.abs(start_x - end_x), Math.abs(start_y - end_y));
    }
    /**
     * You will implement this function.
     *
     * This will return a list of GameStateChild objects. You will generate all of the possible
     * actions in a step and then determine the resulting game state from that action. These are your GameStateChildren.
     * 
     * It may be useful to be able to create a SEPIA Action. In this assignment you will
     * deal with movement and attacking actions. There are static methods inside the Action
     * class that allow you to create basic actions:
     * Action.createPrimitiveAttack(int attackerID, int targetID): returns an Action where
     * the attacker unit attacks the target unit.
     * Action.createPrimitiveMove(int unitID, Direction dir): returns an Action where the unit
     * moves one space in the specified direction.
     *
     * You may find it useful to iterate over all the different directions in SEPIA. This can
     * be done with the following loop:
     * for(Direction direction : Directions.values())
     *
     * To get the resulting position from a move in that direction you can do the following
     * x += direction.xComponent()
     * y += direction.yComponent()
     * 
     * If you wish to explicitly use a Direction you can use the Direction enum, for example
     * Direction.NORTH or Direction.NORTHEAST.
     * 
     * You can check many of the properties of an Action directly:
     * action.getType(): returns the ActionType of the action
     * action.getUnitID(): returns the ID of the unit performing the Action
     * 
     * ActionType is an enum containing different types of actions. The methods given above
     * create actions of type ActionType.PRIMITIVEATTACK and ActionType.PRIMITIVEMOVE.
     * 
     * For attack actions, you can check the unit that is being attacked. To do this, you
     * must cast the Action as a TargetedAction:
     * ((TargetedAction)action).getTargetID(): returns the ID of the unit being attacked
     * 
     * @return All possible actions and their associated resulting game state
     */
    public List<GameStateChild> getChildren() {
        /**the general idea of this method:
        1.for each footman, get a list of all action this footman can do
        2.for each archer, get a lost of all action this archer can do
        3.for the list of list of actions of footman, find the permutations of these lists
        4.do the number 3 except for the archer
        5, go through the permutations and map them
        6, for each action in those permutation, change the copied current state accordingly
        7.add the new GamestateChild created from the map and modified copy of the current state.
        8.return the result.
        */

        //result is the result list that will be returned
        List<GameStateChild> result = new LinkedList<>();

        List<Collection<Action>> footmenActions = new LinkedList<>();
        List<Collection<Action>> archerActions = new LinkedList<>();
        for (Integer id: footmenID){
            footmenActions.add(legalActions(id));
        }
        for (Integer id: archerID){
            archerActions.add(legalActions(id));
        }

        //here we add the possible actions that the footman can do and then pass in the actions the archers can do
        result.addAll(updatedResults(footmenActions));
        result.addAll(updatedResults(archerActions));

        return result;
    }


    //a helper method that return the list of GameStateChild given a list of list of actions
    public List<GameStateChild> updatedResults(List<Collection<Action>> actionCombs){
        //result is for result storage
        List<GameStateChild> result = new LinkedList<>();
        //permutation is the permutations of this action combs
        Collection<List<Action>> permutation = permutations(actionCombs);

        //for each list of action in permutation(so the possible actions in one ply)
        for (List<Action> actions: permutation){
            Map<Integer,Action> actionMap = new HashMap<>();

            //this makes a copy of the current Gamestate
            GameState temp = new GameState(this);

            //for each action
            for (Action action: actions){
                int unitID = action.getUnitId();
                //map the action
                actionMap.put(action.getUnitId(),action);

                //if the action is a movment, change the position
                if (action.getType().equals(ActionType.PRIMITIVEMOVE)){
                    temp.replaceUnitPositionMap(unitID,
                            new UnitPosition(temp.unitPositionMap.get(unitID).getX() + ((DirectedAction) action).getDirection().xComponent(),
                                    temp.unitPositionMap.get(unitID).getY() + ((DirectedAction) action).getDirection().yComponent()));
                    //System.out.println(temp.unitPositionMap.get(action.getUnitId()));
                }
                //if the aciton is a attack, decrease the health of the target unit,
                //and if the unit is dead after attack, remove everything that is related to this unit.
                else {
                    int currentHP = temp.getUnitHPMap().get(unitID);
                    temp.replaceUnitHPMap(((TargetedAction) action).getTargetId(), (currentHP - (footmenID.contains(unitID) ? footmanDamage : archerDamage)));
                    if (!(temp.unitHPMap.get(((TargetedAction) action).getTargetId()) == null) && temp.unitHPMap.get(((TargetedAction) action).getTargetId()) <= 0){
                        int deadUnitID = ((TargetedAction) action).getTargetId();
                        temp.getUnitPositionMap().remove(deadUnitID);
                        temp.getUnitHPMap().remove(deadUnitID);
                        temp.archerID.removeIf(x -> x == deadUnitID);
                        temp.footmenID.removeIf(x -> x ==deadUnitID);
                    }
                }
            }
            result.add(new GameStateChild(actionMap, temp));
        }

        return result;
    }

    //this method takes a unitview and return all legal directions this unit can go to(it exclude the four corner directions)
    private List<Direction> legalDirection(Integer unitID){
        List<Direction> result = new LinkedList<>();
        for (Direction direction: Direction.values()){
            if (isLegalToMoveTo(unitPositionMap.get(unitID).getX(),unitPositionMap.get(unitID).getY(),direction)
                    && !direction.name().equals("SOUTHEAST")
                    && !direction.name().equals("SOUTHWEST")
                    && !direction.name().equals("NORTHEAST")
                    && !direction.name().equals("NORTHWEST"))
                result.add(direction);
        }
        //debugList(result);
        return result;
    }

    //this method returns if a direction is legal to go given the current location of the unit and the direction this unit is going
    private boolean isLegalToMoveTo(int x, int y, Direction direction){
        int newX = x + direction.xComponent();
        int newY = y + direction.yComponent();

        if (newX >= mapX || newX < 0)
            return false;
        else if (newY >= mapY || newY < 0)
            return false;
        else
            return obstacles.stream().noneMatch(obstacle -> obstacle.getX() == newX && obstacle.getY() == newY);
    }

    //this method returns a list of unitIDs of possible targets the attacker unit can attack
    private List<Integer> legalTargets(Integer attacker, List<Integer> targetsID){
        return targetsID.stream().filter(id ->
                    straightLineDistance(unitPositionMap.get(attacker).getX(),unitPositionMap.get(attacker).getY(),unitPositionMap.get(id).getX(),unitPositionMap.get(id).getY())
                            <= (footmenID.contains(attacker)?FOOTMAN_RANGE:ARCHER_RANGE)).collect(Collectors.toList());
    }

    //this method return a list of actions a unit a do given its unitID
    private List<Action> legalActions(Integer id){
        List<Action> actions = new LinkedList<>();
        for (Direction direction: legalDirection(id)){
            actions.add(Action.createPrimitiveMove(id, direction));
        }
        if (archerID.contains(id)) {
            for (Integer target : legalTargets(id, footmenID)) {
                actions.add(Action.createPrimitiveAttack(id, target));
            }
        }
        else {
            for (Integer target : legalTargets(id, archerID)) {
                actions.add(Action.createPrimitiveAttack(id, target));
            }
        }
        //debugList(actions);
        return actions;
    }


    //this method takes a list of lists, and return the permutation of the elements in those lists
    private Collection<List<Action>> permutations(List<Collection<Action>> collections) {
        if (collections == null || collections.isEmpty()) {
            return Collections.emptyList();
        } else {
            Collection<List<Action>> result = new LinkedList<>();
            findPermutations(collections, result, 0, new LinkedList<>());
            return result;
        }
    }
    private void findPermutations(List<Collection<Action>> inputList, Collection<List<Action>> outputList, int d, List<Action> tempList) {
        if (d == inputList.size()) {
            outputList.add(tempList);
            return;
        }
        Collection<Action> currentCollection = inputList.get(d);
        for (Action currElement : currentCollection) {
            List<Action> copy = new LinkedList<>(tempList);
            copy.add(currElement);
            findPermutations(inputList, outputList, d + 1, copy);
        }
    }

    //debugging methods
    private void debugMap(Map map){
        map.forEach((key, value) -> System.out.println(key + ":" + value));
        System.out.println("-------------");
    }

    private void debugList(List list){
        list.forEach(System.out::println);
        System.out.println("-------------");
    }

    private int shortestPath(boolean[][] map, int startX, int startY, int endX, int endY) {
        // Create a queue for all nodes we will process in breadth-first order.
        // Each node is a data structure that holds the position of the unit.
        Queue<pathFindingNode> queue = new ArrayDeque<>();

        // Matrix for "discovered" fields
        boolean[][] visited = new boolean[mapY + 10][mapX + 10];

        // this is the starting position so we want that to be in visited queue
        visited[startY][startX] = true;
        queue.add(new pathFindingNode(startX, startY, null, 0));

        while (!queue.isEmpty()) {
            pathFindingNode node = queue.poll();
            Queue<PathFindingDirection> result = new LinkedList<>();
            // Go breath-first into each direction
            for (PathFindingDirection dir : PathFindingDirection.values()) {
                int newX = node.x + dir.getDx();
                int newY = node.y + dir.getDy();
                PathFindingDirection newDir = node.initialDir == null ? dir : node.initialDir;

                // target found?
                if (newX == endX && newY == endY) {
                    return node.distance;
                }

                // Is there a path in the direction
                // And has that field not yet been discovered?
                if ( !(newX < 0 || newX >= mapX) && !(newY < 0 || newY >= mapY) && !map[newY][newX] && !visited[newY][newX]){
                    // "Discover" and enqueue that field
                    visited[newY][newX] = true;
                    queue.add(new pathFindingNode(newX, newY, newDir, node.distance + 1));
                }
            }
        }

        return -1;
    }

    //this is a node that holds the basic node on path finding map
    private static class pathFindingNode {
        final int x;
        final int y;
        final PathFindingDirection initialDir;
        int distance;

        public pathFindingNode(int x, int y, PathFindingDirection initialDir,int distance) {
            this.x = x;
            this.y = y;
            this.initialDir = initialDir;
            this.distance = distance;
        }
    }

    //here is a enum to hold the direction the node was coming from
    public enum PathFindingDirection {
        UP(0, -1),
        RIGHT(1, 0),
        DOWN(0, 1),
        LEFT(-1, 0);

        private final int dx;
        private final int dy;

        PathFindingDirection(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public int getDx() {
            return dx;
        }

        public int getDy() {
            return dy;
        }
    }

}
