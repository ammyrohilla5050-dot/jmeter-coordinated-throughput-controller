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

package org.apache.jmeter.control.gui;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.jmeter.control.CoordinatedThroughputController;
import org.apache.jmeter.testelement.TestElement;

import net.miginfocom.swing.MigLayout;

public class CoordinatedThroughputControllerGui extends AbstractControllerGui {
    private static final long serialVersionUID = 1L;

    private static final String STATIC_LABEL = "Coordinated Throughput Controller"; // $NON-NLS-1$

    private JTextField percentThroughput;

    public CoordinatedThroughputControllerGui() {
        init();
    }

    @Override
    public TestElement createTestElement() {
        CoordinatedThroughputController controller = new CoordinatedThroughputController();
        modifyTestElement(controller);
        return controller;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        CoordinatedThroughputController controller = (CoordinatedThroughputController) element;
        String throughput = percentThroughput.getText().trim();
        try {
            controller.setPercentThroughput(Float.parseFloat(throughput));
        } catch (NumberFormatException e) {
            controller.setPercentThroughput(throughput);
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        percentThroughput.setText(((CoordinatedThroughputController) element).getPercentThroughput());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        percentThroughput.setText("100"); // $NON-NLS-1$
    }

    @Override
    public String getLabelResource() {
        return STATIC_LABEL;
    }

    @Override
    public String getStaticLabel() {
        return STATIC_LABEL;
    }

    @Override
    public String getDocAnchor() {
        return "Coordinated_Throughput_Controller"; // $NON-NLS-1$
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel panel = new JPanel(new MigLayout("fillx, wrap 2, insets 0", "[][fill,grow]"));
        JLabel label = new JLabel("Throughput weight"); // $NON-NLS-1$
        percentThroughput = new JTextField(15);
        label.setLabelFor(percentThroughput);
        panel.add(label);
        panel.add(percentThroughput, "growx");
        add(panel, BorderLayout.CENTER);
    }
}
