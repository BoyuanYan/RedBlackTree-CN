package rbt;

public class Node {
    Node parent;
    Node left;
    Node right;
    int val;
    boolean isBlack = true;

    Node(){}

    Node(Node p, Node l, Node r, int v, boolean b) {
        parent = p;
        left = l;
        right = r;
        val = v;
        isBlack = b;
    }
}