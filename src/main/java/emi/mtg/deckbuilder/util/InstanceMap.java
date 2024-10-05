package emi.mtg.deckbuilder.util;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class InstanceMap<T> extends HashMap<Class<? extends T>, T> {
	public static class TypeAdapterFactory implements com.google.gson.TypeAdapterFactory {
		@Override
		public <X> TypeAdapter<X> create(Gson gson, TypeToken<X> type) {
			if (type.getRawType() != InstanceMap.class) return null;
			assert type.getType() instanceof ParameterizedType : "InstanceMap is parameterized.";

			Type innerType = ((ParameterizedType) type.getType()).getActualTypeArguments()[0];
			assert innerType instanceof Class : "InstanceMap types can't themselves be parameterized (for now, muahahah)";
			Class<?> innerClass = (Class<?>) innerType;

			return (TypeAdapter<X>) create(gson, innerClass);
		}

		public <Y> TypeAdapter<InstanceMap<Y>> create(Gson gson, Class<Y> innerClass) {
			return new TypeAdapter<InstanceMap<Y>>() {
				@Override
				public void write(JsonWriter out, InstanceMap<Y> value) throws IOException {
					out.beginObject();

					for (Map.Entry<Class<? extends Y>, Y> entry : value.entrySet()) {
						out.name(entry.getKey().getCanonicalName());
						((TypeAdapter<Y>) gson.getAdapter(entry.getKey())).write(out, entry.getValue());
					}

					out.endObject();
				}

				@Override
				public InstanceMap<Y> read(JsonReader in) throws IOException {
					InstanceMap<Y> ret = new InstanceMap<>();

					in.beginObject();

					while (in.peek() == JsonToken.NAME) {
						String instanceClassName = in.nextName();
						Class<? extends Y> instanceClass;
						try {
							instanceClass = Class.forName(instanceClassName, false, PluginUtils.PLUGIN_CLASS_LOADER).asSubclass(innerClass);
						} catch (ClassNotFoundException e) {
							throw new IOException(e);
						}
						ret.add(((TypeAdapter<Y>) gson.getAdapter(instanceClass)).read(in));
					}

					in.endObject();

					return ret;
				}
			};
		};
	}

	public InstanceMap() {
		super();
	}

	public boolean add(T instance) {
		return put((Class<? extends T>) instance.getClass(), instance) != instance;
	}
}
