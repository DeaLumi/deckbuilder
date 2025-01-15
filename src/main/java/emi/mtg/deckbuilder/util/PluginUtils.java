package emi.mtg.deckbuilder.util;

import emi.mtg.deckbuilder.view.MainApplication;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PluginUtils {
	public static final ClassLoader PLUGIN_CLASS_LOADER;

	static {
		Path jarDir = jarPath(MainApplication.class).getParent();
		ClassLoader tmp;
		try {
			List<URL> urls = new ArrayList<>();

			if (Files.isDirectory(Paths.get("plugins/"))) {
				for (Path path : Files.newDirectoryStream(Paths.get("plugins/"), "*.jar")) {
					urls.add(path.toUri().toURL());
				}
			}

			if (Files.isDirectory(jarDir.resolve("plugins/"))) {
				for (Path path : Files.newDirectoryStream(jarDir.resolve("plugins/"), "*.jar")) {
					urls.add(path.toUri().toURL());
				}
			}

			tmp = new URLClassLoader(urls.toArray(new URL[0]), MainApplication.class.getClassLoader());
		} catch (IOException ioe) {
			ioe.printStackTrace();
			MainApplication.LOG.err("Warning: An IO error occurred while loading plugins...");
			tmp = MainApplication.class.getClassLoader();
		}
		PLUGIN_CLASS_LOADER = tmp;
	}

	public static Path jarPath(Class<?> cls) {
		URL jarUrl = cls.getProtectionDomain().getCodeSource().getLocation();
		Path jarPath;
		try {
			jarPath = Paths.get(jarUrl.toURI()).toAbsolutePath();
		} catch (URISyntaxException urise) {
			jarPath = Paths.get(jarUrl.getPath()).toAbsolutePath();
		}

		return jarPath;
	}

	public static <K, T> Map<K, T> providersMap(Class<T> type, Function<T, K> keyExtractor, Comparator<T> sort, Consumer<T> linkageValidator) {
		Stream<T> stream = StreamSupport.stream(ServiceLoader.load(type, PLUGIN_CLASS_LOADER).spliterator(), false);

		if (linkageValidator != null) {
			stream = stream.filter(provider -> {
				try {
					linkageValidator.accept(provider);
					return true;
				} catch (LinkageError le) {
					new LinkageError(String.format("Outdated plugin: %s\n", jarPath(provider.getClass()).toAbsolutePath()), le).printStackTrace();
					return false;
				}
			});
		}

		if (sort != null) {
			stream = stream.sorted(sort);
		}

		return Collections.unmodifiableMap(stream.collect(Collectors.toMap(
				keyExtractor,
				e -> e,
				(a, b) -> { throw new AssertionError(String.format("Duplicate providers for %s, this should be impossible.", a.getClass())); },
				LinkedHashMap::new
		)));
	}

	public static <T> Map<Class<? extends T>, T> providersMap(Class<T> type, Comparator<T> sort, Consumer<T> linkageValidator) {
		return providersMap(type, p -> (Class<? extends T>) p.getClass(), sort, linkageValidator);
	}

	public static <T> Map<Class<? extends T>, T> providersMap(Class<T> type) {
		return providersMap(type, null, null);
	}

	public static <K, T> Map<K, T> providersMap(Class<T> type, Function<T, K> keyExtractor) {
		return providersMap(type, keyExtractor, null, null);
	}

	public static <T> List<T> providers(Class<T> type, Comparator<T> sort, Consumer<T> linkageValidator) {
		Stream<T> stream = StreamSupport.stream(ServiceLoader.load(type, PLUGIN_CLASS_LOADER).spliterator(), false);

		if (linkageValidator != null) {
			stream = stream.filter(provider -> {
				try {
					linkageValidator.accept(provider);
					return true;
				} catch (LinkageError le) {
					new LinkageError(String.format("Outdated plugin: %s\n", jarPath(provider.getClass()).toAbsolutePath()), le).printStackTrace();
					return false;
				}
			});
		}

		if (sort != null) {
			stream = stream.sorted(sort);
		}

		return Collections.unmodifiableList(stream.collect(Collectors.toList()));
	}

	public static <T> List<T> providers(Class<T> type, Comparator<T> sort) {
		return providers(type, sort, null);
	}

	public static <T> List<T> providers(Class<T> type, Consumer<T> linkageValidator) {
		return providers(type, Comparator.comparing(Objects::toString), linkageValidator);
	}

	public static <T> List<T> providers(Class<T> type) {
		return providers(type, Comparator.comparing(Objects::toString), null);
	}
}
