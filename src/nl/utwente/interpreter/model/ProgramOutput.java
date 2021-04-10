package nl.utwente.interpreter.model;

import java.util.ArrayList;

public class ProgramOutput {


    private ArrayList<Tuple<String,Integer>> outputs = new ArrayList<>();

    public ProgramOutput() {

    }

    public void addToList(Tuple<String, Integer> item) {
        outputs.add(item);
    }

    public void clearList() {
        outputs = new ArrayList<>();
    }

    public ArrayList<Tuple<String, Integer>> getCopyOfList() {
        return (ArrayList<Tuple<String, Integer>>) outputs.clone();
    }
}
