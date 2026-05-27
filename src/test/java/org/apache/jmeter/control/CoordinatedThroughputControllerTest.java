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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.control.gui.CoordinatedThroughputControllerGui;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.TestCompiler;
import org.apache.jorphan.collections.ListedHashTree;
import org.junit.jupiter.api.Test;

public class CoordinatedThroughputControllerTest {

    @Test
    public void twoEqualControllersRunOneChildPerIteration() {
        LoopController loop = compiledLoop(10,
                controller("A", 50.0f, "A"),
                controller("B", 50.0f, "B"));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(5, counts.get("A"));
        assertEquals(5, counts.get("B"));
    }

    @Test
    public void selectedControllerRunsAllChildrenAndSiblingsDoNotRun() {
        LoopController loop = compiledLoop(2,
                new NamedSampler("before"),
                controller("A", 50.0f, "A1", "A2"),
                controller("B", 50.0f, "B1", "B2"),
                new NamedSampler("after"));

        List<String> names = nextNamesUntilDone(loop);
        assertTrue(names.equals(Arrays.asList("before", "A1", "A2", "after", "before", "B1", "B2", "after"))
                || names.equals(Arrays.asList("before", "B1", "B2", "after", "before", "A1", "A2", "after")));
    }

    @Test
    public void selectedControllerCanRunTransactionChildOnEachSelectedIteration() {
        LoopController loop = compiledLoop(10,
                controllerWithChildren("A", 50.0f,
                        transaction("A Transaction", new NamedSampler("A"))),
                controllerWithChildren("B", 50.0f,
                        transaction("B Transaction", new NamedSampler("B"))));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(5, counts.get("A"));
        assertEquals(5, counts.get("B"));
    }

    @Test
    public void selectedControllerCanRunLoopChildOnEachSelectedIteration() {
        LoopController loop = compiledLoop(10,
                controllerWithChildren("A", 50.0f,
                        nestedLoop("A Loop", 1, new NamedSampler("A"))),
                controllerWithChildren("B", 50.0f,
                        nestedLoop("B Loop", 1, new NamedSampler("B"))));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(5, counts.get("A"));
        assertEquals(5, counts.get("B"));
    }

    @Test
    public void selectedControllerRunsAllSamplersInsideNestedControllerEverySelectedIteration() {
        LoopController loop = compiledLoop(4,
                controllerWithChildren("A", 50.0f,
                        transaction("A Transaction", new NamedSampler("A1"), new NamedSampler("A2"))),
                controllerWithChildren("B", 50.0f,
                        transaction("B Transaction", new NamedSampler("B1"), new NamedSampler("B2"))));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(2, counts.get("A1"));
        assertEquals(2, counts.get("A2"));
        assertEquals(2, counts.get("B1"));
        assertEquals(2, counts.get("B2"));
    }

    @Test
    public void singleControllerBelow100RunsOnlyConfiguredShare() {
        LoopController loop = compiledLoop(100,
                controller("A", 40.0f, "A"));

        assertEquals(40, count(nextNamesUntilDone(loop), "A"));
    }

    @Test
    public void controllersBelow100LeaveRemainderUnselected() {
        LoopController loop = compiledLoop(100,
                controller("A", 20.0f, "A"),
                controller("B", 30.0f, "B"));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(20, counts.get("A"));
        assertEquals(30, counts.get("B"));
    }

    @Test
    public void fractionalWeightsAreHandledAsPercentages() {
        LoopController loop = compiledLoop(1000,
                controller("A", 1.4f, "A"),
                controller("B", 1.5f, "B"),
                controller("C", 1.6f, "C"));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(14, counts.get("A"));
        assertEquals(15, counts.get("B"));
        assertEquals(16, counts.get("C"));
    }

    @Test
    public void duplicateSavedBranchIdsAreSeparatedUnderTransactionController() {
        CoordinatedThroughputController first = controller("A", 30.0f, "A");
        CoordinatedThroughputController second = controller("B", 40.0f, "B");
        setSavedBranchId(first, "copied-id");
        setSavedBranchId(second, "copied-id");

        LoopController loop = compiledTransactionLoop(100, first, second);

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(30, counts.get("A"));
        assertEquals(40, counts.get("B"));
    }

    @Test
    public void sameNamedParentControllersDoNotShareDistribution() {
        CoordinatedThroughputController first = controller("Copied Controller", 50.0f, "A");
        CoordinatedThroughputController second = controller("Copied Controller", 50.0f, "B");
        setSavedBranchId(first, "copied-parent-branch");
        setSavedBranchId(second, "copied-parent-branch");

        LoopController loop = compiledSameNamedParentLoop(10,
                first,
                second);

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(5, counts.get("A"));
        assertEquals(5, counts.get("B"));
    }

    @Test
    public void controllersAt100RunExactlyOneControllerPerIteration() {
        LoopController loop = compiledLoop(100,
                controller("A", 40.0f, "A"),
                controller("B", 60.0f, "B"));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(40, counts.get("A"));
        assertEquals(60, counts.get("B"));
    }

    @Test
    public void controllersAbove100AreNormalizedAsRelativeWeights() {
        LoopController loop = compiledLoop(50,
                controller("A", 60.0f, "A"),
                controller("B", 90.0f, "B"));

        Map<String, Integer> counts = counts(nextNamesUntilDone(loop));
        assertEquals(20, counts.get("A"));
        assertEquals(30, counts.get("B"));
    }

    @Test
    public void zeroWeightedControllersAreSkipped() {
        LoopController loop = compiledLoop(3,
                controller("A", 0.0f, "A"),
                controller("B", 0.0f, "B"));

        assertTrue(nextNamesUntilDone(loop).isEmpty());
    }

    @Test
    public void distributionIsSharedAcrossClonedThreadControllers() throws InterruptedException {
        CoordinatedThroughputController firstTemplate = controller("A", 50.0f);
        CoordinatedThroughputController secondTemplate = controller("B", 50.0f);

        LoopController firstThread = compiledLoop(1,
                cloneWithSampler(firstTemplate, "A"),
                cloneWithSampler(secondTemplate, "B"));
        LoopController secondThread = compiledLoop(1,
                cloneWithSampler(firstTemplate, "A"),
                cloneWithSampler(secondTemplate, "B"));

        List<String> names = Collections.synchronizedList(new ArrayList<>());
        Thread threadOne = new Thread(() -> names.addAll(nextNamesUntilDone(firstThread)));
        Thread threadTwo = new Thread(() -> names.addAll(nextNamesUntilDone(secondThread)));
        threadOne.start();
        threadTwo.start();
        threadOne.join();
        threadTwo.join();
        assertEquals(1, count(names, "A"), names.toString());
        assertEquals(1, count(names, "B"), names.toString());
    }

    @Test
    public void guiUsesCoordinatedThroughputControllerName() {
        assertEquals("Coordinated Throughput Controller",
                new CoordinatedThroughputControllerGui().getStaticLabel());
    }

    private static LoopController compiledLoop(int loops, TestElement... children) {
        LoopController loop = new LoopController();
        loop.setName("loop");
        loop.setLoops(loops);
        loop.setContinueForever(false);

        ListedHashTree tree = new ListedHashTree();
        tree.add(loop);
        for (TestElement child : children) {
            if (child instanceof CoordinatedThroughputController) {
                ((CoordinatedThroughputController) child).testStarted();
            }
            setRunningVersionRecursively(child);
            tree.add(loop, child);
        }

        TestCompiler.initialize();
        tree.traverse(new TestCompiler(tree));
        loop.setRunningVersion(true);
        loop.initialize();
        return loop;
    }

    private static LoopController compiledTransactionLoop(int loops, CoordinatedThroughputController... controllers) {
        JMeterContextService.getContext().setVariables(new JMeterVariables());

        LoopController loop = new LoopController();
        loop.setName("loop");
        loop.setLoops(loops);
        loop.setContinueForever(false);

        TransactionController transaction = new TransactionController();
        transaction.setName("Transaction Controller Parent");

        ListedHashTree tree = new ListedHashTree();
        tree.add(loop);
        tree.add(loop, transaction);
        for (CoordinatedThroughputController controller : controllers) {
            controller.testStarted();
            controller.setRunningVersion(true);
            tree.add(transaction, controller);
        }

        TestCompiler.initialize();
        tree.traverse(new TestCompiler(tree));
        loop.setRunningVersion(true);
        loop.initialize();
        return loop;
    }

    private static LoopController compiledSameNamedParentLoop(
            int loops,
            CoordinatedThroughputController first,
            CoordinatedThroughputController second) {
        JMeterContextService.getContext().setVariables(new JMeterVariables());

        LoopController loop = new LoopController();
        loop.setName("loop");
        loop.setLoops(loops);
        loop.setContinueForever(false);

        TransactionController firstParent = sameNamedTransactionParent("first");
        TransactionController secondParent = sameNamedTransactionParent("second");

        ListedHashTree tree = new ListedHashTree();
        tree.add(loop);
        tree.add(loop, firstParent);
        tree.add(loop, secondParent);
        first.testStarted();
        second.testStarted();
        first.setRunningVersion(true);
        second.setRunningVersion(true);
        tree.add(firstParent, first);
        tree.add(secondParent, second);

        TestCompiler.initialize();
        tree.traverse(new TestCompiler(tree));
        loop.setRunningVersion(true);
        loop.initialize();
        return loop;
    }

    private static TransactionController sameNamedTransactionParent(String id) {
        TransactionController parent = new TransactionController();
        parent.setName("Same Transaction Parent");
        parent.setProperty(new StringProperty("ctc.test.parent.id", id));
        return parent;
    }

    private static CoordinatedThroughputController controller(String name, float percentage, String... samplerNames) {
        CoordinatedThroughputController controller = new CoordinatedThroughputController();
        controller.setName(name);
        controller.setPercentThroughput(percentage);
        for (String samplerName : samplerNames) {
            controller.addTestElement(new NamedSampler(samplerName));
        }
        return controller;
    }

    private static CoordinatedThroughputController controllerWithChildren(
            String name, float percentage, TestElement... children) {
        CoordinatedThroughputController controller = new CoordinatedThroughputController();
        controller.setName(name);
        controller.setPercentThroughput(percentage);
        for (TestElement child : children) {
            controller.addTestElement(child);
        }
        return controller;
    }

    private static TransactionController transaction(String name, TestElement... children) {
        TransactionController controller = new TransactionController();
        controller.setName(name);
        for (TestElement child : children) {
            controller.addTestElement(child);
        }
        return controller;
    }

    private static LoopController nestedLoop(String name, int loops, TestElement... children) {
        LoopController controller = new LoopController();
        controller.setName(name);
        controller.setLoops(loops);
        controller.setContinueForever(false);
        for (TestElement child : children) {
            controller.addTestElement(child);
        }
        return controller;
    }

    private static void setRunningVersionRecursively(TestElement element) {
        element.setRunningVersion(true);
        if (element instanceof GenericController) {
            for (TestElement child : ((GenericController) element).subControllersAndSamplers) {
                setRunningVersionRecursively(child);
            }
        }
    }

    private static void setSavedBranchId(CoordinatedThroughputController controller, String branchId) {
        controller.setProperty(new StringProperty("CoordinatedThroughputController.branchId", branchId));
    }

    private static CoordinatedThroughputController cloneOf(CoordinatedThroughputController controller) {
        return (CoordinatedThroughputController) controller.clone();
    }

    private static CoordinatedThroughputController cloneWithSampler(
            CoordinatedThroughputController controller, String samplerName) {
        CoordinatedThroughputController clone = cloneOf(controller);
        clone.addTestElement(new NamedSampler(samplerName));
        return clone;
    }

    private static List<String> nextNamesUntilDone(LoopController controller) {
        List<String> names = new ArrayList<>();
        for (int guard = 0; guard < 10000 && !controller.isDone(); guard++) {
            Sampler sampler = controller.next();
            if (sampler != null) {
                names.add(sampler.getName());
            }
        }
        assertTrue(controller.isDone());
        return names;
    }

    private static Map<String, Integer> counts(List<String> names) {
        Map<String, Integer> counts = new HashMap<>();
        for (String name : names) {
            counts.put(name, Collections.frequency(names, name));
        }
        return counts;
    }

    private static int count(List<String> names, String name) {
        return Collections.frequency(names, name);
    }

    private static class NamedSampler extends AbstractSampler {
        private static final long serialVersionUID = 1L;

        NamedSampler(String name) {
            setName(name);
        }

        @Override
        public SampleResult sample(Entry entry) {
            return null;
        }
    }
}
