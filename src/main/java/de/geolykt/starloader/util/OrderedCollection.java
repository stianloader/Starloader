package de.geolykt.starloader.util;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrderedCollection<E extends Comparable<E>> extends AbstractCollection<E> implements CollectionNode<E> {

    private static class Subnode<T> implements CollectionNode<T> {
        @Nullable
        private CollectionNode<T> next;
        @NotNull
        private final T value;

        public Subnode(@NotNull T value) {
            this.value = value;
        }

        @Override
        @Nullable
        public CollectionNode<T> next() {
            return this.next;
        }

        @Override
        @NotNull
        public T get() {
            return this.value;
        }

        @Override
        public void setNext(@Nullable CollectionNode<T> next) {
            this.next = next;
        }
    }

    @Nullable
    private CollectionNode<E> next;
    @Nullable
    private E value;

    @Override
    public Iterator<E> iterator() {
        return new CollectionNode.NodeIterator<>(this);
    }

    @Override
    public int size() {
        int size = 1;
        for (CollectionNode<E> head = this; head != null; size++, head = head.next());
        return size;
    }

    @Override
    @Nullable
    public CollectionNode<E> next() {
        return this.next;
    }

    @Override
    @NotNull
    public E get() {
        @Nullable
        E value = this.value;
        if (value == null) {
            throw new NoSuchElementException("Collection is empty.");
        }
        return value;
    }

    @Override
    public boolean isEmpty() {
        return this.value == null;
    }

    @Override
    public void setNext(@Nullable CollectionNode<E> next) {
        this.next = next;
    }

    @Override
    public boolean remove(Object o) {
        synchronized (this) {
            for (Iterator<E> it = this.iterator(); it.hasNext();) {
                if (Objects.equals(it.next(), o)) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        synchronized (this) {
            if (this.value == null) {
                this.value = e;
                return true;
            } else if (e.compareTo(get()) < 0) {
                this.next = new Subnode<>(get());
                this.value = e;
                return true;
            }
            CollectionNode<E> next = this.next;
            CollectionNode<E> node = this;
            while (next != null) {
                int compare = e.compareTo(next.get());
                if (compare < 0) {
                    CollectionNode<E> inserted = new Subnode<>(e);
                    inserted.setNext(next);
                    node.setNext(inserted);
                    return true;
                }

                node = next;
                next = node.next();
            }

            node.setNext(new Subnode<>(e));
            return true;
        }
    }
}
