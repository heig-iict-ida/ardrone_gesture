package madsdf.ardrone.utils;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

// A class that can be used to accumulate data to create sliding windows
// with a specified step size
public class WindowAccumulator<T> {
    private int windowSize;
    private int step;
    private Deque<T> buffer = new ArrayDeque<>();

    public WindowAccumulator(int windowSize, int step) {
        this.windowSize = windowSize;
        this.step = step;
    }

    // Add a sample to the accumulator. If this creates a new window,
    // the window is returned (an array of size windowSize). Otherwise,
    // null is returned
    public ArrayList<T> add(T s) {
        checkState(buffer.size() <= windowSize);
        ArrayList<T> ret = null;
        buffer.addLast(s);
        if (buffer.size() == windowSize) {
            ret = Lists.newArrayList(buffer.iterator());
            for (int i = 0; i < step; ++i) {
                buffer.removeFirst();
            }
        }
        return ret;
    }
}
