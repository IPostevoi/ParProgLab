package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ForkJoinSolver
        extends SequentialSolver {

    public ForkJoinSolver(Maze maze) {

        super(maze);
    }

    public ForkJoinSolver(Maze maze, int forkAfter) {

        this(maze);
        this.forkAfter = forkAfter;
        visited = new ConcurrentSkipListSet<>();
        predecessor = new ConcurrentHashMap<>();
        frontier = new ArrayDeque <> ();

        this.isGoalFound = new AtomicBoolean(false);

    }

    public ForkJoinSolver(ForkJoinSolver parent, int nstart) {

        this(parent.maze);
        this.forkAfter = parent.forkAfter;
        this.predecessor = parent.predecessor;
        this.visited = parent.visited;
        this.start = nstart;
        this.isGoalFound = parent.isGoalFound;

    }

    private AtomicBoolean isGoalFound;

    @Override
    public List<Integer> compute() {

        return parallelSearch();
    }

    private List<Integer> parallelSearch() {

        int numSteps = this.forkAfter;
        int player = maze.newPlayer(start);
        frontier.push(start);
        while (!frontier.isEmpty()) {
            int current = frontier.pop();
            if (maze.hasGoal(current)) {
                isGoalFound.set(true);
                maze.move(player, current);
                return pathFromTo(start, current);
            }
            try {
                if (!visited.contains(current) && !isGoalFound.get()) {

                    maze.move(player, current);
                    visited.add(current);

                    for (int nb : maze.neighbors(current)) {

                        frontier.push(nb);
                        if (!visited.contains(nb))
                            predecessor.put(nb, current);
                    }

                    if (maze.neighbors(current).size() > 2) {
                        List<ForkJoinSolver> subTasks =
                                createTasks();
                        for (ForkJoinSolver subTask : subTasks) {
                            List<Integer> subPath = subTask.join();
                            if (subPath != null) {
                                List<Integer> superPath = Stream.concat(pathFromTo(start,current).stream(), subPath.stream())
                                        .collect(Collectors.toList());
                                return superPath;
                            }
                        }
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("Caught the NullPointerException. No more nodes to be searched.");
            }
        }
        return null;
    }

    private List<ForkJoinSolver> createTasks() {

        List<ForkJoinSolver> tasks = new ArrayList<>();
        for (Integer node : frontier) {
            if (!visited.contains(node)) {
                tasks.add(new ForkJoinSolver(this, node));
            }
        }

        for (ForkJoinSolver task : tasks) {
            task.fork();
        }

        return tasks;
    }

    @Override
    protected List<Integer> pathFromTo(int from, int to) {
        List<Integer> path = new LinkedList<>();
        Integer goal = to;
        while (goal != from) {
            path.add(goal);
            goal = predecessor.get(goal);
            if (goal == null)
                return null;
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
}