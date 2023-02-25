package emi.mtg.deckbuilder.controller;

import emi.mtg.deckbuilder.model.DeckList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;

import java.util.function.Consumer;

public class DeckChanger {
	public static void change(DeckList list, String doText, String undoText, Consumer<DeckList> action, Consumer<DeckList> undo) {
		list.redoStack().clear();
		DeckList.Change change = new DeckList.Change(doText, undoText, action, undo);
		change.redo.accept(list);
		list.undoStack().add(change);
	}

	public static BooleanBinding canUndo(DeckList list) {
		return list.undoStack().emptyProperty().not();
	}

	public static BooleanBinding canRedo(DeckList list) {
		return list.redoStack().emptyProperty().not();
	}

	public static StringBinding undoText(DeckList list) {
		return Bindings.createStringBinding(() -> list.undoStack().isEmpty() ? "Can't Undo" : list.undoStack().get(list.undoStack().size() - 1).undoText, list.undoStack());
	}

	public static StringBinding redoText(DeckList list) {
		return Bindings.createStringBinding(() -> list.redoStack().isEmpty() ? "Can't Redo" : list.redoStack().get(list.redoStack().size() - 1).doText, list.redoStack());
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
