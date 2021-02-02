package pt.ulisboa.tecnico.cnv.managers;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.model.Instance;
import pt.ulisboa.tecnico.cnv.data.InstanceData;
import pt.ulisboa.tecnico.cnv.server.LoadBalancer;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InstancesManager {
    private static final int MAX_TRIES_TO_AVAILABLE = 3;
    private static final int NUMBER_OF_TRIES_UNTIL_REMOVE = 5 ;
    private Map<String, InstanceData> idsToInstances = new ConcurrentHashMap<>();

    private static InstancesManager instancesManager = null;
    private static int checkCPUCounter = 0;
    private static final int checkCPUUsage = 3;
    private static int current = 0;
    private Map<Long, Integer> queue = new ConcurrentHashMap<>();
    private final static int MIN_WORKLOAD = 2400000;
    public final static int MAX_WORKLOAD = 12000000;
    public final static int TIME_FLAGGED = 60; //sec
    public final static double THRESHHOLD = 0.8;
    private List<InstanceData> detected = new ArrayList<>();
    private List<InstanceData> unavailable = new ArrayList<>();
    public List<InstanceData> toRemove = new ArrayList<>();

    private List<InstanceData> flaggedInstances = new ArrayList<>();

    private int numberCreating = 0;

    private InstancesManager() {
    }

    public int count(){
        return idsToInstances.size()+detected.size();
    }

    public static InstancesManager getInstanceManager() {
        if (instancesManager == null) {
            instancesManager = new InstancesManager();
        }
        return instancesManager;
    }

    public void addInstance(Instance instance) {
        System.out.println("Adding " + instance.getInstanceId());
        InstanceData instanceData = new InstanceData(instance);
        detected.add(instanceData);
    }

    public void cycle(){

        if(count()==0){
            addInstance();
        }

        checkCPUCounter++;
        checkQueue();
        terminateFlagged();
        if (checkCPUCounter == checkCPUUsage) {

            System.out.println("Checking CPU of servers...");
            Double average  = getWebServersCpuUtilization();

            System.out.println("Workload Average is " + average + ".");

            if (average <= MIN_WORKLOAD) {
                System.out.println("Workload low, asking to remove an instance");
                removeInstance();
            }
            if (average >= MAX_WORKLOAD * THRESHHOLD) {
                System.out.println("Workload high, asking to add an instance");
                addInstance();
            }

            checkCPUCounter = 0;
        }
    }

    private void checkQueue() {
        int max =0;
        int loadInQueue = 0;
        List<Integer> loads = new ArrayList<>();
        loads.addAll(queue.values());
        for(int load:loads) {
            loadInQueue += load;
        }
        max = ((loadInQueue+MAX_WORKLOAD-1)/MAX_WORKLOAD) - numberCreating - detected.size();
        for(int i = 0 ; i<max;i++){
            System.out.println("Creating");
            addInstance();
        }
    }

    private synchronized void terminateFlagged() {

        List<InstanceData> list = new ArrayList<>(flaggedInstances);
        for (InstanceData instc: list) {
            if(new Date().after(instc.flagDate()) || instc.getWorkload() == 0){
                idsToInstances.remove(instc.publicIP);
                toRemove.add(instc);

                flaggedInstances.remove(instc);
            }

        }
        System.out.println("Removing "+ toRemove.size()+" instances");
        removeInstances();

    }


    private double getWebServersCpuUtilization() {

        int totalLoad = 0;

        List<InstanceData> list = new ArrayList(idsToInstances.values());
        int numdect =  detected.size();
        for (InstanceData instance : list ) {
            if(!instance.isFlagged()) {
                totalLoad += instance.getWorkload();
            }
        }

        return totalLoad/(list.size() + numdect - flaggedInstances.size()+ numberCreating +0.0);
    }


    public  InstanceData findBestInstance(int load) {
        InstanceData result = null;
        while(result == null){
            try {
                result = findLeastUsed(load);
                if(result==null) {
                    queue.put(Thread.currentThread().getId(),load);
                    System.out.println("Adding "+Thread.currentThread().getId()+" to queue");
                    Thread.sleep(10000L);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        queue.remove(Thread.currentThread().getId());
        System.out.println(result.publicIP +" workload is -> " +result.getWorkload());
        return result;
    }

    private void removeInstance(){
        if(count()-flaggedInstances.size()>LoadBalancer.MIN_INSTANCES){
            InstanceData data = findLeastUsed(0);
            data.flag();
            flaggedInstances.add(data);
            System.out.println("Flagged an instance");
        }
    }

    public void removeLoad(InstanceData ip, int load){
        synchronized (ip) {
            ip.removeWorkload(load);
        }
        System.out.println("RemoveLoad");
    }

    private void addInstance(){

        if(flaggedInstances.size()>0){
            flaggedInstances.remove(0).unflag();
        }

        if(count()<LoadBalancer.MAX_INSTANCES) {
            addCreation();
            TimerTask singleTask = new TimerTask() {
                @Override
                public void run() {
                    LoadBalancer.requestNewInstance();
                }
            };
            Timer timer = new Timer("AddInstance");
            timer.schedule(singleTask, 0);
        }
    }


    public  synchronized InstanceData findLeastUsed( int load){
        List<InstanceData> list = new ArrayList<>( idsToInstances.values());
        System.out.println("There are " + idsToInstances.size() + " alive right now");
        InstanceData result = null;
        int bestWork = Integer.MAX_VALUE;
        for(InstanceData data : list){

            if(data.getWorkload()<bestWork && !data.isFlagged()){
                result = data;
                bestWork = data.getWorkload();
            }
        }
        if(result == null){
            System.out.println("There no instances available");
            return null;
        }
        if(result.getWorkload()+ load > MAX_WORKLOAD){
            return null;
        }
        synchronized (result) {
            result.addWorkload(load);
        }
        return result;
    }

    public synchronized void addCreation(){
        numberCreating++;
    }

    public synchronized void removeCreation(int size){
        this.numberCreating = this.numberCreating - size;
        if(this.numberCreating<0)
            this.numberCreating =0;
    }

    public int getNumber(){
        return numberCreating;
    }

    public void removeInstances(){
        TimerTask singleTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println( InstancesManager.getInstanceManager().toRemove.size());
                for (InstanceData data : InstancesManager.getInstanceManager().toRemove) {
                    System.out.println("Removing "+ data.id);
                    LoadBalancer.requestRemoveInstance(data.id);
                }
                InstancesManager.getInstanceManager().toRemove.clear();
            }
        };
        Timer timer = new Timer("RemoveInstances");
        timer.schedule(singleTask,0);
    }


    public void lifecheck(){
        List<InstanceData> allServers = new ArrayList<>();
        allServers.addAll(detected);
        allServers.addAll(unavailable);
        int available = idsToInstances.size();

        for (int i = 0 ; i<allServers.size();i++) {
            InstanceData data = allServers.get(i);
            try{
                URL url =  new URL("http://" + data.publicIP +":"+LoadBalancer.WEBSERVER_PORT + "/ping");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(2000);
                con.connect();
                con.getInputStream().read();
                con.disconnect();
                data.tries = 0;
                if(detected.contains(data)){
                    detected.remove(data);
                    idsToInstances.put(data.publicIP, data);
                }else{
                    unavailable.remove(data);
                    idsToInstances.put(data.publicIP,data);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                allServers.get(i).tries++;
                if(data.tries++>NUMBER_OF_TRIES_UNTIL_REMOVE){
                    unavailable.remove(data);
                    toRemove.add(data);
                }
            }

        }

        System.out.println("Detected: " + (available+allServers.size())+ " Alive: "+ idsToInstances.size() + " Waiting to Start: "+ detected.size() + " Unavailable: "+ unavailable.size());

    }


    public boolean ping(InstanceData data){
        try {

            URL url = new URL("http://" + data.publicIP + ":" + LoadBalancer.WEBSERVER_PORT + "/ping");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(2000);
            con.connect();
            con.getInputStream().read();
            con.disconnect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            idsToInstances.remove(data.publicIP);
            unavailable.add(data);
            return false;
        }
        return true;
    }
}
