# Cloud Computing and Virtualization - 2018/19, 2nd Semester
[Course Page: https://fenix.tecnico.ulisboa.pt/disciplinas/AVExe/2018-2019/2-semestre/pagina-inicial](https://fenix.tecnico.ulisboa.pt/disciplinas/AVExe/2018-2019/2-semestre/pagina-inicial)
--------------------------------------------------------------------------------------

The orginazation similiar to the original, it was only 2 new packages 

pt.ulisboa.tecnico.cnv.data:
-DynamoController: responsible for initiating communication with DynamoDB
-SolverData: DynamoDBMapper with information containing information about query and cost
-InstanceData: Class that represents the state of web server
-Pair: Struct that pairs a query to a Solver data
pt.ulisboa.tecnico.cnv.managers:
-InstancesManagers: Singleton responsible for managing all web server 

We also created

pt.ulisboa.tecnico.cnv.server.LoadBalancer: its the load balancer

MyInstruction: class responsible for instrumentation

----------------------------------------------------------------------------------------
Configuration:
On LoadBalancer:
-image(ami)
-zone
-Scalling cycle period
-Lifechek/warm up cyle period
-Max/Min intances 
-WebServer Port
-Cache size

On InstanceManager:
- number of life checks can miss
- Threshold to add new Instance
- Max/Min Workload
- Time flagged until removal

----------------------------------------------------------------------------------------
Install and run:
 its required to have  aws-java-sdk

To run WebServer

/usr/bin/java -XX:-UseSplitVerifier -cp /<PATH>/cnv-project:$CLASSPATH:/<PATH>/aws-java-sdk-1.11.528/lib/aws-java-sdk-1.11.528.jar:/<PATH>/aws-java-sdk-1.11.528/third-party/lib/* pt.ulisboa.tecnico.cnv.server.WebServer

To run LoadBalancer

/usr/bin/java -cp /<PATH>/cnv-project:$CLASSPATH:/<PATH>/aws-java-sdk-1.11.528/lib/aws-java-sdk-1.11.528.jar:/<PATH>/aws-java-sdk-1.11.528/third-party/lib/* pt.ulisboa.tecnico.cnv.server.LoadBalancer






