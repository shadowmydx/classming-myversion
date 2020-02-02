package com.classming.rf;

import com.classming.MutateClass;

import java.io.IOException;
import java.util.List;

public class LookupAction implements Action{
    @Override
    public MutateClass proceedAction(MutateClass target, List<State> total) {
        try {
            return target.lookUpSwitchIteration();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
