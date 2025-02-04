package pt.ulisboa.tecnico.cnv.data;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

@DynamoDBTable(tableName = "Metrics")
public class SolverData {
    private String query;
    private Integer x1;
    private Integer x0;
    private Integer y1;
    private Integer y0;
    private Integer startX;
    private Integer startY;
    private String strategy;
    private String inputImage;
    private Integer cost;
    private String id;

    @DynamoDBRangeKey(attributeName = "Id")
    @DynamoDBAutoGeneratedKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDBHashKey(attributeName = "Query")
    public String getQuery(){return query;}
    public void setQuery(String query){this.query = query;}


    @DynamoDBAttribute(attributeName = "StartX")
    public Integer getStartX() { return startX; }
    public void setStartX(Integer startX) { this.startX = startX; }

    @DynamoDBAttribute(attributeName = "StartY")
    public Integer getStartY() { return startY; }
    public void setStartY(Integer startY) { this.startY = startY; }

    @DynamoDBAttribute(attributeName = "Strategy")
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    @DynamoDBAttribute(attributeName = "Image")
    public String getInputImage() { return inputImage; }
    public void setInputImage(String inputImage) { this.inputImage = inputImage; }

    @DynamoDBAttribute(attributeName = "Cost")
    public Integer getCost() { return cost; }
    public void setCost(Integer cost) { this.cost = cost; }

    @DynamoDBAttribute(attributeName = "Y0")
    public Integer getY0() { return y0; }
    public void setY0(Integer y0) { this.y0 = y0; }

    @DynamoDBAttribute(attributeName = "Y1")
    public Integer getY1() { return y1; }
    public void setY1(Integer y1) { this.y1 = y1; }

    @DynamoDBAttribute(attributeName = "X0")
    public Integer getX0() { return x0; }
    public void setX0(Integer x0) { this.x0 = x0; }

    @DynamoDBAttribute(attributeName = "X1")
    public Integer getX1() { return x1; }
    public void setX1(Integer x1) { this.x1 = x1; }
}
