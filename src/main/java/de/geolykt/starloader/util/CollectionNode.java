package de.geolykt.starloader.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface CollectionNode<E> {

    static class NodeIterator<T> implements Iterator<T> {

        @NotNull
        private final CollectionNode<T> root;
        @Nullable
        private CollectionNode<T> head;
        @Nullable
        private CollectionNode<T> previous;
        @Nullable
        private CollectionNode<T> preprevious;

        public NodeIterator(@NotNull CollectionNode<T> root) {
            this.head = this.root = root;
        }

        public NodeIterator(@NotNull CollectionNode<T> root, @NotNull CollectionNode<T> head) {
            this.root = root;
            this.head = head;
        }

        @Override
        public boolean hasNext() {
            return this.head != null;
        }

        @Override
        public T next() {
            CollectionNode<T> head = this.head;
            if (head == null) {
                throw new NoSuchElementException();
            }
            this.head = head.next();
            this.preprevious = this.previous;
            this.previous = head;
            return head.get();
        }

        @Override
        public void remove() {
            if (this.previous == null) {
                throw new NoSuchElementException("#next not called");
            }
            synchronized (this.root) {
                CollectionNode<T> preprevious = this.preprevious;
                if (preprevious == null) {
                    this.root.setNext(this.head);
                } else {
                    preprevious.setNext(this.head);
                }
            }
        }
    }

    @Nullable
    public CollectionNode<E> next();
    @NotNull
    public E get();
    public void setNext(@Nullable CollectionNode<E> next);
}
