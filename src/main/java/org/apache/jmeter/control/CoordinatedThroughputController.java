/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.control;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.FloatProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A weighted throughput controller that coordinates with sibling coordinated
 * throughput controllers under the same parent.
 * <p>
 * Sibling controllers share one selection decision per parent iteration, so at
 * most one sibling runs in a given pass.
 */
public class CoordinatedThroughputController
        extends GenericController
        implements Serializable, LoopIterationListener, TestStateListener {
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(CoordinatedThroughputController.class);

    private static final String PERCENT_THROUGHPUT =
            "CoordinatedThroughputController.percentThroughput"; // $NON-NLS-1$

    private static final String BRANCH_ID =
            "CoordinatedThroughputController.branchId"; // $NON-NLS-1$

    private static final Map<GroupKey, BranchGroupState> GROUPS = new HashMap<>();

    private static final ThreadLocal<SourceRegistry> SOURCE_REGISTRY =
            ThreadLocal.withInitial(SourceRegistry::new);

    private transient GroupKey currentGroupKey;

    private transient LoopIterationEvent currentIterationEvent;

    private transient String currentBranchId;

    private transient ControllerTreeSnapshot controllerTreeSnapshot;

    public CoordinatedThroughputController() {
        setPercentThroughput(100.0f);
        setBranchId(UUID.randomUUID().toString());
    }

    public void setPercentThroughput(float percentThroughput) {
        setProperty(new FloatProperty(PERCENT_THROUGHPUT, percentThroughput));
    }

    public void setPercentThroughput(String percentThroughput) {
        setProperty(new StringProperty(PERCENT_THROUGHPUT, percentThroughput));
    }

    public String getPercentThroughput() {
        return getPropertyAsString(PERCENT_THROUGHPUT, "100"); // $NON-NLS-1$
    }

    public float getPercentThroughputAsFloat() {
        JMeterProperty prop = getProperty(PERCENT_THROUGHPUT);
        float retVal = 100.0f;
        if (prop instanceof FloatProperty) {
            retVal = prop.getFloatValue();
        } else {
            String valueString = prop.getStringValue();
            try {
                retVal = Float.parseFloat(valueString);
            } catch (NumberFormatException e) {
                log.warn("Error parsing '{}'", valueString, e);
            }
        }
        return retVal;
    }

    private void setBranchId(String branchId) {
        setProperty(new StringProperty(BRANCH_ID, branchId));
    }

    private String getBranchId() {
        String branchId = getPropertyAsString(BRANCH_ID, ""); // $NON-NLS-1$
        if (branchId.isEmpty()) {
            branchId = UUID.randomUUID().toString();
            setBranchId(branchId);
        }
        return branchId;
    }

    @Override
    public Sampler next() {
        if (currentIterationEvent == null || currentGroupKey == null) {
            return super.next();
        }

        BranchGroupState state = getGroupState(currentGroupKey);
        String selectedBranchId = state.getSelectedBranchId(currentIterationEvent);
        String branchId = currentBranchId;
        if (!branchId.equals(selectedBranchId)) {
            state.branchCompleted(currentIterationEvent, branchId);
            return null;
        }

        Sampler sampler = super.next();
        if (sampler == null) {
            state.branchCompleted(currentIterationEvent, branchId);
        }
        return sampler;
    }

    @Override
    public void iterationStart(LoopIterationEvent iterEvent) {
        restoreControllerTree();
        reInitialize();
        currentGroupKey = GroupKey.from(iterEvent.getSource());
        currentIterationEvent = iterEvent;
        currentBranchId = getGroupState(currentGroupKey)
                .register(iterEvent, getBranchId(), getPercentThroughputAsFloat());
    }

    @Override
    public boolean isDone() {
        // Returning null means skipped or complete for this parent iteration,
        // not permanently removable from the parent controller tree.
        return false;
    }

    @Override
    public Object clone() {
        CoordinatedThroughputController clone = (CoordinatedThroughputController) super.clone();
        clone.currentGroupKey = null;
        clone.currentIterationEvent = null;
        clone.currentBranchId = null;
        clone.controllerTreeSnapshot = null;
        return clone;
    }

    @Override
    public void testStarted() {
        synchronized (GROUPS) {
            GROUPS.clear();
        }
        SOURCE_REGISTRY.remove();
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded() {
        testStarted();
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        currentGroupKey = null;
        currentIterationEvent = null;
        currentBranchId = null;
        controllerTreeSnapshot = null;
        return this;
    }

    private void restoreControllerTree() {
        if (controllerTreeSnapshot == null) {
            controllerTreeSnapshot = ControllerTreeSnapshot.capture(this);
        } else {
            controllerTreeSnapshot.restore();
        }
    }

    private static BranchGroupState getGroupState(GroupKey key) {
        synchronized (GROUPS) {
            return GROUPS.computeIfAbsent(key, ignored -> new BranchGroupState());
        }
    }

    private static class GroupKey {
        private final String sourceClassName;
        private final String sourceName;
        private final List<String> childSignature;
        private final int sourceOccurrence;

        private GroupKey(
                String sourceClassName,
                String sourceName,
                List<String> childSignature,
                int sourceOccurrence) {
            this.sourceClassName = sourceClassName;
            this.sourceName = sourceName;
            this.childSignature = childSignature;
            this.sourceOccurrence = sourceOccurrence;
        }

        static GroupKey from(TestElement source) {
            String sourceClassName = source.getClass().getName();
            String sourceName = source.getName();
            List<String> childSignature = childSignature(source);
            SourceKey sourceKey = new SourceKey(sourceClassName, sourceName, childSignature);
            int sourceOccurrence = SOURCE_REGISTRY.get().sourceOccurrence(source, sourceKey);
            return new GroupKey(sourceClassName, sourceName, childSignature, sourceOccurrence);
        }

        private static List<String> childSignature(TestElement source) {
            if (!(source instanceof GenericController)) {
                return Collections.emptyList();
            }

            List<String> signature = new ArrayList<>();
            for (TestElement child : ((GenericController) source).subControllersAndSamplers) {
                if (child instanceof CoordinatedThroughputController) {
                    CoordinatedThroughputController controller = (CoordinatedThroughputController) child;
                    signature.add(child.getClass().getName() + ':' + controller.getBranchId());
                } else {
                    signature.add(child.getClass().getName() + ':' + child.getName());
                }
            }
            Collections.sort(signature);
            return Collections.unmodifiableList(signature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GroupKey)) {
                return false;
            }
            GroupKey groupKey = (GroupKey) o;
            return Objects.equals(sourceClassName, groupKey.sourceClassName)
                    && Objects.equals(sourceName, groupKey.sourceName)
                    && Objects.equals(childSignature, groupKey.childSignature)
                    && sourceOccurrence == groupKey.sourceOccurrence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceClassName, sourceName, childSignature, sourceOccurrence);
        }
    }

    private static class SourceKey {
        private final String sourceClassName;
        private final String sourceName;
        private final List<String> childSignature;

        SourceKey(String sourceClassName, String sourceName, List<String> childSignature) {
            this.sourceClassName = sourceClassName;
            this.sourceName = sourceName;
            this.childSignature = childSignature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SourceKey)) {
                return false;
            }
            SourceKey sourceKey = (SourceKey) o;
            return Objects.equals(sourceClassName, sourceKey.sourceClassName)
                    && Objects.equals(sourceName, sourceKey.sourceName)
                    && Objects.equals(childSignature, sourceKey.childSignature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceClassName, sourceName, childSignature);
        }
    }

    private static class SourceRegistry {
        private final IdentityHashMap<TestElement, Integer> occurrencesBySource = new IdentityHashMap<>();
        private final Map<SourceKey, Integer> nextOccurrenceByKey = new HashMap<>();

        int sourceOccurrence(TestElement source, SourceKey sourceKey) {
            Integer sourceOccurrence = occurrencesBySource.get(source);
            if (sourceOccurrence != null) {
                return sourceOccurrence;
            }

            int occurrence = nextOccurrenceByKey.getOrDefault(sourceKey, 0);
            nextOccurrenceByKey.put(sourceKey, occurrence + 1);
            occurrencesBySource.put(source, occurrence);
            return occurrence;
        }
    }

    private static class ControllerTreeSnapshot {
        private final LinkedHashMap<GenericController, List<TestElement>> childrenByController =
                new LinkedHashMap<>();

        static ControllerTreeSnapshot capture(GenericController root) {
            ControllerTreeSnapshot snapshot = new ControllerTreeSnapshot();
            snapshot.captureController(root);
            return snapshot;
        }

        void restore() {
            for (Map.Entry<GenericController, List<TestElement>> entry : childrenByController.entrySet()) {
                GenericController controller = entry.getKey();
                controller.subControllersAndSamplers.clear();
                controller.subControllersAndSamplers.addAll(entry.getValue());
            }
            boolean rootController = true;
            for (GenericController controller : childrenByController.keySet()) {
                if (rootController) {
                    rootController = false;
                    continue;
                }
                resetController(controller);
            }
        }

        private void captureController(GenericController controller) {
            List<TestElement> children = new ArrayList<>(controller.subControllersAndSamplers);
            childrenByController.put(controller, Collections.unmodifiableList(children));
            for (TestElement child : children) {
                if (child instanceof GenericController) {
                    captureController((GenericController) child);
                }
            }
        }

        private static void resetController(GenericController controller) {
            controller.reInitialize();
            controller.resetCurrent();
            controller.resetIterCount();
            controller.setFirst(true);
            controller.setDone(false);
            if (controller instanceof LoopController) {
                ((LoopController) controller).resetLoopCount();
            }
        }
    }

    private static class BranchGroupState {
        private static final String NO_BRANCH_SELECTED = ""; // $NON-NLS-1$

        private final IdentityHashMap<LoopIterationEvent, RoundState> rounds = new IdentityHashMap<>();
        private final Map<String, Double> balances = new HashMap<>();
        private double noSelectionBalance;

        synchronized String register(LoopIterationEvent event, String branchId, double weight) {
            RoundState round = rounds.computeIfAbsent(event, ignored -> new RoundState());
            String effectiveBranchId = branchId;
            for (int copy = 2; round.weights.containsKey(effectiveBranchId); copy++) {
                effectiveBranchId = branchId + "#" + copy; // $NON-NLS-1$
            }
            round.weights.put(effectiveBranchId, normalizedWeight(weight));
            return effectiveBranchId;
        }

        synchronized String getSelectedBranchId(LoopIterationEvent event) {
            RoundState round = rounds.get(event);
            if (round == null) {
                return NO_BRANCH_SELECTED;
            }
            if (round.selectedBranchId == null) {
                round.selectedBranchId = select(round.weights);
            }
            return round.selectedBranchId;
        }

        synchronized void branchCompleted(LoopIterationEvent event, String branchId) {
            RoundState round = rounds.get(event);
            if (round == null) {
                return;
            }
            round.completedBranches.add(branchId);
            if (round.completedBranches.size() >= round.weights.size()) {
                rounds.remove(event);
            }
        }

        private String select(Map<String, Double> weights) {
            double totalWeight = 0.0d;
            for (double weight : weights.values()) {
                totalWeight += weight;
            }
            double noSelectionWeight = Math.max(0.0d, 100.0d - totalWeight);
            double denominator = totalWeight + noSelectionWeight;
            if (denominator <= 0.0d) {
                return NO_BRANCH_SELECTED;
            }

            String selectedBranchId = NO_BRANCH_SELECTED;
            double selectedBalance = Double.NEGATIVE_INFINITY;
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                String branchId = entry.getKey();
                double balance = balances.getOrDefault(branchId, 0.0d) + entry.getValue();
                balances.put(branchId, balance);
                if (balance > selectedBalance) {
                    selectedBalance = balance;
                    selectedBranchId = branchId;
                }
            }

            noSelectionBalance += noSelectionWeight;
            if (noSelectionBalance > selectedBalance) {
                selectedBranchId = NO_BRANCH_SELECTED;
            }

            if (NO_BRANCH_SELECTED.equals(selectedBranchId)) {
                noSelectionBalance -= denominator;
            } else {
                balances.put(selectedBranchId, balances.get(selectedBranchId) - denominator);
            }
            return selectedBranchId;
        }

        private static double normalizedWeight(double weight) {
            if (Double.isNaN(weight) || Double.isInfinite(weight) || weight <= 0.0d) {
                return 0.0d;
            }
            return weight;
        }
    }

    private static class RoundState {
        private final LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        private final Set<String> completedBranches = new HashSet<>();
        private String selectedBranchId;
    }
}
