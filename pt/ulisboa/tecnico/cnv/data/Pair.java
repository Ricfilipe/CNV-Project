package pt.ulisboa.tecnico.cnv.data;

import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

public class Pair {

    private String query;
    private SolverData data;

    public Pair(String query, SolverData data){
        this.query = query;
        this.data = data;
    }

    public SolverData getData() {
        return data;
    }


    public String getQuery() {
        return query;
    }
}
