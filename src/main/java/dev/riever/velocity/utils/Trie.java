package dev.riever.velocity.utils;

import java.util.HashMap;

public class Trie<T> {
    TrieNode<T> root;

    public Trie() {
        this.root = new TrieNode<>();
    }

    public void insert(String word, T value) {
        root.insert(word, value);
    }

    public T search(String word, boolean partial) {
        return root.search(word, partial);
    }
}

final class TrieNode<T> {
    private final HashMap<Character, TrieNode<T>> children;
    private T value;

    public TrieNode() {
        this.children = new HashMap<>();
        this.value = null;
    }

    public void insert(String word, T value) {
        if (word.isEmpty()) {
            this.value = value;
            return;
        }

        char firstChar = word.charAt(0);
        TrieNode<T> child = this.children.computeIfAbsent(firstChar, k -> new TrieNode<>());
        child.insert(word.substring(1), value);
    }

    public boolean isWord() {
        return this.value != null;
    }

    public T search(String word, boolean partial) {
        if (word.isEmpty()) {
            return this.value;
        }
        if (partial && this.value != null) {
            return this.value;
        }
        char firstChar = word.charAt(0);
        TrieNode<T> child = this.children.get(firstChar);
        if (child == null) {
            return null;
        }
        return child.search(word.substring(1), partial);
    }
}
