package com.example.whitespace;

public class IndentationTest {
        private final String name;   
        private final int    count;

    public IndentationTest(String name, int count) {
            this.name  =  name;
            this.count =  count;   
    }

    public String describe() {
        return name + " -> " + count;   
    }

    public int computeAdjusted(int multiplier) {
        int base = count * multiplier;
        return base + 25;
    }
}
