package pt.ulisboa.tecnico.cnv.server;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.data.DynamoController;
import pt.ulisboa.tecnico.cnv.data.Pair;
import pt.ulisboa.tecnico.cnv.data.SolverData;
import pt.ulisboa.tecnico.cnv.managers.InstancesManager;
import pt.ulisboa.tecnico.cnv.data.InstanceData;
import pt.ulisboa.tecnico.cnv.solver.Solver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class LoadBalancer {


    public static final int WEBSERVER_PORT = 8000;
    public static final long period = 1000L;
    private static final long INSTANCEM_MANAGER_PERIOD = 50000L;
    public static final int MAX_INSTANCES = 20;
    public static final int MIN_INSTANCES = 1;
    public static final int CACHE_MAX_SIZE = 50;
    private static final Collection<TagSpecification>  tags = new ArrayList<>();
    private static final long LIFE_CHECK_PERIOD = 10000;

    public static Set<InstanceData> instances;
    static AmazonEC2 ec2;
    private static AmazonCloudWatch cloudWatch;
    public static Map<InstanceData,Integer> tries = new ConcurrentHashMap<>();
    public static Set<InstanceData> availableInstances;
    public static DynamoDBMapper mapper;

    public static List<Pair> cache= new ArrayList<>();

    private static void init() throws Exception {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */

        TagSpecification tag = new TagSpecification();
        tag.withTags( new Tag("WebServer","Server")).setResourceType("instance");
        tags.add(tag);


        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").build();
        cloudWatch = AmazonCloudWatchClientBuilder.standard()
                .withRegion("us-east-1")
                .build();
    }

    public static void main(final String[] args) throws Exception {

        init();
        Map<InstanceData, Boolean> myMap = new ConcurrentHashMap<InstanceData, Boolean>();
        instances =  Collections.newSetFromMap(myMap);
        createInstanceManager();

         myMap = new ConcurrentHashMap<InstanceData, Boolean>();
        availableInstances =  Collections.newSetFromMap(myMap);

        createInstanceFinder();
        final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/climb", new LoadBalancer.LoadBalancerHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        DynamoController.init();
        mapper = new DynamoDBMapper(DynamoController.dynamoDB);
    }

    static class LoadBalancerHandler implements HttpHandler {
        public static int current = 0;
        public void handle(final HttpExchange t) throws IOException {
            InstancesManager manager = InstancesManager.getInstanceManager();
            System.out.println("received request");

            int load = getLoad(t.getRequestURI().getQuery());
            boolean sended=false;
            InstanceData ip = manager.findBestInstance(load);
            try{

            System.out.println("Checking if server is still alive");
            while(!manager.ping(ip)){
                    System.out.println("Server Died\nGetting new Instance");
                    manager.removeLoad(ip,load);
                     ip = manager.findBestInstance(load);
            }
            System.out.println("Sending request to " + ip.publicIP);
            URL url = new URL("http://" + ip.publicIP +":"+LoadBalancer.WEBSERVER_PORT + "/climb?" + t.getRequestURI().getQuery());
            System.out.println("Sending request to: " + ip.publicIP);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            InputStream serverResponse = con.getInputStream();

            final Headers hdrs = t.getResponseHeaders();

            t.sendResponseHeaders(200, con.getContentLength());

            hdrs.add("Content-Type", "image/png");

            hdrs.add("Access-Control-Allow-Origin", "*");
            hdrs.add("Access-Control-Allow-Credentials", "true");
            hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
            hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

            final OutputStream os = t.getResponseBody();

            os.write(getResponse(serverResponse,con.getContentLength()));
            manager.removeLoad(ip,load);
            System.out.println("Receive response from: " + ip.publicIP);
            os.close();
            con.disconnect();
            }catch(IOException ex)
            {
                manager.removeLoad(ip,load);
            }
        }

        private byte[] getResponse(InputStream serverResponse,int size) {
            byte[] response = new byte[size];
            try {
                int i=0;
                while(i<size){
                    i +=  serverResponse.read(response,i,size-i);
                    System.out.println("copied: " +i + " of " + size);
                }

            } catch (IOException e) {
                System.out.println("Something went wrong");
            }finally {
                try {
                    serverResponse.close();
                } catch (IOException e) {
                    //
                }
            }
            return response;
        }

    }



    private static void createInstanceFinder(){
        TimerTask repeatedTask = new TimerTask() {
            public void run() {
                InstancesManager manage = InstancesManager.getInstanceManager();
                try {
                    DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
                    DescribeInstancesRequest request = new DescribeInstancesRequest();
                    List<String> values = new ArrayList<>();
                    values.add("Server");
                    if(manage.count()==0){
                        values.add("ServerC");
                    }
                    DescribeInstancesResult describeInstancesRequest = ec2.describeInstances(request.withFilters(new Filter("tag:WebServer", values)));
                    List<Reservation> reservations = describeInstancesRequest.getReservations();
                    Set<Instance> result = new HashSet<>();
                    for (Reservation reservation : reservations){
                        result.addAll(reservation.getInstances());
                    }
                    if(result.size()>0) {
                        List<String> ids = new ArrayList<>();


                        for (Instance instnc : result) {
                            if(instnc.getState().getName().equals("running")){
                                manage.addInstance(instnc);
                                ids.add(instnc.getInstanceId());
                            }
                        }
                        if(ids.size()>0) {
                            System.out.println("Found "+ ids.size()+ " new server.");
                            CreateTagsRequest request2 = new CreateTagsRequest().withTags(new Tag().withKey("WebServer").withValue("ServerC"));
                            request2.setResources(ids);
                            ec2.createTags(request2);
                            if(manage.getNumber()>0){
                                manage.removeCreation(ids.size());
                            }
                        }
                    }
                    values = new ArrayList<>();
                    values.add("AutoScaler");
                }catch (AmazonServiceException ase) {
                    System.out.println("Caught Exception: " + ase.getMessage());
                    System.out.println("Reponse Status Code: " + ase.getStatusCode());
                    System.out.println("Error Code: " + ase.getErrorCode());
                    System.out.println("Request ID: " + ase.getRequestId());
                }
            }
        };



        Timer timer = new Timer("LookForWebServers");
        timer.schedule(repeatedTask,0L,period);
    }


    public static GetMetricStatisticsResult requestCpuUsage(String id){

        long offsetInMilliseconds = 1000 * 60 * 10 * 5;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");

        instanceDimension.setValue(id);
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                .withNamespace("AWS/EC2")
                .withPeriod(60)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(instanceDimension)
                .withEndTime(new Date());

        return cloudWatch.getMetricStatistics(request);

    }

    private static void createInstanceManager(){
        TimerTask repeatedTask = new TimerTask() {
            @Override
            public void run() {
                InstancesManager.getInstanceManager().cycle();
            }
        };
        Timer timer = new Timer("InstanceManager");
        timer.schedule(repeatedTask,INSTANCEM_MANAGER_PERIOD,INSTANCEM_MANAGER_PERIOD);

        TimerTask lifeTask = new TimerTask() {
            @Override
            public void run() {
                InstancesManager.getInstanceManager().lifecheck();
            }
        };
        Timer timer2 = new Timer("LifeCheck");
        timer2.schedule(lifeTask,LIFE_CHECK_PERIOD,LIFE_CHECK_PERIOD);
    }

    public static void  requestNewInstance(){
        System.out.println("Check if have reached maximum");
        InstancesManager manager = InstancesManager.getInstanceManager();
        if(MAX_INSTANCES> manager.count()){

            try {

                RunInstancesRequest runInstancesRequest =
                        new RunInstancesRequest();

                IamInstanceProfileSpecification iam = new IamInstanceProfileSpecification();
                iam.setArn("arn:aws:iam::933719546089:instance-profile/Full-Access");
                runInstancesRequest.withImageId("ami-0a3c465aba6b429e9")
                        .withInstanceType("t2.micro")
                        .withMinCount(1)
                        .withMaxCount(1)
                        .withKeyName("CNV-lab-AWS")
                        .withSecurityGroups("CNV-ssh+http")
                        .withTagSpecifications(tags).
                        withIamInstanceProfile(iam).
                        withMonitoring(true);

                RunInstancesResult runInstancesResult =
                        ec2.runInstances(runInstancesRequest);


                Instance newInstanceId = runInstancesResult.getReservation().getInstances()
                        .get(0);


            }catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());

            }

        }
    }

    public static void  requestRemoveInstance(String id){

        try {
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(id);
            ec2.terminateInstances(termInstanceReq);
        }catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }

    }

    public static int getLoad(String query) {


        for(Pair pair : cache){
            if(pair.getQuery().equals(query)){
                System.out.println("Cache hit");
                return pair.getData().getCost();
            }
        }


        SolverData data = new SolverData();
        data.setQuery(query);
        DynamoDBQueryExpression<SolverData> queryExpression = new DynamoDBQueryExpression<SolverData>().withHashKeyValues(data);
        System.out.println("Querying dynamo");
        List<SolverData> list = mapper.query(SolverData.class,queryExpression);

        if(list.size()>0) {
            int load =list.get(0).getCost();
            if(cache.size()<CACHE_MAX_SIZE){
                cache.add(new Pair(query,list.get(0)));
            }else{
                cache.remove(0);
                cache.add(new Pair(query,list.get(0)));
            }
            System.out.println("Found One cost" + load);
            if(InstancesManager.MAX_WORKLOAD*InstancesManager.THRESHHOLD<load)
                load = new Double(InstancesManager.MAX_WORKLOAD*InstancesManager.THRESHHOLD).intValue();
           return load;
        }

        String[] args =query.split("&");
        String[] realArgs = new String[args.length];
        for(int i = 0; i<args.length;i++){
            realArgs[i] = args[i].split("=")[1];
        }
        System.out.println("looking for similar requests");
        return getSimilarCost(realArgs);
    }

    private static int getSimilarCost(String[] realArgs) {
        int x0,y0,x1,y1,y,x;
        x0 = Integer.parseInt(realArgs[2]);
        y0 = Integer.parseInt(realArgs[4]);
        x1 = Integer.parseInt(realArgs[3]);
        y1 = Integer.parseInt(realArgs[5]);
        x =Integer.parseInt(realArgs[0]);
        y = Integer.parseInt(realArgs[1]);
        if(x0<x*0.25){
            if(x1<x*0.75){
                x0=0;
                x1=new Double(x*0.50).intValue();
            }else{
                x0=0;
                x1=x;
            }
        }else if(x1>x*0.75){
            if(x0>x*0.25){
                x0=new Double(x*0.50).intValue();
                x1=x;
            }else{
                x0=0;
                x1=x;
            }
        }else{
            x0=0;
            x1=x;
        }

        if(y0<y*0.25){
            if(y1<y*0.75){
                y0=0;
                y1=new Double(y*0.50).intValue();
            }else{
                y0=0;
                y1=x;
            }
        }else if(y1>y*0.75){
            if(y0>y*0.25){
                y0=new Double(y*0.50).intValue();
                y1=y;
            }else{
                y0=0;
                y1=y;
            }
        }else{
            y0=0;
            y1=x;
        }

        System.out.println((((y1-y0)/2)+y0)+" "+((((x1-x0)/2)+x0))+" " +x0+" "+y0+" " +x1+" "+y1+" " +realArgs[8]+" "+realArgs[9] );

        HashMap<String, AttributeValue> attributeValues = new HashMap<String, AttributeValue>();
        attributeValues.put(":x0", new AttributeValue().withN(""+(x0)));
        attributeValues.put(":x1", new AttributeValue().withN(""+(x1)));
        attributeValues.put(":y0", new AttributeValue().withN(""+(y0)));
        attributeValues.put(":y1", new AttributeValue().withN(""+(y1)));
        attributeValues.put(":y", new AttributeValue().withN(""+(((y1-y0)/2)+y0)));
        attributeValues.put(":x", new AttributeValue().withN(""+(((x1-x0)/2)+x0)));
        attributeValues.put(":strategy", new AttributeValue().withS(realArgs[8]));
        attributeValues.put(":image", new AttributeValue().withS(realArgs[9]));
        List<SolverData> list= null;

    DynamoDBScanExpression queryExpression = new DynamoDBScanExpression().
            withFilterExpression("StartX = :x AND StartY = :y AND X0 = :x0 AND Y0 = :y0 AND Y1 = :y1 AND X1 = :x1 AND Strategy = :strategy AND Image = :image").
            withExpressionAttributeValues(attributeValues);
     list = mapper.scan(SolverData.class, queryExpression);

        System.out.println("there are "+list.size() +" similar requests");
        int average = 0;
        for(SolverData sd:list)
        {
            average += sd.getCost();
        }
        if(average>0) {
            System.out.println("Getting Average from similar requests");
            return average/list.size();
        }
        System.out.println("No similar requests");
        return new Double(InstancesManager.MAX_WORKLOAD*InstancesManager.THRESHHOLD).intValue();
    }


}

