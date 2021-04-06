package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class MinimaxAlphaBeta extends Agent {

    private final int numPlys;

    public MinimaxAlphaBeta(int playernum, String[] args)
    {
        super(playernum);

        if(args.length < 1)
        {
            System.err.println("You must specify the number of plys");
            System.exit(1);
        }

        numPlys = Integer.parseInt(args[0]);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        GameStateChild bestChild = alphaBetaSearch(new GameStateChild(newstate),
                numPlys,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);

        return bestChild.action;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {

    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this.
     *
     * This is the main entry point to the alpha beta search. Refer to the slides, assignment description
     * and book for more information.
     *
     * Try to keep the logic in this function as abstract as possible (i.e. move as much SEPIA specific
     * code into other functions and methods)
     *
     * @param node The action and state to search from
     * @param depth The remaining number of plys under this node
     * @param alpha The current best value for the maximizing node from this node to the root
     * @param beta The current best value for the minimizing node from this node to the root
     * @return The best child of this node with updated values
     */

    public GameStateChild alphaBetaSearch(GameStateChild node, int depth, double alpha, double beta)
    {
        //bestUtility method return the utility value of the child node that agent should go next.
        double result = bestUtility(node, depth, alpha, beta);

        //find the child that has the utility value and return this child of current node
        for (GameStateChild child: node.state.getChildren()){
            if (result == child.state.getUtility()) {
                return child;
            }
        }

        //if it cannot find it it will return a null and print an error message
        System.out.println("no legal next state to go");
        return null;
    }


    //this is the helper method that use minimax alpha beta pruning to get the best utility value of the children
    private double bestUtility(GameStateChild node, int depth, double alpha, double beta){
        if (depth == 0 || isGameOver(node)) {
            //System.out.println("bottom!");
            return node.state.getUtility();
        }

        //orderChildrenWIthHeuristics is used here inorder to have a higher chance of pruning.
        List<GameStateChild> childrenNodes = orderChildrenWithHeuristics(node.state.getChildren());
        if (node.state.getTurn() == 0){

            //max eval and min eval are the local max and local min here, while alpha beta are the global min/max
            double maxEval = Double.NEGATIVE_INFINITY;
            for (GameStateChild child: childrenNodes){
                double eval = bestUtility(child, depth - 1, alpha, beta);
                maxEval = Math.max(maxEval,eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        }
        else {
            double minEval = Double.POSITIVE_INFINITY;
            for (GameStateChild child : childrenNodes) {
                double eval = bestUtility(child, depth - 1, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            return minEval;
        }
    }


    //this simply check if all each all the footman are dead or archer are dead.
    //because in gamestate a unit is deleted from the id list if it has a health below zero
    private boolean isGameOver(GameStateChild node)
    {
        return node.state.getFootmenID().isEmpty() || node.state.getArcherID().isEmpty();
    }
    /**
     * You will implement this.
     *
     * Given a list of children you will order them according to heuristics you make up.
     * See the assignment description for suggestions on heuristics to use when sorting.
     *
     * Use this function inside of your alphaBetaSearch method.
     *
     * Include a good comment about what your heuristics are and why you chose them.
     *
     * @param children
     * @return The list of children sorted by your heuristic.
     */

    //our method use quick sort to sort the children using their getUtility value
    public List<GameStateChild> orderChildrenWithHeuristics(List<GameStateChild> children)
    {
        return quickSort(children,0,children.size() - 1);
    }


    public List<GameStateChild> quickSort(List<GameStateChild> arr, int begin, int end) {

        if (begin < end) {
            int partitionIndex = partition(arr, begin, end);

            quickSort(arr, begin, partitionIndex-1);
            quickSort(arr, partitionIndex+1, end);
        }

        return arr;
    }

   private int partition(List<GameStateChild> arr, int begin, int end) {
        GameStateChild pivot = arr.get(end);
        int i = (begin-1);

        for (int j = begin; j < end; j++) {
            if (arr.get(j).state.getUtility() <= pivot.state.getUtility()) {
                i++;
                Collections.swap(arr, i, j);
            }
        }
        Collections.swap(arr, i + 1, end);
        return i+1;
    }


    //methods for debug purposes
    private void debugNode(GameStateChild node){
        System.out.println(node.state.getUtility());
    }

    private void debugMap(Map map){
        map.forEach((key, value) -> System.out.println(key + ":" + value));
        System.out.println("-------------");
    }
}
