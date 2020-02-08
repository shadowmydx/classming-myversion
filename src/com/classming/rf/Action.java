package com.classming.rf;

import com.classming.MutateClass;

import java.util.List;

public interface Action {
    public State proceedAction(MutateClass target, List<State> total);
}
