package nez.x.generator;

import java.util.TreeMap;

import nez.Verbose;
import nez.parser.ParserGenerator;

public class GeneratorLoader {
	public final static String GeneratorLoaderPoint = "nez.main.ext.L";
	static TreeMap<String, Class<?>> classMap = new TreeMap<String, Class<?>>();

	public static void regist(String key, Class<?> c) {
		classMap.put(key, c);
	}

	public final static boolean isSupported(String key) {
		if (!classMap.containsKey(key)) {
			try {
				Class.forName(GeneratorLoaderPoint + key);
			} catch (ClassNotFoundException e) {
			}
		}
		return classMap.containsKey(key);
	}

	public final static ParserGenerator load(String key) {
		Class<?> c = classMap.get(key);
		if (c != null) {
			try {
				return (ParserGenerator) c.newInstance();
			} catch (InstantiationException e) {
				Verbose.traceException(e);
			} catch (IllegalAccessException e) {
				Verbose.traceException(e);
			}
		}
		return null;
	}
}
