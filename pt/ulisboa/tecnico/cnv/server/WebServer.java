package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.data.DynamoController;
import pt.ulisboa.tecnico.cnv.data.SolverData;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

public class WebServer {

	public static ConcurrentHashMap store= new ConcurrentHashMap<>();
	public static DynamoDBMapper mapper;

	private static void init() throws Exception {
		DynamoController.init();
		mapper = new DynamoDBMapper(DynamoController.dynamoDB);
	}


	public static void main(final String[] args) throws Exception {

		init();

		//final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/climb", new MyHandler());

		server.createContext("/ping", new PingHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
	}

	static class MyHandler implements HttpHandler {
		public static int id =0;
		@Override
		public void handle(final HttpExchange t) throws IOException {

			int myID = getID();
			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			/*
			for(String p: params) {
				System.out.println(p);
			}
			*/

			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);

				/*
				System.out.println("splitParam[0]: " + splitParam[0]);
				System.out.println("splitParam[1]: " + splitParam[1]);
				*/
			}

			newArgs.add("-d");

			// Store from ArrayList into regular String[].
			final String[] args = new String[newArgs.size()];
			int i = 0;
			for(String arg: newArgs) {
				args[i] = arg;
				i++;
			}

			/*
			for(String ar : args) {
				System.out.println("ar: " + ar);
			} */

			SolverArgumentParser ap = null;
			try {
				// Get user-provided flags.
				ap = new SolverArgumentParser(args);
			}
			catch(Exception e) {
				System.out.println(e);
				return;
			}

			System.out.println("> Finished parsing args.");

			// Create solver instance from factory.
			final Solver s = SolverFactory.getInstance().makeSolver(ap);



			// Write figure file to disk.
			File responseFile = null;
			try {

				final BufferedImage outputImg = s.solveImage();

				final String outPath = ap.getOutputDirectory();

				final String imageName = s.toString();

				if(ap.isDebugging()) {
					System.out.println("> Image name: " + imageName);
				}

				final Path imagePathPNG = Paths.get(outPath, imageName);
				ImageIO.write(outputImg, "png", imagePathPNG.toFile());

				responseFile = imagePathPNG.toFile();

			} catch (final FileNotFoundException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}



			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();
			System.out.println(responseFile.length());
			t.sendResponseHeaders(200, responseFile.length());

			hdrs.add("Content-Type", "image/png");

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

			final OutputStream os = t.getResponseBody();
			Files.copy(responseFile.toPath(), os);


			os.close();
			System.out.println("> Sent response to " + t.getRemoteAddress().toString());


			SolverData data = new SolverData();
			Long number = new Long(Thread.currentThread().getId());
			System.out.println("Sending to  DynamoDB "+(Integer)store.get(number));
			data.setCost((Integer)store.get(number));
			data.setStartX(ap.getStartX());
			data.setStartY(ap.getStartY());
			data.setInputImage(ap.getInputImage());
			data.setStrategy(ap.getSolverStrategy().name());
			data.setX0(ap.getX0());
			data.setX1(ap.getX1());
			data.setY0(ap.getY0());
			data.setY1(ap.getY1());
			data.setQuery(query);
			System.out.println("Sending now...");
			mapper.save(data);
			System.out.println("ITEM ADDED");

		}

		private synchronized  int getID(){
			return id++;
		}
	}

	static class PingHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String response = "ping";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

}
