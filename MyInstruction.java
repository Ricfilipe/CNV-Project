import BIT.highBIT.*;
import pt.ulisboa.tecnico.cnv.server.LoadBalancer;
import pt.ulisboa.tecnico.cnv.server.WebServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MyInstruction {

    private static PrintStream out = null;


    private static int counter =0;

    private static void init(){

    }

    public static void main(String argv[]) {
        File file_in = new File(argv[0]);

                ClassInfo ci = new ClassInfo(argv[0] );

                // loop through all the routines
                // see java.util.Enumeration for more information on Enumeration class
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                        for (Enumeration b = routine.getInstructionArray().elements(); b.hasMoreElements(); ) {
                            Instruction bb = (Instruction) b.nextElement();
                            int op = bb.getOpcode();
                            if(op == InstructionTable.aload ){
                                System.out.println(ci.getSourceFileName());
                                bb.addBefore("MyInstruction", "count", new Integer(op ));
                            }

                    }

                }
                ci.write(argv[0] );
            }




    public static synchronized void count(int i) {
        Long id = new Long(Thread.currentThread().getId());
        if(WebServer.store.get(id)==null){
            WebServer.store.put(id,new Integer(1));
            return;
        }
        WebServer.store.put(id,new Integer((Integer) WebServer.store.get(id)+1));
    }

}
