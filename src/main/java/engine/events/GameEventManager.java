package engine.events;

import java.util.*;
import java.util.function.Consumer;

public class GameEventManager {
    private Map<Class<?>, List<Consumer<GameEvent>>> listeners = new HashMap<>();

    public <T extends GameEvent> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<GameEvent>) listener);
    }

    public void fire(GameEvent event) {
        List<Consumer<GameEvent>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<GameEvent> l : list) l.accept(event);
        }
    }
}