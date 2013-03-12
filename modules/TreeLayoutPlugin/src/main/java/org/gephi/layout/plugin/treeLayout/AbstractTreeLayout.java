/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gephi.layout.plugin.treeLayout;

import org.gephi.dynamic.api.DynamicController;
import org.gephi.dynamic.api.DynamicModel;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

/**
 *
 * @author jbilcke
 */
 public abstract class AbstractTreeLayout implements Layout {

    private final LayoutBuilder layoutBuilder;
    protected GraphModel graphModel;
    protected DynamicModel dynamicModel;
    protected boolean converged;
    protected Node root;
    
    public AbstractTreeLayout(LayoutBuilder layoutBuilder) {
        this.layoutBuilder = layoutBuilder;
    }

    @Override
    public LayoutBuilder getBuilder() {
        return layoutBuilder;
    }

    @Override
    public void setGraphModel(GraphModel graphModel) {
        this.graphModel = graphModel;
        Workspace workspace = graphModel.getWorkspace();
        DynamicController dynamicController = Lookup.getDefault().lookup(DynamicController.class);
        if (dynamicController != null && workspace != null) {
            dynamicModel = dynamicController.getModel(workspace);
        }
    }

    @Override
    public boolean canAlgo() {
        return (!isConverged()) && graphModel != null;
    }

    public void setConverged(boolean converged) {
        this.converged = converged;
    }

    public boolean isConverged() {
        return converged;
    }
 }