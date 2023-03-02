package emi.mtg.deckbuilder.controller;

import emi.mtg.deckbuilder.model.DeckList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class DeckChanger {
	private static final Consumer<DeckList> IDENTITY = l -> {};
	private static final Map<DeckList, Consumer<DeckList>> BATCH_DO = new HashMap<>(), BATCH_UNDO = new HashMap<>();

	public static void change(DeckList list, String description, Consumer<DeckList> action, Consumer<DeckList> undo) {
		if (BATCH_DO.containsKey(list) || BATCH_UNDO.containsKey(list)) {
			throw new IllegalStateException(String.format("Attempt to perform single change on %s while batch change is already in progress!", list.name()));
		}

		list.redoStack().clear();
		DeckList.Change change = new DeckList.Change(description, action, undo);
		change.redo.accept(list);
		list.modifiedProperty().set(true);
		list.undoStack().add(change);
	}

	public static void startChangeBatch(DeckList list) {
		if (BATCH_DO.containsKey(list) || BATCH_UNDO.containsKey(list)) {
			throw new IllegalStateException(String.format("Attempt to start batch change on %s while change is already in progress!", list.name()));
		}

		BATCH_DO.put(list, IDENTITY);
		BATCH_UNDO.put(list, IDENTITY);
	}

	public static void addBatchedChange(DeckList list, Consumer<DeckList> action, Consumer<DeckList> undo) {
		if (!BATCH_DO.containsKey(list) || !BATCH_UNDO.containsKey(list)) {
			throw new IllegalStateException(String.format("Attempt to add batch change to %s without change being in progress!", list.name()));
		}

		BATCH_DO.put(list, BATCH_DO.get(list).andThen(action));
		BATCH_UNDO.put(list, BATCH_UNDO.get(list).andThen(undo));
	}

	public static void endChangeBatch(DeckList list, String description) {
		if (!BATCH_DO.containsKey(list) || BATCH_DO.get(list) == IDENTITY || !BATCH_UNDO.containsKey(list) || BATCH_UNDO.get(list) == IDENTITY) {
			throw new IllegalStateException(String.format("Attempt to finalize batch change to %s without change being in progress!", list.name()));
		}

		list.redoStack().clear();
		DeckList.Change change = new DeckList.Change(description, BATCH_DO.get(list), BATCH_UNDO.get(list));
		change.redo.accept(list);
		list.modifiedProperty().set(true);
		list.undoStack().add(change);

		BATCH_DO.remove(list);
		BATCH_UNDO.remove(list);
	}

	public static void abandonChangeBatch(DeckList list) {
		if (!BATCH_DO.containsKey(list) || !BATCH_UNDO.containsKey(list)) {
			throw new IllegalStateException(String.format("Attempt to abandon batch change on %s without change being in progress!", list.name()));
		}

		BATCH_DO.remove(list);
		BATCH_UNDO.remove(list);
	}

	public static BooleanBinding canUndo(DeckList list) {
		return list.undoStack().emptyProperty().not();
	}

	public static BooleanBinding canRedo(DeckList list) {
		return list.redoStack().emptyProperty().not();
	}

	public static StringBinding undoText(DeckList list) {
		return Bindings.createStringBinding(() -> list.undoStack().isEmpty() ? "Can't Undo" : "Undo " + list.undoStack().get(list.undoStack().size() - 1).description, list.undoStack());
	}

	public static StringBinding redoText(DeckList list) {
		return Bindings.createStringBinding(() -> list.redoStack().isEmpty() ? "Can't Redo" : "Redo " + list.redoStack().get(list.redoStack().size() - 1).description, list.redoStack());
	}

	public static void undo(DeckList list) {
		DeckList.Change change = list.undoStack().remove(list.undoStack().size() - 1);
		change.undo.accept(list);
		list.redoStack().add(change);
	}

	public static void redo(DeckList list) {
		DeckList.Change change = list.redoStack().remove(list.redoStack().size() - 1);
		change.redo.accept(list);
		list.undoStack().add(change);
	}
}
