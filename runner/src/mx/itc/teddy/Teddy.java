package mx.itc.teddy;

import java.sql.*;
import java.io.*;
import java.util.*;

public class Teddy {
	static String LANG;
	private static Conexion con;
	private static boolean DEBUG;
	private static boolean FULL_DEBUG ;
	private static final HashMap<String, String> languages;
	static {
		languages = new HashMap<String, String>();
		languages.put("C", ".c");
		languages.put("C++", ".cpp");
		languages.put("C#", ".cs");
		languages.put("Crystal", ".cr");
		languages.put("Elixir", ".ex");
		languages.put("Erlang", ".erl");
		languages.put("Go", ".go");
		languages.put("Java", ".java");
		languages.put("Javascript", ".js");
		languages.put("Perl", ".pl");
		languages.put("PHP", ".php");
		languages.put("Python", ".py");
		languages.put("Ruby", ".rb");
		languages.put("Rust", ".rs");
		languages.put("Swift", ".swift");
	}

	public static void main( String [] args ) throws IOException {
		for (String iarg : args) {
			if (iarg.equals("-h")) {
				System.out.println("--------------------------");
				System.out.println("| Teddy Online Judge      |");
				System.out.println("|                         |");
				System.out.println("| 2010-2015 Alan Gonzalez |");
				System.out.println("--------------------------");
				System.out.println("");
				System.out.println("Usage:");
				System.out.println(" -d       Modo de debug básico");
				System.out.println(" -df      Modo de debug completo");
				System.out.println(" -h       Ayuda");
				System.exit(1);
			}
		}

		DEBUG = false;
		FULL_DEBUG = false;

		for (String iarg : args) {
			if ( iarg.equals("-d") )
				DEBUG = true;

			if ( iarg.equals("-df") )
				FULL_DEBUG = true;
		}

		TeddyLog.logger.info("--------------- Iniciando Teddy Online Judge ---------------");

		// probar que existan directorios y que me pueda conectar a la BD
		testDirs();

		try {
			con = iniciarConexion();
			terminarConexion();
		} catch (Exception e) {
			TeddyLog.logger.error("Imposible iniciar conexión con la BD:" + e);
			System.exit(1);
		}

		TeddyLog.logger.info("Directorios y BD OK, iniciando ejecución.");

		while(true) {
			try {
				run();
				Thread.sleep(3000);
			} catch(Exception e) {
				TeddyLog.logger.error(e);
			}
		}
	}

	private static Conexion iniciarConexion() throws Exception{
		//leer el archivo config.php para sacar los datos de la base de datos
		BufferedReader br = new BufferedReader(new FileReader("/opt/teddy/runner/config.php"));
		String s, tempString, server = null, login = null, password = null, bd = null;

		while ((s = br.readLine()) != null) {
			if (s.indexOf("$TEDDY_DB_SERVER") != -1) {
				server = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"') );
			}

			if (s.indexOf("$TEDDY_DB_USER") != -1) {
				login = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"') );
			}

			if (s.indexOf("$TEDDY_DB_PASS") != -1) {
				password = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"') );
			}

			if (s.indexOf("$TEDDY_DB_NAME") != -1) {
				bd = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"') );
			}
		}

		if (server == null) {
			throw new Exception("Faltan datos (host) en el config.php");
		}

		if (login == null) {
			throw new Exception("Faltan datos (usuario) en el config.php");
		}

		if (password == null) {
			throw new Exception("Faltan datos (password) en el config.php");
		}

		if (bd == null) {
			throw new Exception("Faltan datos (nombre de la bd) en el config.php");
		}

		return new Conexion(server, login, password, bd);
	}

	public static boolean testDirs () {
		String [] neededPaths = {
						"/var/tmp/teddy/work_zone/",
						"/usr/teddy/codigos"
					};

		for (int i = 0; i < neededPaths.length; i++) {
			File f = new File(neededPaths[i]);
			if (!f.canWrite()) {
			    TeddyLog.logger.error("No puedo escribir a " + f);
				System.exit(-1);
				return false;
			}
		}
		return true;
	}

	public static void run() {
		String execID;
		String userID;
		String probID;
		String concursoID;

		try {
			TeddyLog.logger.debug("Conectándose a la base de datos.");
			con = iniciarConexion();
		} catch(Exception e) {
			TeddyLog.logger.error("Error al iniciar:");
			TeddyLog.logger.error(e);
			return;
		}

		// leer la base de datos y revisar si hay runs en estado = "WAITING" ...
		ResultSet rs = con.query("SELECT * FROM Ejecucion WHERE status = 'WAITING' LIMIT 1;");
		TeddyLog.logger.debug("SELECT * FROM Ejecucion WHERE status = 'WAITING' LIMIT 1;");

		try {
			if (!rs.next()) {
				TeddyLog.logger.debug("No hay ejecuciones pendientes...");
				return;
			}

			TeddyLog.logger.info( "-------------------------- RUN --------------------------------------");
			TeddyLog.logger.info( "Total memoria de la maquina virtual : " +  (Runtime.getRuntime().totalMemory() / 1024) + "kb");
			TeddyLog.logger.info( "Hay una ejecución pendiente: " + rs.getString("execID") );

			execID = rs.getString("execID");
			LANG = rs.getString("LANG");
			userID = rs.getString("userID");
			probID = rs.getString("probID");
			concursoID = rs.getString("Concurso");

		} catch (SQLException sqle) {
			TeddyLog.logger.info("Error al contactar la BD:" + sqle);
			return;
		}

		// Crear el nombre del archivo
		String fileName = execID + languages.get(LANG);

		// es un concurso ?
		boolean concurso = !concursoID.equals("-1");

		TeddyLog.logger.info("execID     : " + execID);
		TeddyLog.logger.info("concursoID : " + concursoID);
		TeddyLog.logger.info("probID     : " + probID);
		TeddyLog.logger.info("language   : " + LANG);
		TeddyLog.logger.info("userID     : " + userID);
		TeddyLog.logger.info("fileName   : " + fileName);

		// crear un directorio para trabajar con ese código
		File workingDirectory = new File("/var/tmp/teddy/work_zone/" + execID);
		workingDirectory.setWritable(true);
		workingDirectory.mkdir();

		// crear un objeto File del código fuente que se ha subido en la primer carpeta
		File sourceFile = new File( "/usr/teddy/codigos/" + fileName);
		sourceFile.setWritable(true);

		// crear un objeto File donde se guardara el código fuente para ser compilado dentro de su sub-carpeta
		File workingFile = new File(workingDirectory, fileName);
		try {
			workingFile.createNewFile();
		} catch (IOException ioe) {
			TeddyLog.logger.fatal("Error al escribir en el disco duro archivo " + workingFile);
			TeddyLog.logger.fatal(ioe);
			return;
		}

		// copiar linea por linea el contenido en el archivo del work_zone
		try {
			BufferedReader br = new BufferedReader(new FileReader( sourceFile ));
			PrintWriter pw = new PrintWriter( workingFile );

			String contents = "";
			while ((contents = br.readLine()) != null) {
				// aquí puedo ir revisando linea por linea por código malicioso
				pw.println( contents );
			}

			pw.flush();
			pw.close();

		} catch (FileNotFoundException fnfe) {
			TeddyLog.logger.fatal("No se ha podido leer el archivo de código fuente." );
			TeddyLog.logger.fatal(fnfe);
			con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = "+ execID +" LIMIT 1 ;");
			return;

		} catch (IOException ioe) {
			TeddyLog.logger.info("Error al transcribir el código fuente." + ioe);
			con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = "+ execID +" LIMIT 1 ;");
			return;
		}

		// ok todo va bien, ahora si poner las cosas en la base de datos de todo lo que he hecho
		// ponerlo como que estoy juzgando
		con.update("UPDATE Ejecucion SET status = 'JUDGING' WHERE execID = "+ execID +" LIMIT 1 ;");

		// agregar un nuevo intento a ese problema
		con.update("UPDATE Problema SET intentos = (intentos + 1) WHERE probID = "+ probID +" LIMIT 1 ");

		// agregar un nuevo intento a este usuario
		con.update("UPDATE Usuario SET tried = tried + 1  WHERE userID = '"+ userID +"' LIMIT 1 ;");

		// --------------compile el código fuente-----------------------------------//

		// obvio depende de que voy a compile

		// al constructor se le proporciona la ruta hasta el .codigo_fuente
		Compilador c = new Compilador();
		c.setLang( LANG );
		c.setFile( "/var/tmp/teddy/work_zone/" + execID + "/" + fileName );
		c.setRunId(execID);

		// verificar si compilo bien o no
		if (!c.compile()) {
			TeddyLog.logger.info("COMPILACION FALLIDA");

			// no compilo, actualizar la base de datos
			con.update("UPDATE Ejecucion SET status = 'COMPILACION' WHERE execID = "+ execID +" LIMIT 1 ;");

			// cerrar la conexión a la base
			terminarConexion();

			// salir
			return;
		}

		// brindarle los datos de entrada ahí en la carpeta
		// esos datos están en la base de datos
		String titulo ;
		int tiempoLimite;

		rs = con.query("SELECT titulo, tiempoLimite FROM Problema WHERE probID = " + probID);
		try {
			rs.next();
			titulo  = rs.getString("titulo");
			tiempoLimite = Integer.parseInt ( rs.getString("tiempoLimite") );

		} catch (SQLException sqle) {

			TeddyLog.logger.fatal("Error al contactar la BD.");
			return;
		}

		// generar el archivo de entrada para el programa
		File archivoEntrada = new File(workingDirectory, "data.in");
		try {
			archivoEntrada.createNewFile();
		} catch(IOException ioe) {
			TeddyLog.logger.fatal("Error al escribir el archivo de entrada." + ioe);
			con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = "+ execID +" LIMIT 1 ;");
			return;
		}

		// llenar el contenido del archivo de entrada
		String pathArchivoDeEntrada = "/usr/teddy/casos/" + probID + ".in";
		try {
			BufferedReader br = new BufferedReader(new FileReader(pathArchivoDeEntrada));
			PrintWriter pw = new PrintWriter( archivoEntrada );
			String s = null;

			while ((s = br.readLine()) != null) {
				pw.println(s);
			}

			pw.flush();
			pw.close();

		} catch(IOException ioe) {
			TeddyLog.logger.fatal("Error al transcribir el archivo de entrada: " + pathArchivoDeEntrada);
			TeddyLog.logger.fatal(ioe);

			con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = " + execID + " LIMIT 1 ;");
			return;
		}

		// eliminar el archivo de entrada al terminar el proceso
		archivoEntrada.deleteOnExit();

		// --------------ejecutar lo que salga de la compilación -----------------------------------//
		TeddyLog.logger.info("Ejecutando el código del concursante ...");

		// aquí esta lo bueno, ejecutar el código... sniff
		// por el momento al la clase ejecutar solo le pasaremos
		// el execID y con eso ejecutara el Main que este dentro o el a.out etc
		Ejecutar e = new Ejecutar(execID);

		// decirle que lenguaje es... pudiera ser c, c++, python, etc
		e.setLang(LANG);

		// la clase ejecutar es un hilo
		Thread ejecucion = new Thread(e);

		// comienza el tiempo
		long start = System.currentTimeMillis();

		// iniciar el hilo
		ejecucion.start();

		synchronized(ejecucion) {
			try {
				// esperar hasta el tiempo limite
				ejecucion.wait( tiempoLimite );

			} catch(InterruptedException ie) {
				// ni idea... :s
				con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = " + execID + " LIMIT 1 ;");
				TeddyLog.logger.warn("thread interrumpido");
			}

			// al regresar, si el otro hilo sigue vivo entonces detenerlo
			if (ejecucion.isAlive()) {
				// destruir el proceso... pero... como !
				// ejecucion.stop();
				e.destroyProc();
			}
		}

		// calcular tiempo total
		long tiempoTotal = System.currentTimeMillis() - start;

		// la varibale e.status contiene:
		// 	TIEMPO 		si sobrepaso el limite de tiempo
		// 	JUEZ_ERROR 	si surgió un error interno del juez
		// 	EXCEPTION 	si el programa evaluado arrojo una exception

		TeddyLog.logger.debug("resultado: "+ e.status);

		// revisar distintos casos después de ejecutar el programa
		if (e.status.equals("TIME") ) {
			// no cumplió en el tiempo
			TeddyLog.logger.info("TIEMPO");
			TeddyLog.logger.info("Tu programa fue detenido a los "+tiempoTotal+"ms");

			// guardar el resultado
			con.update("UPDATE Ejecucion SET status = 'TIEMPO', tiempo = "+ tiempoTotal +"  WHERE execID = "+ execID +" LIMIT 1 ;");

			// cerra base de datos
			terminarConexion();
			vaciarCarpeta( execID );

			// salir
			return;
		}

		if ( e.status.equals("EXCEPTION") ) {
			// arrojo una exception
			TeddyLog.logger.info("RUN-TIME ERROR");

			// guardar el resultado
			con.update("UPDATE Ejecucion SET status = 'RUNTIME_ERROR' WHERE execID = "+ execID +" LIMIT 1 ;");

			// cerra base de datos
			terminarConexion();
			vaciarCarpeta( execID );

			// salir
			return;
		}

		if (e.status.equals("JUEZ_ERROR") ) {
			// arrojo una exception
			TeddyLog.logger.error("ERROR INTERNO EN EL JUEZ");

			// guardar el resultado
			con.update("UPDATE Ejecucion SET status = 'ERROR' WHERE execID = "+ execID +" LIMIT 1 ;");

			// cerra base de datos
			terminarConexion();
			vaciarCarpeta( execID );

			// Salir
			return;
		}

		// COMPROBAR SALIDA
		TeddyLog.logger.debug("comprobando salida...");

		// si seguimos hasta acá, entonces ya solo resta compara el resultado
		// del programa con la variable salida
		String salidaTotal = "";

		int flag = 0;
		boolean erroneo = false;

		// leer los contenidos del archivo que genero el programa he ir comparando linea por linea con la respuesta
		try {
			BufferedReader salidaDePrograma = new BufferedReader(new FileReader(new File(workingDirectory, "data.out")));
			BufferedReader salidaCorrecta = new BufferedReader(new FileReader("/usr/teddy/casos/" + probID + ".out"));

			String foo = null;
			String bar = null;
			while(((foo = salidaCorrecta.readLine()) != null) ) {
				if ((bar = salidaDePrograma.readLine()) == null) {
					erroneo = true;
					TeddyLog.logger.debug("Se esperaban mas líneas de respuesta!!!") ;
					break;
				}

				TeddyLog.logger.debug("ESPERADO : >" + foo + "<") ;
				TeddyLog.logger.debug("RESPUESTA: >" + bar + "<") ;

				if (!foo.equals(bar)) {
					erroneo = true;
					TeddyLog.logger.debug("^------ DIFF ------^") ;
				}
			}

			if ((bar = salidaDePrograma.readLine()) != null) {
				if (! bar.trim().equals("")) {
					erroneo = true;
					TeddyLog.logger.debug("Terminé de leer la correcta pero tu programa tiene más líneas") ;
					TeddyLog.logger.debug("->"+bar) ;
				}
			}

		} catch (IOException ioe) {

			TeddyLog.logger.info("NO SALIDA");
			TeddyLog.logger.warn(ioe);

			con.update("UPDATE Ejecucion SET status = 'NO_SALIDA', tiempo = "+ tiempoTotal +"  WHERE execID = "+ execID +" LIMIT 1 ;");
			// Cerrar base de datos
			terminarConexion();
			vaciarCarpeta( execID );

			// Salir
			return;
		}

		TeddyLog.logger.debug("erróneo : " + erroneo);

		if ( !erroneo ) {
			// programa correcto !
			TeddyLog.logger.info("OK");
			TeddyLog.logger.info("El tiempo fue de "+ tiempoTotal +"ms");

			// guardar el resultado de ejecucion
			con.update("UPDATE Ejecucion SET status = 'OK', tiempo = "+ tiempoTotal +"  WHERE execID = "+ execID +" LIMIT 1 ;");

			// darle un credito mas a este chavo solo si no ha resuelto este antes
			// revisar si ya lo ha resolvido
			rs = con.query("SELECT status FROM Ejecucion WHERE (probID = '" + probID +"' AND userID = '" + userID+"')" );
			int aciertos = 0;
			int intentos = 0;

			try {
				while(rs.next()) {
					intentos++;
					// TeddyLog.logger.info("("+ intentos +")->"+rs.getString("status"));

					if ( rs.getString("status").equals("OK") )
						aciertos++;
				}

			} catch(SQLException sqle) {
				TeddyLog.logger.fatal("Error al contactar la BD.");
				return;
			}

			// TeddyLog.logger.info(intentos+ " "+ aciertos);
			// si no es así, entonces sumarle uno

			if ( aciertos == 1 ) {
				con.update("UPDATE Usuario SET solved = solved + 1  WHERE userID = '"+ userID +"' LIMIT 1 ;");

			} else {
				TeddyLog.logger.info("Ya tenias resuelto este problema. Ya haz enviado "+ intentos +" soluciones para este problema. Y "+aciertos+" han sido correctas.");
			}

			// agregar un nuevo acierto al problema
			con.update("UPDATE Problema SET aceptados = (aceptados + 1) WHERE probID = "+ probID +" LIMIT 1 ");
		} else {
			// salida erronea
			TeddyLog.logger.info(" - WRONG - ");
			TeddyLog.logger.info("El programa terminó en "+tiempoTotal+"ms. Pero no produjo la respuesta correcta.");

			// guardar el resultado
			con.update("UPDATE Ejecucion SET status = 'INCORRECTO', tiempo = "+ tiempoTotal +"  WHERE execID = "+ execID +" LIMIT 1 ;");
		}

		// fin, terminar la conexión con la base de datos
		terminarConexion();
		vaciarCarpeta( execID );
	}

	static void vaciarCarpeta(String execID) {
		// vaciar el contenido de la carpeta
		for (String file :  new File("/var/tmp/teddy/work_zone/"+execID).list()) {
			new File( "/var/tmp/teddy/work_zone/"+execID+"/"+file ).delete();
		}
	}

	private static void terminarConexion() {
		// terminar la conexión con la base de datos
		try {
			con.cerrar();
		} catch(Exception e) {
			TeddyLog.logger.fatal("Error al cerrar la conexión con la base de datos.");
			return;
		}
	}
}
