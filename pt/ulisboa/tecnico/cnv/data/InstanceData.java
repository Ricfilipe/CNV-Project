package pt.ulisboa.tecnico.cnv.data;

import com.amazonaws.services.ec2.model.Instance;
import pt.ulisboa.tecnico.cnv.managers.InstancesManager;
import pt.ulisboa.tecnico.cnv.server.LoadBalancer;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

import java.util.*;

public class InstanceData {

    public String publicIP;
    public List<String> requests = new ArrayList<>();
    public String id;
    public boolean alive ;
    public int tries;
    private int workload;
    private boolean flag;
    private Date flagRemovsl;

    public InstanceData(Instance instance) {
        this.publicIP = instance.getPublicIpAddress();
        this.id = instance.getInstanceId();
        this.alive = false;
        this.tries = 0;
        this.workload = 0;
    }

    public int  getWorkload(){
        return workload;
    }

    public void addWorkload(int load){
        this.workload += load;
    }

    public void removeWorkload(int load) {
        this.workload -= load;
    }

    public void flag(){
        this.flag =true;
        Calendar date = Calendar.getInstance();
        date.add(Calendar.MILLISECOND, InstancesManager.TIME_FLAGGED*1000);
        this.flagRemovsl = new Date();
    }

    public void unflag(){
        this.flag =false;
        this.flagRemovsl = null;
    }

    public Date flagDate(){
        return this.flagRemovsl ;
    }


    public boolean isFlagged() {
        return flag;
    }
}
