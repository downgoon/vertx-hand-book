import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class EchoCmd {

	
	public static void main(String[] args) throws Exception {
		
		boolean isQuiet = (args.length > 0 && ("--quiet".equals(args[0]) || "-q".equals(args[0])));
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));
		
		writer.write("welcome to echocmd program\r\n");
		writer.flush();
		try {
			while (true) {
				
				if (! isQuiet) {
					writer.write("input your cmd ('quit' to exit)>");
					writer.flush();
				}
				
				String cmd = reader.readLine();
				if ("quit".equalsIgnoreCase(cmd)) {
					writer.write("\t<echocmd quit, ByeBye");
					writer.flush();
					break;
				} else {
					if (! isQuiet) {
						writer.write("\t<echo your cmd: " + cmd + "\r\n");
					} else {
						writer.write(cmd + "\r\n");
					}
					
					writer.flush();
				}
			}
		} finally {
			if (reader != null) { reader.close(); }
			if (writer != null) { writer.close(); }
		}
		
	}

}
