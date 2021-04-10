package nl.utwente.interpreter.model;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Tree {
    private List<Tree> next;
    private Tree previous;
    private Integer level;
    private String value;
    private String name;
    private DataTypes picture;
    private int pictureSize;
    private int index;
    private int occurs;
    private Tree like;


    public Tree(Integer level, String value, String name) {
        this.name = name;
        this.level = level;
        this.value = value;
        this.next = new ArrayList<>();
        this.previous = null;
        this.picture = null;
        this.index = 1;
        this.occurs = 1;
        this.like = null;
    }

    public Tree deepCopy() {
        Tree copy = new Tree(this.getLevel(), this.getValue(), this.getName());
        if (this.picture != null) {
            copy.setPicture(this.getPicture().toString());
        }
        for (var c: this.getNext()) {
            copy.addNext(c.deepCopy());
        }
        copy.setPictureSize(this.getPictureSize());
        return copy;
    }

    public Map<Tree, Integer> getLeaves(Map<Tree, Integer> result, int childOrder) {
        for (var n: this.getNext()) {
            if (!n.getNext().isEmpty()) {
                n.getLeaves(result, childOrder + 1);
            } else {
                result.put(n, childOrder);
            }
        }

        return result;
    }

    public Boolean identicalNode(Tree tree) {
        return this.getLevel().equals(tree.getLevel()) && this.getName().equals(tree.getName());
    }

    public Boolean isRecord() {
        return !this.next.isEmpty();
    }

    public Tree getLike() {
        return like;
    }

    public void setLike(Tree like) {
        this.like = like;
    }

    public int getOccurs() {
        return occurs;
    }

    public List<Tree> getNodesWithOccurs(List<Tree> result) {
        if (this.getOccurs() > 1) {
            result.add(this);
        }
        for (var c: this.getNext()) {
            c.getNodesWithOccurs(result);
        }
        return result;
    }

    public List<Tree> getNodesWithLikes(List<Tree> result) {
        if (this.getLike() != null) {
            result.add(this);
        }
        for (var c: this.getNext()) {
            c.getNodesWithLikes(result);
        }
        return result;
    }

    public void setOccurs(int occurs) {
        this.occurs = occurs;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public void setPicture(String picture) {
        this.picture = DataTypes.valueOf(picture);
    }

    public DataTypes getPicture() {
        return picture;
    }

    public void addNext(Tree child) {
        next.add(child);
    }

    public void addNext(List<Tree> childs) {
        this.next = Stream.concat(this.next.stream(), childs.stream()).collect(Collectors.toList());
    }

    public void setPrevious(Tree previous) {
        this.previous = previous;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getLevel() {
        return level;
    }

    public String getValue() {
        return value;
    }

    public List<Tree> getNext() {
        return next;
    }

    public void setNext(List<Tree> next) {
        this.next = next;
    }

    public Tree getPrevious() {
        return previous;
    }

    public String getName() {
        return name;
    }

    public int getPictureSize() {
        return pictureSize;
    }

    public void setPictureSize(int pictureSize) {
        this.pictureSize = pictureSize;
    }

    public void resetNode(){
        this.setValue(this.getName().toUpperCase());
        for (var c: this.getNext()) {
            c.resetNode();
        }
    }

    public void print() {
        System.out.println(this.getLevel() + " " + this.getValue() + " " + this.getName() + " " + this.getPicture());
        for (var i: this.getNext()) {
            i.print();
        }
    }

    public List<Tree> getNodes(String node, List<Tree> result) {
        if (this.getName().equals(node)) {
            result.add(this);
        }
        for (var c: this.getNext()) {
            c.getNodes(node, result);
        }
        return result;
    }

    public List<Tree> getNodesFromPath(String path, List<Tree> result) {
        var ls = path.split("\\(")[0].split("OF");
        Collections.reverse(Arrays.asList(ls));
        List<Tree> list = this.getNodes(ls[0], new ArrayList<>());
        int index = 1;
        while (index < ls.length) {
            List<Tree> children = new ArrayList<>();
            for (var l: list) {
                children = Stream.concat(children.stream(), l.getNodes(ls[index], new ArrayList<>()).stream())
                        .collect(Collectors.toList());
            }
            list = children;
            index += 1;
        }
        return list;
    }
}
